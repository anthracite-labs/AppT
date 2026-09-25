package dev.anthracite.appt.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.anthracite.appt.data.FakeTvProfileDao
import dev.anthracite.appt.data.NameSource
import dev.anthracite.appt.data.TvProfile
import dev.anthracite.appt.data.TvProfiles
import dev.anthracite.appt.preferences.PreferenceStore
import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import dev.anthracite.appt.testing.FakeSamsungTvs
import dev.anthracite.appt.testing.MainDispatcherRule
import dev.anthracite.appt.testing.PREFERENCES_FILE_NAME
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Remote observes the host's session, sends typed commands on it, and is the only place
 * `firstControlAchieved` is written (presentation.md#remote, data.md, sync.md#remote-entry-gate).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    @get:Rule val folder = TemporaryFolder()

    private val tvs = FakeSamsungTvs()
    private val livingRoom = TvId(FakeSamsungTvs.LIVING_ROOM_ID)
    private val dao = FakeTvProfileDao()
    private val profiles = TvProfiles(dao) { 1L }
    /**
     * Lazy because `TemporaryFolder` only creates its root when the rule runs, which is after the
     * test instance is constructed. The path is resolved once inside it and then handed to every
     * DataStore call, because DataStore reads its file more than once.
     */
    private val store by lazy {
        val file = File(folder.newFolder(), PREFERENCES_FILE_NAME)
        PreferenceStore(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { file },
            )
        )
    }

    /** A host that has already entered the television, as the Pairing route did. */
    private suspend fun TestScope.entered(): ActiveRemoteHost {
        val host = ActiveRemoteHost(tvs, this)
        host.enter(livingRoom)
        advanceUntilIdle()
        return host
    }

    @Test
    fun readyExposesOnlyTheKeysTheTelevisionAccepts() =
        runTest(mainRule.dispatcher) {
            val viewModel = RemoteViewModel(livingRoom, entered(), profiles, store)
            advanceUntilIdle()
            tvs.sessionFor(livingRoom)!!.ready(setOf(RemoteKey.VolumeUp, RemoteKey.VolumeDown))
            advanceUntilIdle()

            assertEquals(
                listOf(RemoteKey.VolumeUp, RemoteKey.VolumeDown),
                viewModel.state.value.keys,
            )
            assertEquals(ConnectionUi.Ready, viewModel.state.value.connection)
        }

    @Test
    fun anUnreadySessionExposesNoKeys() =
        runTest(mainRule.dispatcher) {
            val viewModel = RemoteViewModel(livingRoom, entered(), profiles, store)
            advanceUntilIdle()
            tvs.sessionFor(livingRoom)!!.publish(SessionState.AwaitingTvApproval)
            advanceUntilIdle()

            assertEquals(emptyList<RemoteKey>(), viewModel.state.value.keys)
            assertEquals(ConnectionUi.WaitingForApproval, viewModel.state.value.connection)
        }

    @Test
    fun theTelevisionNameComesFromTheProfileRow() =
        runTest(mainRule.dispatcher) {
            dao.upsert(
                TvProfile(
                    tvId = livingRoom.value,
                    friendlyName = "Living Room TV",
                    nameSource = NameSource.TV,
                    stableIdentity = true,
                    createdAt = 1L,
                    lastOpenedAt = null,
                )
            )
            val viewModel = RemoteViewModel(livingRoom, entered(), profiles, store)
            advanceUntilIdle()

            assertEquals("Living Room TV", viewModel.state.value.tvName)
        }

    @Test
    fun anAcceptedCommandSetsFirstControlAchievedOnce() =
        runTest(mainRule.dispatcher) {
            val viewModel = RemoteViewModel(livingRoom, entered(), profiles, store)
            advanceUntilIdle()
            val session = tvs.sessionFor(livingRoom)!!
            session.ready()
            session.nextResult = CommandResult.Accepted
            advanceUntilIdle()
            assertFalse("no command has been accepted yet", store.firstControlAchieved.first())

            viewModel.onCommand(TvCommand.Tap(RemoteKey.VolumeUp))
            advanceUntilIdle()
            assertTrue(store.firstControlAchieved.first())

            viewModel.onCommand(TvCommand.Tap(RemoteKey.VolumeDown))
            advanceUntilIdle()
            assertTrue("idempotent", store.firstControlAchieved.first())
            assertEquals(
                listOf(TvCommand.Tap(RemoteKey.VolumeUp), TvCommand.Tap(RemoteKey.VolumeDown)),
                session.commands,
            )
        }

    @Test
    fun aRejectedCommandNeverSetsFirstControlAchieved() =
        runTest(mainRule.dispatcher) {
            val viewModel = RemoteViewModel(livingRoom, entered(), profiles, store)
            advanceUntilIdle()
            val session = tvs.sessionFor(livingRoom)!!
            session.ready()
            session.nextResult = CommandResult.Rejected(TvFailure.Unavailable)
            advanceUntilIdle()

            viewModel.onCommand(TvCommand.Tap(RemoteKey.VolumeUp))
            advanceUntilIdle()

            assertFalse(store.firstControlAchieved.first())
        }

    @Test
    fun aReadySessionNeverSetsFirstControlAchieved() =
        runTest(mainRule.dispatcher) {
            RemoteViewModel(livingRoom, entered(), profiles, store)
            advanceUntilIdle()
            tvs.sessionFor(livingRoom)!!.ready()
            advanceUntilIdle()

            assertFalse("reaching Ready is not a command", store.firstControlAchieved.first())
        }

    @Test
    fun aCommandBeforeTheSessionIsReadyWritesNothing() =
        runTest(mainRule.dispatcher) {
            val viewModel = RemoteViewModel(livingRoom, entered(), profiles, store)
            advanceUntilIdle()
            val session = tvs.sessionFor(livingRoom)!!
            session.nextResult = CommandResult.Accepted
            advanceUntilIdle()

            viewModel.onCommand(TvCommand.Tap(RemoteKey.VolumeUp))
            advanceUntilIdle()

            assertEquals(
                "the session rejects anything before Ready",
                listOf(TvCommand.Tap(RemoteKey.VolumeUp)),
                session.commands,
            )
            assertFalse(store.firstControlAchieved.first())
        }

    /**
     * A dead session is not reused. `Unreachable` is a session that already ended, so Try again has
     * to open a real socket rather than re-reading the one that failed (connection.md: `Unreachable
     * → Connecting: caller opens again`).
     */
    @Test
    fun retryAfterUnavailableOpensANewSession() =
        runTest(mainRule.dispatcher) {
            val viewModel = RemoteViewModel(livingRoom, entered(), profiles, store)
            advanceUntilIdle()
            val dead = tvs.sessionFor(livingRoom)!!
            dead.publish(SessionState.Unreachable)
            advanceUntilIdle()

            viewModel.onRetry()
            advanceUntilIdle()

            assertEquals(ConnectionUi.Connecting, viewModel.state.value.connection)
            assertTrue("the dead session is released", dead.closed)
            assertEquals(
                "one television, opened again",
                listOf(livingRoom, livingRoom),
                tvs.openedIds,
            )
        }
}
