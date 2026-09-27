package dev.anthracite.appt.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The scheduling contract the session, Pairing, and Remote tests are built on.
 *
 * `ActiveRemoteHost` observes the session in the scope the test hands it, and those tests hand it
 * `TestScope.backgroundScope` because an observation never completes and `runTest` would otherwise
 * wait for it. What that costs is that `advanceUntilIdle()` stops before those observers run, so a
 * published snapshot would never reach `ActiveRemoteHost.current` and every assertion after it
 * would read a stale state. [settle] is what the tests call instead; this pins both halves of the
 * reason, so a coroutines upgrade that changes the upstream behaviour is caught here rather than
 * showing up as a cluster of quietly-stale assertions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestScopeSettleTest {
    @Test
    fun advanceUntilIdleLeavesABackgroundCollectorUnprocessed() = runTest {
        val source = MutableStateFlow(0)
        val observed = MutableStateFlow(-1)
        backgroundScope.launch { source.collect { observed.value = it } }

        source.value = 1
        advanceUntilIdle()

        assertEquals(
            "upstream leaves background work to runCurrent/advanceTimeBy, so this stays behind",
            -1,
            observed.value,
        )
    }

    @Test
    fun settleDrivesTheBackgroundCollectorAndEverythingItFeeds() = runTest {
        val source = MutableStateFlow(0)
        val observed = MutableStateFlow(-1)
        backgroundScope.launch { source.collect { observed.value = it } }

        source.value = 1
        settle()

        assertEquals(1, observed.value)
    }
}
