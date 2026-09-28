package dev.anthracite.appt.savedpairing

import dev.anthracite.appt.data.FakeTvProfileDao
import dev.anthracite.appt.data.TvProfiles
import dev.anthracite.appt.pairing.PairingPhase
import dev.anthracite.appt.pairing.PairingUiState
import dev.anthracite.appt.pairing.PairingViewModel
import dev.anthracite.appt.preferences.PreferenceStore
import dev.anthracite.appt.remote.ActiveRemoteHost
import dev.anthracite.appt.remote.ConnectionUi
import dev.anthracite.appt.remote.RemoteUiState
import dev.anthracite.appt.remote.RemoteViewModel
import dev.anthracite.appt.samsung.RepairReason
import dev.anthracite.appt.samsung.SessionSnapshot
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCapabilities
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import dev.anthracite.appt.testing.FakeSamsungTvs
import dev.anthracite.appt.testing.MainDispatcherRule
import dev.anthracite.appt.testing.settle
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import androidx.datastore.preferences.core.PreferenceDataStoreFactory

/**
 * How the saved-pairing session states present (Issue #91, presentation.md): a denied approval
 * stays a retryable prompt, a television that no longer matches the saved connection is named as
 * such and is only ever answered by the explicit confirmed re-pair, and saved material that cannot
 * be read offers a fresh pairing — never a silent retry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedPairingUiTest {
    @get:Rule val mainRule = MainDispatcherRule()

    @get:Rule val folder = TemporaryFolder()

    private val tvs = FakeSamsungTvs()
    private val livingRoom = TvId(FakeSamsungTvs.LIVING_ROOM_ID)

    private fun snapshot(state: SessionState, reason: RepairReason?) =
        SessionSnapshot(state, TvCapabilities(emptySet()), reason)

    // --- the pure mappings ---------------------------------------------------------------

    @Test
    fun pairingMapsEachNeedsRepairReasonToItsOwnPresentation() {
        val denied =
            PairingUiState.of("TV", snapshot(SessionState.NeedsRepair, RepairReason.ApprovalDenied))
        assertEquals(PairingPhase.Failed(TvFailure.NeedsRepair), denied.phase)

        val timedOut =
            PairingUiState.of("TV", snapshot(SessionState.NeedsRepair, RepairReason.ApprovalTimedOut))
        assertEquals(PairingPhase.Failed(TvFailure.TimedOut), timedOut.phase)

        val changed =
            PairingUiState.of("TV", snapshot(SessionState.NeedsRepair, RepairReason.IdentityChanged))
        assertEquals(PairingPhase.Failed(TvFailure.IdentityChanged), changed.phase)

        val rejected =
            PairingUiState.of("TV", snapshot(SessionState.NeedsRepair, RepairReason.TokenRejected))
        assertEquals(PairingPhase.Failed(TvFailure.IdentityChanged), rejected.phase)

        val unreadable = PairingUiState.of("TV", snapshot(SessionState.NeedsRepair, null))
        assertEquals(PairingPhase.Failed(TvFailure.SecretsUnavailable), unreadable.phase)
    }

    @Test
    fun theRemoteNamesTheIdentityChangeAndNothingElse() {
        assertEquals(
            ConnectionUi.NeedsRepair(TvFailure.IdentityChanged),
            RemoteUiState.of("TV", snapshot(SessionState.NeedsRepair, RepairReason.IdentityChanged))
                .connection,
        )
        assertEquals(
            ConnectionUi.NeedsRepair(TvFailure.IdentityChanged),
            RemoteUiState.of("TV", snapshot(SessionState.NeedsRepair, RepairReason.TokenRejected))
                .connection,
        )
        assertEquals(
            ConnectionUi.NeedsRepair(TvFailure.NeedsRepair),
            RemoteUiState.of("TV", snapshot(SessionState.NeedsRepair, RepairReason.ApprovalDenied))
                .connection,
        )
        assertEquals(
            ConnectionUi.NeedsRepair(TvFailure.SecretsUnavailable),
            RemoteUiState.of("TV", snapshot(SessionState.NeedsRepair, null)).connection,
        )
    }

    // --- the confirmed re-pair through the ViewModels --------------------------------------

    @Test
    fun pairAgainCallsConfirmRepairOnTheRetainedSession() = runTest {
        val host = entered()
        val viewModel = PairingViewModel(livingRoom, host, TvProfiles(FakeTvProfileDao()) { 1L })
        val session = tvs.sessionFor(livingRoom)!!
        session.publish(SessionState.NeedsRepair, repairReason = RepairReason.IdentityChanged)
        settle()
        assertEquals(
            "the control exists for the failed state",
            SessionState.NeedsRepair,
            host.current.value?.snapshot?.state,
        )

        viewModel.onPairAgain()
        settle()

        assertEquals("the explicit re-pair reaches the session", 1, session.confirmRepairs)
        assertEquals("and it is not a plain retry", 0, session.retryApprovals)
    }

    @Test
    fun theRemoteConfirmsRepairOnTheRetainedSession() = runTest {
        val host = entered()
        val viewModel =
            RemoteViewModel(livingRoom, host, TvProfiles(FakeTvProfileDao()) { 1L }, preferenceStore())
        val session = tvs.sessionFor(livingRoom)!!
        session.publish(SessionState.NeedsRepair, repairReason = RepairReason.IdentityChanged)
        settle()
        assertEquals(
            "the control exists for the failed state",
            SessionState.NeedsRepair,
            host.current.value?.snapshot?.state,
        )

        viewModel.onConfirmRepair()
        settle()

        assertEquals(1, session.confirmRepairs)
    }

    @Test
    fun pairAgainOnAnUnreadableSavedConnectionForgetsAndPairsFresh() = runTest {
        val host = entered()
        val viewModel = PairingViewModel(livingRoom, host, TvProfiles(FakeTvProfileDao()) { 1L })
        val session = tvs.sessionFor(livingRoom)!!
        // SecretsUnavailable publishes NeedsRepair with no repair reason; confirmRepair ignores it.
        session.publish(SessionState.NeedsRepair)
        settle()

        viewModel.onPairAgain()
        settle()

        assertEquals(
            "the unreadable Samsung relationship is forgotten",
            listOf(livingRoom),
            tvs.forgottenIds,
        )
        assertEquals(
            "the session's confirmRepair is not the secrets path",
            0,
            session.confirmRepairs,
        )
        assertTrue("the stuck session was closed", session.closed)
        assertTrue(
            "a fresh session was opened for the same television",
            tvs.sessionFor(livingRoom) !== session,
        )
    }

    @Test
    fun theRemotePairsAgainTheSameWayThroughItsConfirmedRepair() = runTest {
        val host = entered()
        val viewModel =
            RemoteViewModel(livingRoom, host, TvProfiles(FakeTvProfileDao()) { 1L }, preferenceStore())
        val session = tvs.sessionFor(livingRoom)!!
        session.publish(SessionState.NeedsRepair)
        settle()

        viewModel.onConfirmRepair()
        settle()

        assertEquals(listOf(livingRoom), tvs.forgottenIds)
        assertEquals(0, session.confirmRepairs)
        assertTrue(tvs.sessionFor(livingRoom) !== session)
    }

    @Test
    fun pairAgainDoesNothingOutsideTheRepairStates() = runTest {
        val host = entered()
        val viewModel = PairingViewModel(livingRoom, host, TvProfiles(FakeTvProfileDao()) { 1L })
        val session = tvs.sessionFor(livingRoom)!!
        session.ready()
        settle()

        viewModel.onPairAgain()
        settle()

        assertEquals("a healthy session is not dropped", 0, session.confirmRepairs)
        assertEquals("and nothing was forgotten", emptyList<TvId>(), tvs.forgottenIds)
        assertTrue(tvs.sessionFor(livingRoom) === session)
    }

    private suspend fun TestScope.entered(): ActiveRemoteHost {
        val host = ActiveRemoteHost(tvs, backgroundScope)
        host.enter(livingRoom)
        settle()
        return host
    }

    /** The same lazily-created DataStore the Remote tests use; this test never writes a flag. */
    private fun preferenceStore(): PreferenceStore {
        val file = File(folder.newFolder(), "saved-pairing-test.preferences_pb")
        return PreferenceStore(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { file },
            ),
        )
    }
}
