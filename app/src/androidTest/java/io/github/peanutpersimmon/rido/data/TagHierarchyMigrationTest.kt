package io.github.peanutpersimmon.rido.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.local.room.TagHierarchyMigration
import io.github.peanutpersimmon.rido.data.local.room.SessionEventSourceMigration
import io.github.peanutpersimmon.rido.data.local.room.CategoryColorMigration
import io.github.peanutpersimmon.rido.data.local.room.FinalTemporalSchemaMigration
import io.github.peanutpersimmon.rido.data.local.room.ReminderMigration
import io.github.peanutpersimmon.rido.data.local.room.TaskLocationMigration
import io.github.peanutpersimmon.rido.data.repository.RoomTagRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TagHierarchyMigrationTest {
    @Test fun populatedV1RetainsTagsAndAllFourRelations() = migrate(populated = true)
    @Test fun emptyV1DoesNotInventTags() = migrate(populated = false)

    private fun migrate(populated: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "tag-migration-${UUID.randomUUID()}.db"
        val schema = instrumentation.context.assets.open(
            "io.github.peanutpersimmon.rido.data.local.room.RidoDatabase/1.json"
        ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (i in 0 until entities.length()) {
                            val entity = entities.getJSONObject(i)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                            val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                            for (j in 0 until indices.length())
                                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                        }
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        val old = helper.writableDatabase
        if (populated) {
            old.execSQL("INSERT INTO tags VALUES(7, 'old', 0, 3, 10, 20)")
            old.execSQL("INSERT INTO tags VALUES(8, 'archived', 1, 4, 11, 21)")
            old.execSQL("INSERT INTO tasks(id,title,status,createdAtMillis,updatedAtMillis) VALUES(1,'Task','TODO',1,1)")
            old.execSQL("INSERT INTO events(id,title,dateEpochDay,createdAtMillis,updatedAtMillis) VALUES(1,'Event',1,1,1)")
            old.execSQL("INSERT INTO sessions(id,title,kind,startAtMillis,endAtMillis,createdAtMillis,updatedAtMillis) VALUES(1,'Session','ACTIVITY',100,200,1,1)")
            old.execSQL("INSERT INTO keyword_rules(id,keyword,isEnabled,priority,createdAtMillis,updatedAtMillis) VALUES(1,'old',1,0,1,1)")
            listOf("task", "event", "session", "keyword_rule").forEach {
                old.execSQL("INSERT INTO ${it}_tag_cross_refs VALUES(1,7)")
            }
        }
        helper.close()
        val db = Room.databaseBuilder(context, RidoDatabase::class.java, name)
            .addMigrations(TagHierarchyMigration, SessionEventSourceMigration,
                CategoryColorMigration, FinalTemporalSchemaMigration, ReminderMigration,
                TaskLocationMigration).build()
        try {
            // Opening validates the full v1→v7 chain against the generated Room schema.
            val sql = db.openHelper.writableDatabase
            runBlocking {
                val tags = db.tagDao().getAllIncludingArchived()
                if (!populated) assertTrue(tags.isEmpty())
                else {
                    val root = tags.single { it.parentId == null }
                    assertEquals("原有标签", root.name)
                    assertEquals(root.id, tags.single { it.id == 7L }.parentId)
                    assertEquals("old", tags.single { it.id == 7L }.name)
                    assertEquals(20L, tags.single { it.id == 7L }.updatedAtMillis)
                    assertTrue(tags.single { it.id == 8L }.isArchived)
                    listOf("task", "event", "session", "keyword_rule").forEach { table ->
                        sql.query("SELECT tagId FROM ${table}_tag_cross_refs").use { assertTrue(it.moveToFirst()); assertEquals(7L, it.getLong(0)) }
                    }
                    val repository = RoomTagRepository(db)
                    repository.renameTag(7, "Renamed")
                    repository.deleteTag(root.id)
                    assertTrue(db.tagDao().getById(7)!!.isArchived)
                    sql.query("SELECT tagId FROM task_tag_cross_refs").use { assertTrue(it.moveToFirst()); assertEquals(7L, it.getLong(0)) }
                }
            }
            sql.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
