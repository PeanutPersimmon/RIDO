package io.github.peanutpersimmon.rido.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Existing records deliberately keep a null origin. Never infer origins from copied fields. */
object SessionEventSourceMigration : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sessions ADD COLUMN sourceEventId INTEGER REFERENCES events(id) ON UPDATE NO ACTION ON DELETE SET NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sessions_sourceEventId ON sessions(sourceEventId)")
    }
}
