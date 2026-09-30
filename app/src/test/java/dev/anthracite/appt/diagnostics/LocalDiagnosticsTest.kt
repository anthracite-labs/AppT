package dev.anthracite.appt.diagnostics

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalDiagnosticsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun localRecordIsBoundedAndRedacted() {
        val noBackupRoot = folder.newFolder("no-backup")
        val diagnostics =
            LocalDiagnostics(
                File(noBackupRoot, LocalDiagnostics.DIAGNOSTICS_DIRECTORY),
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                Dispatchers.Unconfined,
            )

        repeat(250) { index ->
            assertTrue(diagnostics.recordApp(AppDiagnosticName.RemoteInteraction, index.toLong()))
        }
        assertEquals(LocalDiagnostics.MAX_EVENTS, diagnostics.appEvents.value.size)
        assertEquals("oldest app events are dropped", 50L, diagnostics.appEvents.value.first().elapsedMs)
        val record = File(noBackupRoot, "diagnostics/v1/${LocalDiagnostics.RECORD_FILE}")
        assertTrue(record.isFile)
        assertTrue(record.length() <= LocalDiagnostics.MAX_FILE_BYTES)
        assertEquals(LocalDiagnostics.MAX_EVENTS, diagnostics.durableEvents.value.size)
        assertFalse(record.readText().contains("commandText"))
    }

    @Test
    fun plantedSensitiveValuesAreRedactedAndForbiddenFieldsAreRejected() {
        val planted =
            "https://tv.example/path 10.0.0.12 fe:dc:ba:98:76:54 user@example.com " +
                "3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e planted-token-1234"
        val safe =
            DiagnosticRedactor.safeField(
                "state",
                planted,
                secretValues = setOf("planted-token-1234"),
            )
        assertNotNull(safe)
        val redacted = safe!!.second
        listOf(
                "https://tv.example",
                "10.0.0.12",
                "fe:dc:ba:98:76:54",
                "user@example.com",
                "3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e",
                "planted-token-1234",
            )
            .forEach { assertFalse("sensitive value survives: $it", redacted.contains(it)) }
        assertNull(DiagnosticRedactor.safeField("commandText", "KEY_VOLUP"))
        assertNull(DiagnosticRedactor.safeField("address", "10.0.0.12"))
    }

    @Test
    fun samsungBufferAlsoDropsOldestAtItsIndependentBound() {
        val diagnostics =
            LocalDiagnostics(
                File(folder.newFolder(), LocalDiagnostics.DIAGNOSTICS_DIRECTORY),
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                Dispatchers.Unconfined,
            )
        repeat(205) { index ->
            diagnostics.recordSamsung(
                dev.anthracite.appt.samsung.SessionSnapshot(
                    dev.anthracite.appt.samsung.SessionState.Ready,
                    dev.anthracite.appt.samsung.TvCapabilities(setOf(dev.anthracite.appt.samsung.RemoteKey.Home)),
                ),
                index.toLong(),
            )
        }
        assertEquals(LocalDiagnostics.MAX_EVENTS, diagnostics.samsungEvents.value.size)
        assertEquals(5L, diagnostics.samsungEvents.value.first().elapsedMs)
        assertTrue(diagnostics.samsungEvents.value.all { it.fields.keys.all { key -> key in setOf("state", "repairReason", "capabilities") } })
    }
}
