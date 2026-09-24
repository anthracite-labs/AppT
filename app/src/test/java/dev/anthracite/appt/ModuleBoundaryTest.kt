package dev.anthracite.appt

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * modules.md / samsung-interface.md: `app` depends on the public `dev.anthracite.appt.samsung`
 * package only, never on the module's `internal` package. Gradle runs unit tests from the module
 * directory.
 */
class ModuleBoundaryTest {
    private val forbidden = "dev.anthracite.appt.samsung" + ".internal"

    @Test
    fun appNeverReachesIntoSamsungInternals() {
        val sources = File("src").walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") }.toList()
        assertTrue("expected to find app sources from ${File("").absolutePath}", sources.size > 10)
        val offenders = sources.filter { it.readText().contains(forbidden) }.map { it.path }
        assertEquals(emptyList<String>(), offenders)
    }
}
