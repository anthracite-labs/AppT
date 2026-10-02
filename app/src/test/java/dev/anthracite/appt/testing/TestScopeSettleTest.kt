package dev.anthracite.appt.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

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
