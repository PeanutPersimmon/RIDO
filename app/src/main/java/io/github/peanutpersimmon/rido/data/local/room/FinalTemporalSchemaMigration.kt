package io.github.peanutpersimmon.rido.data.local.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Final v5 rebuild: copy only surviving columns; all legacy Sessions are intervals. */
object FinalTemporalSchemaMigration : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val taskColumns = "id,title,note,status,parentTaskId,plannedDateEpochDay,plannedStartMinuteOfDay,dueDateEpochDay,dueMinuteOfDay,estimatedDurationMinutes,categoryId,createdAtMillis,updatedAtMillis,completedAtMillis,cancelledAtMillis,completionSource"
        val eventColumns = "id,title,note,location,dateEpochDay,startMinuteOfDay,endDateEpochDay,endMinuteOfDay,categoryId,createdAtMillis,updatedAtMillis"
        val sessionColumns = "id,title,note,location,kind,startAtMillis,endAtMillis,taskId,categoryId,createdAtMillis,updatedAtMillis,sourceEventId"

        listOf("tasks", "events", "sessions", "task_tag_cross_refs", "event_tag_cross_refs", "session_tag_cross_refs").forEach { table ->
            db.execSQL("CREATE TEMP TABLE backup_$table AS SELECT * FROM $table")
        }
        listOf("task_tag_cross_refs", "event_tag_cross_refs", "session_tag_cross_refs", "sessions", "tasks", "events").forEach { table ->
            db.execSQL("DROP TABLE $table")
        }

        db.execSQL("""CREATE TABLE tasks (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, note TEXT,
            status TEXT NOT NULL, parentTaskId INTEGER, plannedDateEpochDay INTEGER,
            plannedStartMinuteOfDay INTEGER, dueDateEpochDay INTEGER, dueMinuteOfDay INTEGER,
            estimatedDurationMinutes INTEGER, categoryId INTEGER, createdAtMillis INTEGER NOT NULL,
            updatedAtMillis INTEGER NOT NULL, completedAtMillis INTEGER, cancelledAtMillis INTEGER,
            completionSource TEXT,
            FOREIGN KEY(parentTaskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
            FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE SET NULL
        )""")
        db.execSQL("""CREATE TABLE events (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, note TEXT, location TEXT,
            dateEpochDay INTEGER NOT NULL, startMinuteOfDay INTEGER, endDateEpochDay INTEGER,
            endMinuteOfDay INTEGER, categoryId INTEGER, createdAtMillis INTEGER NOT NULL,
            updatedAtMillis INTEGER NOT NULL,
            FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE SET NULL
        )""")
        db.execSQL("""CREATE TABLE sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, note TEXT, location TEXT,
            kind TEXT NOT NULL, temporalType TEXT NOT NULL, startAtMillis INTEGER NOT NULL,
            endAtMillis INTEGER, taskId INTEGER, categoryId INTEGER, createdAtMillis INTEGER NOT NULL,
            updatedAtMillis INTEGER NOT NULL, sourceEventId INTEGER,
            FOREIGN KEY(sourceEventId) REFERENCES events(id) ON UPDATE NO ACTION ON DELETE SET NULL,
            FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE SET NULL,
            FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE SET NULL
        )""")
        db.execSQL("""CREATE TABLE task_tag_cross_refs (
            taskId INTEGER NOT NULL, tagId INTEGER NOT NULL, PRIMARY KEY(taskId,tagId),
            FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )""")
        db.execSQL("""CREATE TABLE event_tag_cross_refs (
            eventId INTEGER NOT NULL, tagId INTEGER NOT NULL, PRIMARY KEY(eventId,tagId),
            FOREIGN KEY(eventId) REFERENCES events(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )""")
        db.execSQL("""CREATE TABLE session_tag_cross_refs (
            sessionId INTEGER NOT NULL, tagId INTEGER NOT NULL, PRIMARY KEY(sessionId,tagId),
            FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )""")

        db.execSQL("INSERT INTO tasks ($taskColumns) SELECT $taskColumns FROM backup_tasks")
        db.execSQL("INSERT INTO events ($eventColumns) SELECT $eventColumns FROM backup_events")
        db.execSQL("INSERT INTO sessions ($sessionColumns,temporalType) SELECT $sessionColumns,'INTERVAL' FROM backup_sessions")
        listOf("task", "event", "session").forEach { kind ->
            val table = "${kind}_tag_cross_refs"
            db.execSQL("INSERT INTO $table SELECT * FROM backup_$table")
        }
        listOf("tasks", "events", "sessions", "task_tag_cross_refs", "event_tag_cross_refs", "session_tag_cross_refs").forEach { table ->
            db.execSQL("DROP TABLE backup_$table")
        }

        listOf("status", "parentTaskId", "plannedDateEpochDay", "dueDateEpochDay", "categoryId", "completedAtMillis").forEach {
            db.execSQL("CREATE INDEX index_tasks_$it ON tasks($it)")
        }
        listOf("dateEpochDay", "categoryId").forEach {
            db.execSQL("CREATE INDEX index_events_$it ON events($it)")
        }
        listOf("startAtMillis", "endAtMillis", "taskId", "sourceEventId", "categoryId", "kind").forEach {
            db.execSQL("CREATE INDEX index_sessions_$it ON sessions($it)")
        }
        listOf("task", "event", "session").forEach { kind ->
            db.execSQL("CREATE INDEX index_${kind}_tag_cross_refs_tagId ON ${kind}_tag_cross_refs(tagId)")
        }
    }
}

