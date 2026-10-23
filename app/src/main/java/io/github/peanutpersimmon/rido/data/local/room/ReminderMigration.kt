package io.github.peanutpersimmon.rido.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ReminderMigration : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN plannedReminderOffsetMinutes INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN dueReminderOffsetMinutes INTEGER")
        db.execSQL("ALTER TABLE events ADD COLUMN startReminderOffsetMinutes INTEGER")
    }
}
