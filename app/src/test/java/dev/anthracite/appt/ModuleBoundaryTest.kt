package dev.anthracite.appt

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * modules.md / samsung-interface.md: `app` depends on `dev.anthracite.appt.samsung` only and never
 * on `dev.anthracite.appt.samsung.internal`. Gradle runs unit tests from the module directory.
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
