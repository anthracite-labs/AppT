package dev.anthracite.appt.pairing

import dev.anthracite.appt.data.FakeTvProfileDao
import dev.anthracite.appt.data.NameSource
import dev.anthracite.appt.data.TvProfile
import dev.anthracite.appt.data.TvProfiles
import dev.anthracite.appt.remote.ActiveRemoteHost
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.RepairReason
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import dev.anthracite.appt.testing.FakeSamsungTvs
import dev.anthracite.appt.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Pairing observes the host's session and never opens one (presentation.md#pairing). */
@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val tvs = FakeSamsungTvs()
    private val livingRoom = TvId(FakeSamsungTvs.LIVING_ROOM_ID)
    private val older = TvId(FakeSamsungTvs.OLDER_ID)
    private val dao = FakeTvProfileDao()
    private val profiles = TvProfiles(dao) { 1L }

    /** A host that has already entered [tvId], as the Discovery route does. */
    private suspend fun TestScope.entered(tvId: TvId = livingRoom): ActiveRemoteHost {
        val host = ActiveRemoteHost(tvs, this)
        host.enter(tvId)
        advanceUntilIdle()
        return host
    }

    private fun profile(tvId: TvId, name: String) =
        TvProfile(
            tvId = tvId.value,
            friendlyName = name,
            nameSource = NameSource.TV,
            stableIdentity = true,
            createdAt = 1L,
            lastOpenedAt = null,
        )

    @Test
    fun connectingIsTheInitialPhaseBeforeAnySessionIsHeld() =
        runTest(mainRule.dispatcher) {
            val viewModel = PairingViewModel(livingRoom, ActiveRemoteHost(tvs, this), profiles)
            advanceUntilIdle()

            assertEquals(PairingPhase.Connecting, viewModel.state.value.phase)
            assertTrue(!viewModel.state.value.recallHintVisible)
        }

    @Test
    fun theTelevisionNameComesFromTheProfileRow() =
        runTest(mainRule.dispatcher) {
            dao.upsert(profile(livingRoom, "Living Room TV"))
            val host = entered()
            val viewModel = PairingViewModel(livingRoom, host, profiles)
            advanceUntilIdle()

            assertEquals("Living Room TV", viewModel.state.value.tvName)
        }

    @Test
    fun awaitingApprovalShowsTheRecallHint() =
        runTest(mainRule.dispatcher) {
            val host = entered()
            val viewModel = PairingViewModel(livingRoom, host, profiles)
            advanceUntilIdle()
            tvs.sessionFor(livingRoom)!!.publish(SessionState.AwaitingTvApproval)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(PairingPhase.WaitingForApproval, state.phase)
            assertTrue(state.recallHintVisible)
        }

    @Test
    fun readyBecomesSucceededWithoutARecallHint() =
        runTest(mainRule.dispatcher) {
            val host = entered()
            val viewModel = PairingViewModel(livingRoom, host, profiles)
            advanceUntilIdle()
            tvs.sessionFor(livingRoom)!!.ready(setOf(RemoteKey.VolumeUp))
            advanceUntilIdle()

            assertEquals(PairingPhase.Succeeded, viewModel.state.value.phase)
            assertTrue(!viewModel.state.value.recallHintVisible)
        }

    @Test
    fun aRepairPhaseReportsTheReasonInOrdinaryLanguage() =
        runTest(mainRule.dispatcher) {
            val host = entered()
            val viewModel = PairingViewModel(livingRoom, host, profiles)
            advanceUntilIdle()
            tvs.sessionFor(livingRoom)!!.publish(
                SessionState.NeedsRepair,
                repairReason = RepairReason.ApprovalDenied,
            )
            advanceUntilIdle()

            val phase = viewModel.state.value.phase
            assertTrue(phase is PairingPhase.Failed)
            assertEquals(TvFailure.NeedsRepair, (phase as PairingPhase.Failed).failure)
            assertTrue(!viewModel.state.value.recallHintVisible)
        }

    @Test
    fun retryApprovalIsForwardedToTheSession() =
        runTest(mainRule.dispatcher) {
            val host = entered()
            val viewModel = PairingViewModel(livingRoom, host, profiles)
            advanceUntilIdle()
            val session = tvs.sessionFor(livingRoom)!!
            session.publish(SessionState.NeedsRepair, repairReason = RepairReason.ApprovalDenied)
            advanceUntilIdle()

            viewModel.onRetryApproval()
            advanceUntilIdle()

            assertEquals(1, session.retryApprovals)
        }

    @Test
    fun pairingNeverOpensASessionItself() =
        runTest(mainRule.dispatcher) {
            val viewModel = PairingViewModel(livingRoom, ActiveRemoteHost(tvs, this), profiles)
            advanceUntilIdle()

            assertEquals("the host is the only opener", emptyList<TvId>(), tvs.openedIds)
            assertEquals(PairingPhase.Connecting, viewModel.state.value.phase)
        }

    @Test
    fun anotherTelevisionsSessionIsNotRead() =
        runTest(mainRule.dispatcher) {
            val host = entered()
            val viewModel = PairingViewModel(older, host, profiles)
            advanceUntilIdle()

            tvs.sessionFor(livingRoom)!!.ready()
            advanceUntilIdle()

            assertEquals(
                "a session for another television is not this route's",
                PairingPhase.Connecting,
                viewModel.state.value.phase,
            )
        }
}
