package dev.anthracite.appt.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical-phone command measurement for Issue #117. The trace starts at the Compose command
 * callback and ends when Samsung's command call returns after the local socket write; TV response
 * and visible action are outside this measurement. Each measured tap is preceded by an unmeasured
 * warm-up tap on the same Ready session.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class CommandLatencyBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun commandLatencyBudget() =
        benchmarkRule.measureRepeated(
            packageName = RemoteJourney.PACKAGE_NAME,
            metrics = listOf(TraceSectionMetric(COMMAND_LATENCY_TRACE)),
            iterations = MEASURED_RUNS,
            setupBlock = {
                with(RemoteJourney) { openReadyRemote() }
                warmUpCommand()
            },
        ) {
            clickCommand()
        }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.warmUpCommand() {
        clickCommand()
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.clickCommand() {
        val selector = By.text(RemoteJourney.COMMAND_LABEL)
        check(device.wait(androidx.test.uiautomator.Until.hasObject(selector), SCREEN_TIMEOUT_MS)) {
            "The Ready Remote volume control is not visible."
        }
        checkNotNull(device.findObject(selector)).click()
    }

    private companion object {
        const val COMMAND_LATENCY_TRACE = "commandLatencyBudget"
        const val MEASURED_RUNS = 5
        const val SCREEN_TIMEOUT_MS = 15_000L
    }
}
