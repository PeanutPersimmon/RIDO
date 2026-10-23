package io.github.peanutpersimmon.rido.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Existing notes are intentionally left untouched; their location-like text is ambiguous. */
object TaskLocationMigration : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN location TEXT")
    }
}
