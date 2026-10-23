package io.github.peanutpersimmon.rido.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rido.data.local.room.FinalTemporalSchemaMigration
import io.github.peanutpersimmon.rido.data.local.room.ReminderMigration
import io.github.peanutpersimmon.rido.data.local.room.TaskLocationMigration
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FinalTemporalSchemaMigrationTest {
    @Test fun populatedV4RetainsRowsAndSetNullForeignKeys() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "temporal-migration-${UUID.randomUUID()}.db"
        val schema = instrumentation.context.assets.open(
            "io.github.peanutpersimmon.rido.data.local.room.RidoDatabase/4.json",
        ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (i in 0 until entities.length()) {
                            val entity = entities.getJSONObject(i)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                            val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                            for (j in 0 until indices.length()) {
                                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                            }
                        }
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        try {
            val old = helper.writableDatabase
            old.execSQL("INSERT INTO categories(id,name,isArchived,sortOrder,createdAtMillis,updatedAtMillis,colorKey) VALUES(1,'Category',0,0,1,1,'neutral')")
            old.execSQL("INSERT INTO tags(id,name,isArchived,sortOrder,createdAtMillis,updatedAtMillis) VALUES(7,'Tag',0,0,1,1)")
            old.execSQL("INSERT INTO tasks(id,title,status,plannedDateEpochDay,plannedStartMinuteOfDay,plannedEndMinuteOfDay,categoryId,createdAtMillis,updatedAtMillis) VALUES(1,'Task','TODO',10,600,660,1,1,1)")
            old.execSQL("INSERT INTO tasks(id,title,status,parentTaskId,createdAtMillis,updatedAtMillis) VALUES(4,'Child','TODO',1,1,1)")
            old.execSQL("INSERT INTO events(id,title,dateEpochDay,actualStartAtMillis,actualEndAtMillis,categoryId,createdAtMillis,updatedAtMillis) VALUES(2,'Event',10,100,200,1,1,1)")
            old.execSQL("INSERT INTO sessions(id,title,kind,startAtMillis,endAtMillis,taskId,sourceEventId,categoryId,createdAtMillis,updatedAtMillis) VALUES(3,'Record','ACTIVITY',100,200,1,2,1,1,1)")
            old.execSQL("INSERT INTO task_tag_cross_refs VALUES(1,7)")
            old.execSQL("INSERT INTO event_tag_cross_refs VALUES(2,7)")
            old.execSQL("INSERT INTO session_tag_cross_refs VALUES(3,7)")
            helper.close()

            val db = Room.databaseBuilder(context, RidoDatabase::class.java, name)
                .addMigrations(FinalTemporalSchemaMigration, ReminderMigration, TaskLocationMigration).build()
            try {
                // Room opening also validates the migrated SQLite schema against generated v7.
                val sql = db.openHelper.writableDatabase
                fun columns(table: String): Set<String> =
                    sql.query("PRAGMA table_info($table)").use { cursor ->
                        val names = mutableSetOf<String>()
                        while (cursor.moveToNext()) names += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                        names
                    }
                assertFalse("plannedEndMinuteOfDay" in columns("tasks"))
                assertFalse("actualStartAtMillis" in columns("events"))
                assertFalse("actualEndAtMillis" in columns("events"))
                assertTrue("temporalType" in columns("sessions"))
                sql.query("PRAGMA index_list(events)").use { cursor ->
                    while (cursor.moveToNext()) {
                        assertFalse(cursor.getString(cursor.getColumnIndexOrThrow("name")).contains("actual"))
                    }
                }
                sql.query("PRAGMA table_info(sessions)").use { cursor ->
                    while (cursor.moveToNext()) {
                        if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "temporalType") {
                            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("notnull")))
                        }
                    }
                }
                sql.query("SELECT temporalType,taskId,sourceEventId FROM sessions WHERE id=3").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("INTERVAL", it.getString(0))
                    assertEquals(1L, it.getLong(1))
                    assertEquals(2L, it.getLong(2))
                }
                listOf("task", "event", "session").forEach { kind ->
                    sql.query("SELECT tagId FROM ${kind}_tag_cross_refs").use {
                        assertTrue(it.moveToFirst())
                        assertEquals(7L, it.getLong(0))
                    }
                }
                sql.execSQL("DELETE FROM tasks WHERE id=1")
                sql.query("SELECT taskId FROM sessions WHERE id=3").use {
                    assertTrue(it.moveToFirst())
                    assertTrue(it.isNull(0))
                }
                sql.query("SELECT parentTaskId FROM tasks WHERE id=4").use {
                    assertTrue(it.moveToFirst())
                    assertTrue(it.isNull(0))
                }
                sql.execSQL("DELETE FROM events WHERE id=2")
                sql.query("SELECT sourceEventId FROM sessions WHERE id=3").use {
                    assertTrue(it.moveToFirst())
                    assertTrue(it.isNull(0))
                }
                sql.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            } finally {
                db.close()
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}

