package io.github.peanutpersimmon.rido.backup

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.presentation.plan.PlanViewMode
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses disposable database names. It never replaces the application's rido.db. */
@RunWith(AndroidJUnit4::class)
class BackupDatabaseIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun serviceGeneratedZipContainsNoCredentialStoreOrSensitiveSettingKeys() {
        val archive = File(context.cacheDir, "service-export-${UUID.randomUUID()}.zip")
        try {
            runBlocking { BackupService(context).export(Uri.fromFile(archive)) }
            ZipFile(archive).use { zip ->
                assertEquals(setOf("manifest.json", "database/rido.db", "settings.json"),
                    zip.entries().asSequence().map { it.name }.toSet())
                val settings = zip.getInputStream(zip.getEntry("settings.json")).bufferedReader().readText()
                val manifest = zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().readText()
                listOf("encrypted_api_key", "api_key_iv", "workspace_id", "qwen_credentials.preferences_pb")
                    .forEach { secret ->
                        assertFalse(settings.contains(secret))
                        assertFalse(manifest.contains(secret))
                    }
            }
        } finally { archive.delete() }
    }

    @Test fun snapshotZipAndRestorePreserveEveryBusinessRelationAndReminderField() {
        val sourceName = "backup-source-${UUID.randomUUID()}.db"
        val restoredName = "backup-restored-${UUID.randomUUID()}.db"
        val source = Room.databaseBuilder(context, RidoDatabase::class.java, sourceName).build()
        val directory = File(context.cacheDir, "backup-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val sql = source.openHelper.writableDatabase
            sql.execSQL("INSERT INTO categories(id,name,isArchived,sortOrder,createdAtMillis,updatedAtMillis,colorKey) VALUES(1,'分类',0,0,1,1,'neutral')")
            sql.execSQL("INSERT INTO tags(id,name,isArchived,sortOrder,createdAtMillis,updatedAtMillis) VALUES(2,'标签',0,0,1,1)")
            sql.execSQL("INSERT INTO tasks(id,title,location,status,categoryId,plannedDateEpochDay,plannedStartMinuteOfDay,plannedReminderOffsetMinutes,dueDateEpochDay,dueMinuteOfDay,dueReminderOffsetMinutes,createdAtMillis,updatedAtMillis) VALUES(3,'父任务','实验室','TODO',1,10,600,10,10,720,30,1,1)")
            sql.execSQL("INSERT INTO tasks(id,title,status,parentTaskId,createdAtMillis,updatedAtMillis) VALUES(4,'子任务','TODO',3,1,1)")
            sql.execSQL("INSERT INTO events(id,title,dateEpochDay,startMinuteOfDay,startReminderOffsetMinutes,categoryId,createdAtMillis,updatedAtMillis) VALUES(5,'日程',10,840,60,1,1,1)")
            sql.execSQL("INSERT INTO sessions(id,title,kind,temporalType,startAtMillis,endAtMillis,taskId,sourceEventId,categoryId,createdAtMillis,updatedAtMillis) VALUES(6,'记录','ACTIVITY','INTERVAL',100,200,3,5,1,1,1)")
            sql.execSQL("INSERT INTO sessions(id,title,kind,temporalType,startAtMillis,endAtMillis,createdAtMillis,updatedAtMillis) VALUES(7,'夜间睡眠','NIGHT_SLEEP','INTERVAL',300,400,1,1)")
            sql.execSQL("INSERT INTO sessions(id,title,kind,temporalType,startAtMillis,endAtMillis,createdAtMillis,updatedAtMillis) VALUES(8,'小睡','NAP','INTERVAL',500,600,1,1)")
            sql.execSQL("INSERT INTO keyword_rules(id,keyword,categoryId,isEnabled,priority,createdAtMillis,updatedAtMillis) VALUES(9,'关键词',1,1,0,1,1)")
            sql.execSQL("INSERT INTO task_tag_cross_refs(taskId,tagId) VALUES(3,2)")
            sql.execSQL("INSERT INTO event_tag_cross_refs(eventId,tagId) VALUES(5,2)")
            sql.execSQL("INSERT INTO session_tag_cross_refs(sessionId,tagId) VALUES(6,2)")
            sql.execSQL("INSERT INTO keyword_rule_tag_cross_refs(keywordRuleId,tagId) VALUES(9,2)")
            BackupFiles.checkpoint(source)
            source.close()

            val snapshot = File(directory, "snapshot.db")
            BackupFiles.copySynced(context.getDatabasePath(sourceName), snapshot)
            val manifest = BackupManifest(1, "test", 1, 7, "2026-10-04T00:00:00Z",
                context.packageName, BackupFiles.sha256(snapshot), snapshot.length())
            val zip = File(directory, "backup.zip")
            zip.outputStream().use { BackupZip.write(it, BackupFormat.encodeManifest(manifest), snapshot,
                BackupFormat.encodeSettings(BackupSettings(true, io.github.peanutpersimmon.rido.presentation.plan.PlanViewMode.entries))) }
            ZipFile(zip).use { archive ->
                assertEquals(setOf("manifest.json", "database/rido.db", "settings.json"),
                    archive.entries().asSequence().map { it.name }.toSet())
            }
            val extracted = BackupZip.extract(zip, File(directory, "candidate.db"))
            BackupDatabaseValidator.validate(context, extracted.database, 7)
            BackupFiles.copySynced(extracted.database, context.getDatabasePath(restoredName))
            val restored = Room.databaseBuilder(context, RidoDatabase::class.java, restoredName).build()
            try {
                val db = restored.openHelper.writableDatabase
                fun scalar(statement: String): Long = db.query(statement).use { cursor ->
                    assertTrue(cursor.moveToFirst()); cursor.getLong(0)
                }
                assertEquals(4L, scalar("SELECT id FROM tasks WHERE parentTaskId=3"))
                db.query("SELECT location FROM tasks WHERE id=3").use {
                    assertTrue(it.moveToFirst()); assertEquals("实验室", it.getString(0))
                }
                assertEquals(10L, scalar("SELECT plannedReminderOffsetMinutes FROM tasks WHERE id=3"))
                assertEquals(30L, scalar("SELECT dueReminderOffsetMinutes FROM tasks WHERE id=3"))
                assertEquals(60L, scalar("SELECT startReminderOffsetMinutes FROM events WHERE id=5"))
                assertEquals(3L, scalar("SELECT taskId FROM sessions WHERE id=6"))
                assertEquals(5L, scalar("SELECT sourceEventId FROM sessions WHERE id=6"))
                assertEquals(2L, scalar("SELECT COUNT(*) FROM sessions WHERE kind IN ('NIGHT_SLEEP','NAP')"))
                assertEquals(1L, scalar("SELECT COUNT(*) FROM categories WHERE id=1"))
                assertEquals(1L, scalar("SELECT COUNT(*) FROM tags WHERE id=2"))
                assertEquals(1L, scalar("SELECT COUNT(*) FROM keyword_rules WHERE id=9 AND categoryId=1"))
                listOf("task_tag_cross_refs", "event_tag_cross_refs", "session_tag_cross_refs",
                    "keyword_rule_tag_cross_refs").forEach { table ->
                    assertEquals(1L, scalar("SELECT COUNT(*) FROM $table"))
                }
                db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            } finally { restored.close() }
        } finally {
            source.close()
            context.deleteDatabase(sourceName)
            context.deleteDatabase(restoredName)
            directory.deleteRecursively()
        }
    }

    @Test fun preflightRejectsVersionMismatchForeignKeyFailureAndCorruption() {
        val name = "backup-preflight-${UUID.randomUUID()}.db"
        val source = Room.databaseBuilder(context, RidoDatabase::class.java, name).build()
        val directory = File(context.cacheDir, "backup-negative-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            source.openHelper.writableDatabase.query("SELECT COUNT(*) FROM tasks").close()
            BackupFiles.checkpoint(source)
            source.close()
            val clean = File(directory, "clean.db")
            BackupFiles.copySynced(context.getDatabasePath(name), clean)
            BackupDatabaseValidator.validate(context, clean, 7)

            val mismatch = File(directory, "mismatch.db")
            clean.copyTo(mismatch)
            assertThrows(InvalidBackupException::class.java) {
                BackupDatabaseValidator.validate(context, mismatch, 5)
            }
            val foreign = File(directory, "foreign.db")
            clean.copyTo(foreign)
            SQLiteDatabase.openDatabase(foreign.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("PRAGMA foreign_keys=OFF")
                db.execSQL("INSERT INTO task_tag_cross_refs(taskId,tagId) VALUES(999,999)")
            }
            assertThrows(InvalidBackupException::class.java) {
                BackupDatabaseValidator.validate(context, foreign, 7)
            }
            val corrupt = File(directory, "corrupt.db").apply { writeText("not a SQLite database") }
            assertThrows(InvalidBackupException::class.java) {
                BackupDatabaseValidator.validate(context, corrupt, 7)
            }
            // Preflight touched only isolated files; the current source database retains its bytes.
            assertEquals(BackupFiles.sha256(clean), BackupFiles.sha256(context.getDatabasePath(name)))
        } finally {
            source.close()
            context.deleteDatabase(name)
            directory.deleteRecursively()
        }
    }

    @Test fun servicePreflightRejectsBadHashSizeVersionPackageAndSettingsBeforeCurrentDatabaseChanges() {
        val name = "backup-service-preflight-${UUID.randomUUID()}.db"
        val source = Room.databaseBuilder(context, RidoDatabase::class.java, name).build()
        val directory = File(context.cacheDir, "backup-service-negative-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            source.openHelper.writableDatabase.query("SELECT COUNT(*) FROM tasks").close()
            BackupFiles.checkpoint(source)
            source.close()
            val candidate = File(directory, "candidate.db")
            BackupFiles.copySynced(context.getDatabasePath(name), candidate)
            val current = context.getDatabasePath(RidoDatabase.DATABASE_NAME)
            val before = if (current.isFile) BackupFiles.sha256(current) else null
            val valid = BackupManifest(1, "test", 1, 7, "2026-10-04T00:00:00Z",
                context.packageName, BackupFiles.sha256(candidate), candidate.length())
            val settings = BackupFormat.encodeSettings(BackupSettings(false, PlanViewMode.entries))
            val service = BackupService(context)
            fun tryArchive(index: Int, manifest: BackupManifest, settingsBytes: ByteArray = settings) {
                val zip = File(directory, "case$index.zip")
                zip.outputStream().use {
                    BackupZip.write(it, BackupFormat.encodeManifest(manifest), candidate, settingsBytes)
                }
                assertThrows(InvalidBackupException::class.java) {
                    runBlocking { service.prepareImport(Uri.fromFile(zip)) }
                }
                assertEquals(before, if (current.isFile) BackupFiles.sha256(current) else null)
            }
            tryArchive(1, valid.copy(databaseSha256 = "b".repeat(64)))
            tryArchive(2, valid.copy(databaseSizeBytes = valid.databaseSizeBytes + 1))
            tryArchive(3, valid.copy(databaseVersion = 8))
            tryArchive(4, valid.copy(packageName = "not.rido"))
            tryArchive(5, valid.copy(backupFormatVersion = 2))
            tryArchive(6, valid, "{\"planViewOrder\":[\"UNKNOWN\"]}".toByteArray())
        } finally {
            source.close()
            context.deleteDatabase(name)
            directory.deleteRecursively()
        }
    }
}
