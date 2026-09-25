package dev.anthracite.appt.samsung.internal

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The bounded, cancellation-safe wait used for AirPlay service resolution (NsdAirPlayBrowser).
 * Virtual time, so the bound is exact.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoundedCallbackTest {

    @Test
    fun theDeliveredOutcomeIsReturnedAndNothingIsWithdrawn() = runTest {
        var withdrawn = 0
        lateinit var deliver: (String?) -> Unit
        val outcome = async {
            awaitCallback<String>(BOUND) { callback ->
                deliver = callback
                val withdraw: () -> Unit = { withdrawn++ }
                withdraw
            }
        }
        runCurrent()
        deliver("resolved")
        assertEquals("resolved", outcome.await())
        assertEquals(0, withdrawn)
    }

    @Test
    fun aFailedRequestIsNullAndNothingIsWithdrawn() = runTest {
        var withdrawn = 0
        val outcome =
            awaitCallback<String>(BOUND) { callback ->
                callback(null)
                val withdraw: () -> Unit = { withdrawn++ }
                withdraw
            }
        assertNull(outcome)
        assertEquals(0, withdrawn)
    }

    @Test
    fun aSilentPlatformIsWithdrawnAtTheBound() = runTest {
        var withdrawn = 0
        val started = currentTime
        val outcome = awaitCallback<String>(BOUND) { { withdrawn++ } }
        assertNull(outcome)
        assertEquals(BOUND.inWholeMilliseconds, currentTime - started)
        assertEquals(1, withdrawn)
    }

    @Test
    fun cancellingTheCallerWithdrawsThePendingRequest() = runTest {
        var withdrawn = 0
        val waiting = launch { awaitCallback<String>(BOUND) { { withdrawn++ } } }
        runCurrent()
        waiting.cancel()
        runCurrent()
        assertEquals(1, withdrawn)
    }

    @Test
    fun aCallbackArrivingAfterTheBoundIsIgnored() = runTest {
        lateinit var deliver: (String?) -> Unit
        val outcome =
            awaitCallback<String>(BOUND) { callback ->
                deliver = callback
                val withdraw: () -> Unit = {}
                withdraw
            }
        assertNull(outcome)
        // Must not throw or resume anything: the wait is already over.
        deliver("late")
    }

    private companion object {
        val BOUND = 2.seconds
    }
}
