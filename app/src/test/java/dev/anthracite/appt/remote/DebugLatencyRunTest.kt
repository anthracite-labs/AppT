package dev.anthracite.appt.remote

import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.TvFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLatencyRunTest {
    @Test
    fun eachOfFiveMeasuredSamplesRequiresItsOwnSuccessfulWarmup() {
        val run = DebugLatencyRun()
        val accepted = CommandResult.Accepted

        assertFalse(run.canMeasure)
        assertFalse(run.recordMeasurement(accepted, 1.0))

        repeat(DebugLatencyRun.REQUIRED_MEASUREMENTS) { index ->
            assertTrue("warm-up $index should unlock exactly one sample", run.recordWarmup(accepted))
            assertTrue(run.canMeasure)
            assertTrue(run.recordMeasurement(accepted, (index + 1).toDouble()))
            assertFalse("warm-up must be repeated before the next sample", run.canMeasure)
        }

        assertFalse(run.recordWarmup(accepted))
        assertFalse(run.recordMeasurement(accepted, 6.0))
    }

    @Test
    fun failedWarmupDoesNotUnlockMeasurement() {
        val run = DebugLatencyRun()

        assertFalse(run.recordWarmup(CommandResult.Rejected(TvFailure.Unavailable)))
        assertFalse(run.canMeasure)
        assertFalse(run.recordMeasurement(CommandResult.Accepted, 10.0))
    }

    @Test
    fun reportUsesFiveSamplesAndNearestRankP50AndP95() {
        val run = DebugLatencyRun()
        listOf(40.0, 10.0, 30.0, 50.0, 20.0).forEach { sample ->
            assertTrue(run.recordWarmup(CommandResult.Accepted))
            assertTrue(run.recordMeasurement(CommandResult.Accepted, sample))
        }

        val report =
            run.report(
                candidateSha = "a".repeat(40),
                phoneModel = "Pixel 7a",
                androidVersion = "16",
                apiLevel = 36,
            )

        assertEquals(5, report?.runCount)
        assertEquals(listOf(40.0, 10.0, 30.0, 50.0, 20.0), report?.samplesMillis)
        assertEquals(30.0, report?.p50Millis ?: -1.0, 0.0)
        assertEquals(50.0, report?.p95Millis ?: -1.0, 0.0)
        assertTrue(report?.p50Pass == false)
        assertNull(run.report("short-sha", "Pixel 7a", "16", 36))
    }

    @Test
    fun measuredTimingStartsInTheUiCallbackAndStopsBeforeCompletionProcessing() = runTest {
        val events = mutableListOf<String>()
        var clockIndex = 0
        val measured =
            DebugMeasuredCommand {
                events += "clock-${++clockIndex}"
                if (clockIndex == 1) 1_000_000L else 26_000_000L
            }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        var result: CommandResult? = null
        var elapsedMillis: Double? = null

        measured.dispatch(
            scope = scope,
            command = {
                events += "session-command"
                CommandResult.Accepted
            },
            onComplete = { commandResult, duration ->
                events += "process-result"
                result = commandResult
                elapsedMillis = duration
            },
        )

        assertEquals(listOf("clock-1"), events)
        runCurrent()

        assertEquals(
            listOf("clock-1", "session-command", "clock-2", "process-result"),
            events,
        )
        assertEquals(CommandResult.Accepted, result)
        assertEquals(25.0, elapsedMillis ?: -1.0, 0.0)
    }
}
