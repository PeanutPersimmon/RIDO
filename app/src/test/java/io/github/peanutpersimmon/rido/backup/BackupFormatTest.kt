package io.github.peanutpersimmon.rido.backup

import io.github.peanutpersimmon.rido.presentation.plan.PlanViewMode
import org.junit.Assert.*
import org.junit.Test

class BackupFormatTest {
    private val packageName = "io.github.peanutpersimmon.rido"
    private val manifest = BackupManifest(1, "1.0", 1, 7, "2026-10-04T00:00:00Z",
        packageName, "a".repeat(64), 123)

    @Test fun manifestRoundTripKeepsFormatAndRoomVersionsSeparate() {
        assertEquals(manifest, BackupFormat.parseManifest(BackupFormat.encodeManifest(manifest), packageName))
        assertEquals(6, BackupFormat.parseManifest(BackupFormat.encodeManifest(
            manifest.copy(databaseVersion = 6)), packageName).databaseVersion)
        assertEquals(1, BACKUP_FORMAT_VERSION)
        assertEquals(7, ROOM_VERSION)
    }

    @Test fun unsupportedFormatPackageAndFutureDatabaseAreRejected() {
        listOf(
            manifest.copy(backupFormatVersion = 2),
            manifest.copy(packageName = "another.app"),
            manifest.copy(databaseVersion = 8),
        ).forEach { value ->
            assertThrows(InvalidBackupException::class.java) {
                BackupFormat.parseManifest(BackupFormat.encodeManifest(value), packageName)
            }
        }
    }

    @Test fun settingsRoundTripContainsOnlyWhiteListedKeys() {
        val settings = BackupSettings(true, PlanViewMode.entries.reversed())
        val json = BackupFormat.encodeSettings(settings).toString(Charsets.UTF_8)
        assertEquals(settings, BackupFormat.parseSettings(json.toByteArray()))
        assertTrue(json.contains("\"night\":true"))
        assertTrue(json.contains("\"planViewOrder\""))
        listOf("encrypted_api_key", "api_key_iv", "workspace_id", "qwen_credentials",
            "provider", "model", "endpoint").forEach { assertFalse(json.contains(it)) }
    }

    @Test fun missingOptionalSettingsUseDefaultsButInvalidValuesFail() {
        val defaults = BackupFormat.parseSettings("{}".toByteArray())
        assertFalse(defaults.night)
        assertEquals(PlanViewMode.entries.toSet(), defaults.planViewOrder.toSet())
        listOf(
            "{\"night\":\"true\"}",
            "{\"night\":1}",
            "{\"planViewOrder\":[\"UNKNOWN\"]}",
            "{\"planViewOrder\":[\"DAY\",\"DAY\"]}",
            "{\"encrypted_api_key\":\"secret\"}",
        ).forEach { value ->
            assertThrows(InvalidBackupException::class.java) {
                BackupFormat.parseSettings(value.toByteArray())
            }
        }
    }
}
