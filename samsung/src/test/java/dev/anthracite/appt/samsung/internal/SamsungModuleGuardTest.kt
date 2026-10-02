package dev.anthracite.appt.samsung.internal

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Module-level guard tests for the S04 secret-store surface (docs/architecture/testing.md named
 * contracts and data.md): samsung code produces no log output, and the pairing-secret types are
 * structurally invisible outside this module, so no Room, DataStore or diagnostics code can grow a
 * dependency on them.
 */
class SamsungModuleGuardTest {
    private fun productionSources(): List<File> {
        val root = File("src/main")
        assertTrue(
            "expected to run from the samsung module, ran from ${root.absolutePath}",
            root.isDirectory,
        )
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun appSources(): List<File> {
        val root = File("../app/src/main")
        assertTrue(
            "expected to run from the samsung module, ran from ${root.absolutePath}",
            root.isDirectory,
        )
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun linesMatching(source: File, pattern: Regex): List<String> =
        source
            .readLines()
            .mapIndexed { index, line -> "${source.path}:${index + 1}" to line }
            .filter { (_, line) -> pattern.containsMatchIn(line) }
            .map { (at, _) -> at }

    @Test
    fun samsungHasNoLogCalls() {
        val logging =
            Regex(
                """\b(Log\.[ivwde]|println|printStackTrace|System\.(out|err)\b)|\bprint\(""",
                RegexOption.IGNORE_CASE,
            )
        val offenders = productionSources().flatMap { linesMatching(it, logging) }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun thePairingSecretStaysInsideTheSamsungModule() {
        val secretTypes =
            Regex(
                """\b(PairingSecret|SamsungSecretStore|SamsungDeviceRecord|StoredSecret|SecretStoreException)\b"""
            )
        val offenders = appSources().flatMap { linesMatching(it, secretTypes) }
        assertEquals(emptyList<String>(), offenders)
    }
}
