package io.github.peanutpersimmon.rido.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.peanutpersimmon.rido.data.local.room.dao.CategoryDao
import io.github.peanutpersimmon.rido.data.local.room.dao.EventDao
import io.github.peanutpersimmon.rido.data.local.room.dao.KeywordRuleDao
import io.github.peanutpersimmon.rido.data.local.room.dao.ProcessedCommandDao
import io.github.peanutpersimmon.rido.data.local.room.dao.SessionDao
import io.github.peanutpersimmon.rido.data.local.room.dao.TagDao
import io.github.peanutpersimmon.rido.data.local.room.dao.TaskDao
import io.github.peanutpersimmon.rido.data.local.room.entity.CategoryEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.EventEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.EventTagCrossRef
import io.github.peanutpersimmon.rido.data.local.room.entity.KeywordRuleEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.KeywordRuleTagCrossRef
import io.github.peanutpersimmon.rido.data.local.room.entity.ProcessedCommandEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.SessionEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.SessionTagCrossRef
import io.github.peanutpersimmon.rido.data.local.room.entity.TagEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.TaskEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.TaskTagCrossRef

@Database(
    entities = [
        TaskEntity::class,
        EventEntity::class,
        SessionEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        TaskTagCrossRef::class,
        EventTagCrossRef::class,
        SessionTagCrossRef::class,
        KeywordRuleEntity::class,
        KeywordRuleTagCrossRef::class,
        ProcessedCommandEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(RidoTypeConverters::class)
abstract class RidoDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun eventDao(): EventDao
    abstract fun sessionDao(): SessionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun keywordRuleDao(): KeywordRuleDao
    abstract fun processedCommandDao(): ProcessedCommandDao

    companion object {
        const val DATABASE_NAME = "rido.db"
        val MIGRATIONS: Array<Migration> = arrayOf(
            TagHierarchyMigration, SessionEventSourceMigration, CategoryColorMigration,
            FinalTemporalSchemaMigration, ReminderMigration, TaskLocationMigration,
        )

        fun hasMigrationPath(fromVersion: Int): Boolean {
            if (fromVersion !in 1..7) return false
            var current = fromVersion
            while (current < 7) {
                current = MIGRATIONS.firstOrNull { it.startVersion == current }?.endVersion ?: return false
            }
            return current == 7
        }

        @Volatile
        private var instance: RidoDatabase? = null

        fun getInstance(context: Context): RidoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RidoDatabase::class.java,
                    DATABASE_NAME,
                ).addMigrations(*MIGRATIONS)
                    .addCallback(FreshDatabaseMarkerCallback).build().also { instance = it }
            }

        /** Existing repositories must never be used again after this; import cold-restarts. */
        fun closeForImport() = synchronized(this) { instance?.close() }
    }
}

/** Room invokes onCreate only for a newly created database, never for an existing or upgraded one. */
internal val FreshDatabaseMarkerCallback = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO processed_commands(commandId, processedAtMillis) VALUES (?, ?)",
            arrayOf<Any>(FreshDatabaseMarker, System.currentTimeMillis()),
        )
    }
}
