package dev.anthracite.appt.discovery

import dev.anthracite.appt.gate.LocalNetworkPhase
import dev.anthracite.appt.samsung.ControlAvailability
import dev.anthracite.appt.samsung.DiscoveryEvent
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import dev.anthracite.appt.testing.FakePermissionGate
import dev.anthracite.appt.testing.FakeSamsungTvs
import dev.anthracite.appt.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Discovery ViewModel states through the fake `SamsungTvs` (presentation.md#discovery). */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val tvs = FakeSamsungTvs()
    private val gate = FakePermissionGate(LocalNetworkPhase.Granted)

    private fun viewModel() = DiscoveryViewModel(tvs, gate)

    @Test
    fun entersScanningAndStartsExactlyOneScanImmediately() =
        runTest(mainRule.dispatcher) {
            val viewModel = viewModel()
            runCurrent()
            assertEquals(1, tvs.discoverCalls)
            assertEquals(
                DiscoveryUiState(ScanPhase.Scanning, emptyList(), showEmptyState = false),
                viewModel.state.value,
            )
        }

    @Test
    fun foundTelevisionsBecomeCardsInArrivalOrder() =
        runTest(mainRule.dispatcher) {
            val viewModel = viewModel()
            runCurrent()
            tvs.latest.send(FakeSamsungTvs.found())
            tvs.latest.send(
                FakeSamsungTvs.found(
                    id = FakeSamsungTvs.OLDER_ID,
                    name = "Older TV",
                    availability = ControlAvailability.Unsupported,
                )
            )
            tvs.latest.send(
                FakeSamsungTvs.found(
                    id = "local-1",
                    name = "Bedroom",
                    ControlAvailability.ReadyToOpen,
                    true,
                )
            )
            runCurrent()

            assertEquals(
                listOf(
                    TvCardUi(
                        TvId(FakeSamsungTvs.LIVING_ROOM_ID),
                        "Living Room TV",
                        CardState.NeedsPairing,
                        false,
                    ),
                    TvCardUi(
                        TvId(FakeSamsungTvs.OLDER_ID),
                        "Older TV",
                        CardState.Unsupported,
                        false,
                    ),
                    TvCardUi(TvId("local-1"), "Bedroom", CardState.Ready, true),
                ),
                viewModel.state.value.cards,
            )
            assertEquals(ScanPhase.Scanning, viewModel.state.value.scan)
        }

    @Test
    fun aRepeatedIdStaysOneCard() =
        runTest(mainRule.dispatcher) {
            val viewModel = viewModel()
            runCurrent()
            tvs.latest.send(FakeSamsungTvs.found())
            tvs.latest.send(FakeSamsungTvs.found(name = "Renamed"))
            runCurrent()
            assertEquals(listOf("Living Room TV"), viewModel.state.value.cards.map { it.label })
        }

    @Test
    fun finishedWithNothingShowsTheEmptyState() =
        runTest(mainRule.dispatcher) {
            val viewModel = viewModel()
            runCurrent()
            tvs.latest.send(DiscoveryEvent.Finished)
            advanceUntilIdle()
            assertEquals(
                DiscoveryUiState(ScanPhase.Finished, emptyList(), showEmptyState = true),
                viewModel.state.value,
            )
        }

    @Test
    fun finishedWithCardsHasNoEmptyState() =
        runTest(mainRule.dispatcher) {
            val viewModel = viewModel()
            runCurrent()
            tvs.latest.send(FakeSamsungTvs.found())
            tvs.latest.send(DiscoveryEvent.Finished)
            advanceUntilIdle()
            assertEquals(ScanPhase.Finished, viewModel.state.value.scan)
            assertFalse(viewModel.state.value.showEmptyState)
            assertEquals(1, viewModel.state.value.cards.size)
        }

    @Test
    fun failureIsShownAndOnlyDenialReachesTheGate() =
        runTest(mainRule.dispatcher) {
            val viewModel = viewModel()
            runCurrent()
            tvs.latest.send(DiscoveryEvent.Failed(TvFailure.Unreachable))
            advanceUntilIdle()
            assertEquals(ScanPhase.Failed(TvFailure.Unreachable), viewModel.state.value.scan)
            assertFalse(viewModel.state.value.showEmptyState)
            assertEquals(0, gate.deniedCalls)

            viewModel.onRescan()
            runCurrent()
            tvs.latest.send(DiscoveryEvent.Failed(TvFailure.LocalNetworkDenied))
            advanceUntilIdle()
            assertEquals(ScanPhase.Failed(TvFailure.LocalNetworkDenied), viewModel.state.value.scan)
            assertEquals(1, gate.deniedCalls)
            assertEquals("denial is not retried by the ViewModel", 2, tvs.discoverCalls)
        }

    @Test
    fun rescanStartsAFreshDiscoverAndCancelsThePreviousScan() =
        runTest(mainRule.dispatcher) {
            val viewModel = viewModel()
            runCurrent()
            tvs.latest.send(FakeSamsungTvs.found())
            runCurrent()
            val first = tvs.latest

            viewModel.onRescan()
            runCurrent()
            assertEquals(2, tvs.discoverCalls)
            assertTrue(first.cancelled)
            assertEquals(DiscoveryUiState.Initial, viewModel.state.value)

            tvs.latest.send(DiscoveryEvent.Finished)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.showEmptyState)
        }

    @Test
    fun pickingACardCancelsTheScanAndGoesNoFurther() =
        runTest(mainRule.dispatcher) {
            val viewModel = viewModel()
            runCurrent()
            tvs.latest.send(FakeSamsungTvs.found())
            runCurrent()

            viewModel.onPick(TvId(FakeSamsungTvs.LIVING_ROOM_ID))
            advanceUntilIdle()
            assertTrue(tvs.latest.cancelled)
            assertEquals(ScanPhase.Finished, viewModel.state.value.scan)
            assertEquals(1, viewModel.state.value.cards.size)
            assertEquals("no new scan, no other side effect", 1, tvs.discoverCalls)
        }

    @Test
    fun pickingAnUnsupportedCardIsNotAnIntent() =
        runTest(mainRule.dispatcher) {
            val viewModel = viewModel()
            runCurrent()
            tvs.latest.send(
                FakeSamsungTvs.found(
                    id = FakeSamsungTvs.OLDER_ID,
                    availability = ControlAvailability.Unsupported,
                )
            )
            runCurrent()

            viewModel.onPick(TvId(FakeSamsungTvs.OLDER_ID))
            viewModel.onPick(TvId("unknown"))
            runCurrent()
            assertFalse(tvs.latest.cancelled)
            assertEquals(ScanPhase.Scanning, viewModel.state.value.scan)
        }

    @Test
    fun restorationScansOnceThenStops() =
        runTest(mainRule.dispatcher) {
            // After process death the restored Discovery route gets a new ViewModel.
            val restored = viewModel()
            runCurrent()
            tvs.latest.send(DiscoveryEvent.Finished)
            advanceUntilIdle()
            assertEquals(ScanPhase.Finished, restored.state.value.scan)
            assertEquals("exactly one fresh bounded scan, then it stops", 1, tvs.discoverCalls)
        }

    @Test
    fun leavingTheForegroundCancelsTheScanAndReturningStartsOneFreshScan() =
        runTest(mainRule.dispatcher) {
            val viewModel = viewModel()
            runCurrent()
            viewModel.onStarted()
            runCurrent()
            assertEquals("the first start does not duplicate the scan", 1, tvs.discoverCalls)
            tvs.latest.send(FakeSamsungTvs.found())
            runCurrent()

            viewModel.onStopped()
            runCurrent()
            assertTrue("no scan runs in the background", tvs.latest.cancelled)
            assertEquals(1, tvs.discoverCalls)

            viewModel.onStarted()
            runCurrent()
            assertEquals(2, tvs.discoverCalls)
            assertEquals(
                "the resumed scan is fresh",
                DiscoveryUiState.Initial,
                viewModel.state.value,
            )

            viewModel.onStarted()
            runCurrent()
            assertEquals("the interrupted scan is resumed once", 2, tvs.discoverCalls)
        }

    @Test
    fun aFinishedScanIsNotRestartedOnReturn() =
        runTest(mainRule.dispatcher) {
            val viewModel = viewModel()
            runCurrent()
            tvs.latest.send(DiscoveryEvent.Finished)
            runCurrent()

            viewModel.onStopped()
            viewModel.onStarted()
            runCurrent()
            assertEquals(1, tvs.discoverCalls)
            assertEquals(ScanPhase.Finished, viewModel.state.value.scan)
        }
}
