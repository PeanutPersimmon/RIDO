package io.github.peanutpersimmon.rido.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rido.backup.BackupDatabaseValidator
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.local.room.TaskLocationMigration
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** A genuine v6 file is preflighted, then opened through Room's production 6→7 path. */
@RunWith(AndroidJUnit4::class)
class TaskLocationMigrationTest {
    @Test fun v6BackupKeepsNotesRemindersAndRelationsWhileLocationDefaultsNull() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "task-location-migration-${UUID.randomUUID()}.db"
        val schema = instrumentation.context.assets.open(
            "io.github.peanutpersimmon.rido.data.local.room.RidoDatabase/6.json",
        ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (i in 0 until entities.length()) {
                            val entity = entities.getJSONObject(i)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                            val indices = entity.optJSONArray("indices") ?: JSONArray()
                            for (j in 0 until indices.length())
                                db.execSQL(indices.getJSONObject(j).getString("createSql")
                                    .replace("${'$'}{TABLE_NAME}", table))
                        }
                        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                        db.execSQL("INSERT INTO room_master_table(id,identity_hash) VALUES(42,?)",
                            arrayOf<Any>(schema.getString("identityHash")))
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        try {
            val old = helper.writableDatabase
            old.execSQL("INSERT INTO tasks(id,title,note,status,plannedReminderOffsetMinutes,dueReminderOffsetMinutes,plannedDateEpochDay,plannedStartMinuteOfDay,dueDateEpochDay,dueMinuteOfDay,createdAtMillis,updatedAtMillis) VALUES(1,'送样','地点：旧备注','TODO',10,30,10,600,10,720,1,1)")
            old.execSQL("INSERT INTO tasks(id,title,status,parentTaskId,createdAtMillis,updatedAtMillis) VALUES(2,'子项','TODO',1,1,1)")
            old.execSQL("INSERT INTO events(id,title,dateEpochDay,startMinuteOfDay,startReminderOffsetMinutes,createdAtMillis,updatedAtMillis) VALUES(3,'日程',10,840,60,1,1)")
            old.execSQL("INSERT INTO sessions(id,title,kind,temporalType,startAtMillis,endAtMillis,taskId,sourceEventId,createdAtMillis,updatedAtMillis) VALUES(4,'记录','ACTIVITY','INTERVAL',100,200,1,3,1,1)")
            helper.close()
            assertTrue(RidoDatabase.hasMigrationPath(6))
            BackupDatabaseValidator.validate(context, context.getDatabasePath(name), 6)
            val room = Room.databaseBuilder(context, RidoDatabase::class.java, name)
                .addMigrations(TaskLocationMigration).build()
            try {
                val sql = room.openHelper.writableDatabase
                assertEquals(7, sql.version)
                sql.query("SELECT title,note,location,plannedReminderOffsetMinutes,dueReminderOffsetMinutes FROM tasks WHERE id=1").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("送样", it.getString(0))
                    assertEquals("地点：旧备注", it.getString(1))
                    assertTrue(it.isNull(2))
                    assertEquals(10, it.getInt(3))
                    assertEquals(30, it.getInt(4))
                }
                sql.query("SELECT parentTaskId FROM tasks WHERE id=2").use {
                    assertTrue(it.moveToFirst()); assertEquals(1L, it.getLong(0))
                }
                sql.query("SELECT startReminderOffsetMinutes FROM events WHERE id=3").use {
                    assertTrue(it.moveToFirst()); assertEquals(60, it.getInt(0))
                }
                sql.query("SELECT taskId,sourceEventId FROM sessions WHERE id=4").use {
                    assertTrue(it.moveToFirst()); assertEquals(1L, it.getLong(0)); assertEquals(3L, it.getLong(1))
                }
                sql.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            } finally { room.close() }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
