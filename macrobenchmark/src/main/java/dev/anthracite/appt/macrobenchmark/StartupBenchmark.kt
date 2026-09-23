package dev.anthracite.appt.macrobenchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start measurement for the AppT walking skeleton.
 *
 * S01 exists to prove the test-only module compiles and targets `:app` without
 * entering the production graph. The reliability *targets* this measurement
 * eventually defends are owned by docs/architecture/reliability.md and are
 * asserted by the harness in S15; this slice sets no threshold.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupToWelcome() = benchmarkRule.measureRepeated(
        packageName = "dev.anthracite.appt",
        metrics = listOf(StartupTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }
}
