package io.github.peanutpersimmon.rido.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rido.data.local.room.ReminderMigration
import io.github.peanutpersimmon.rido.data.local.room.TaskLocationMigration
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderMigrationTest {
    @Test fun populatedV5PreservesBusinessDataAndDefaultsRemindersOff() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "reminder-migration-${UUID.randomUUID()}.db"
        val schema = instrumentation.context.assets.open(
            "io.github.peanutpersimmon.rido.data.local.room.RidoDatabase/5.json",
        ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (i in 0 until entities.length()) {
                            val entity = entities.getJSONObject(i)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                            val indices = entity.optJSONArray("indices") ?: JSONArray()
                            for (j in 0 until indices.length())
                                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                        }
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        try {
            val old = helper.writableDatabase
            old.execSQL("INSERT INTO tasks(id,title,status,plannedDateEpochDay,plannedStartMinuteOfDay,dueDateEpochDay,dueMinuteOfDay,createdAtMillis,updatedAtMillis) VALUES(1,'Task','TODO',10,600,10,720,1,1)")
            old.execSQL("INSERT INTO tasks(id,title,status,parentTaskId,createdAtMillis,updatedAtMillis) VALUES(2,'Child','TODO',1,1,1)")
            old.execSQL("INSERT INTO events(id,title,dateEpochDay,startMinuteOfDay,endMinuteOfDay,createdAtMillis,updatedAtMillis) VALUES(3,'Event',10,840,900,1,1)")
            helper.close()
            val db = Room.databaseBuilder(context, RidoDatabase::class.java, name)
                .addMigrations(ReminderMigration, TaskLocationMigration).build()
            try {
                // Opening validates the full generated v7 schema.
                val sql = db.openHelper.writableDatabase
                sql.query("SELECT title,plannedDateEpochDay,plannedStartMinuteOfDay,dueMinuteOfDay,plannedReminderOffsetMinutes,dueReminderOffsetMinutes FROM tasks WHERE id=1").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Task", it.getString(0))
                    assertEquals(10L, it.getLong(1))
                    assertEquals(600, it.getInt(2))
                    assertEquals(720, it.getInt(3))
                    assertTrue(it.isNull(4))
                    assertTrue(it.isNull(5))
                }
                sql.query("SELECT parentTaskId FROM tasks WHERE id=2").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(1L, it.getLong(0))
                }
                sql.query("SELECT title,startMinuteOfDay,startReminderOffsetMinutes FROM events WHERE id=3").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Event", it.getString(0))
                    assertEquals(840, it.getInt(1))
                    assertTrue(it.isNull(2))
                }
                sql.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            } finally { db.close() }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
