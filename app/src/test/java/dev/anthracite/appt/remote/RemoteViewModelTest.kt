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
import dev.anthracite.appt.testing.settle
import dev.anthracite.appt.testing.subscribeTo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Remote observes the host's session, sends typed commands on it, and is the only place
 * `firstControlAchieved` is written (presentation.md#remote, data.md, account-entitlement.md#remote-entry-gate).
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
        val host = ActiveRemoteHost(tvs, backgroundScope)
        host.enter(livingRoom)
        settle()
        return host
    }

    // `setFirstControlAchieved` is a suspending write: the ViewModel hands it to DataStore, which
    // performs it on its own write actor and resumes the caller once it has landed. `settle` only
    // drains virtual time, so the write is still in flight when it returns and a single read can
    // not observe the flag. Collecting until the flag is true suspends until the write lands, which
    // is how PreferenceStoreTest reads the same key.
    private suspend fun assertFirstControlAchieved(message: String) {
        assertTrue(message, store.firstControlAchieved.first { it })
    }

    @Test
    fun readyExposesOnlyTheKeysTheTelevisionAccepts() =
        runTest(mainRule.dispatcher) {
            val viewModel = RemoteViewModel(livingRoom, entered(), profiles, store)
            subscribeTo(viewModel.state)
            settle()
            tvs.sessionFor(livingRoom)!!.ready(setOf(RemoteKey.VolumeUp, RemoteKey.VolumeDown))
            settle()

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
            subscribeTo(viewModel.state)
            settle()
            tvs.sessionFor(livingRoom)!!.publish(SessionState.AwaitingTvApproval)
            settle()

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
            subscribeTo(viewModel.state)
            settle()

            assertEquals("Living Room TV", viewModel.state.value.tvName)
        }

    @Test
    fun anAcceptedCommandSetsFirstControlAchievedOnce() =
        runTest(mainRule.dispatcher) {
            val viewModel = RemoteViewModel(livingRoom, entered(), profiles, store)
            subscribeTo(viewModel.state)
            settle()
            val session = tvs.sessionFor(livingRoom)!!
            session.ready()
            session.nextResult = CommandResult.Accepted
            settle()
            assertFalse("no command has been accepted yet", store.firstControlAchieved.first())

            viewModel.onCommand(TvCommand.Tap(RemoteKey.VolumeUp))
            settle()
            assertFirstControlAchieved("the accepted command is recorded")

            viewModel.onCommand(TvCommand.Tap(RemoteKey.VolumeDown))
            settle()
            assertFirstControlAchieved("idempotent")
            assertEquals(
                listOf(TvCommand.Tap(RemoteKey.VolumeUp), TvCommand.Tap(RemoteKey.VolumeDown)),
                session.commands,
            )
        }

    @Test
    fun aRejectedCommandNeverSetsFirstControlAchieved() =
        runTest(mainRule.dispatcher) {
            val viewModel = RemoteViewModel(livingRoom, entered(), profiles, store)
            subscribeTo(viewModel.state)
            settle()
            val session = tvs.sessionFor(livingRoom)!!
            session.ready()
            session.nextResult = CommandResult.Rejected(TvFailure.Unavailable)
            settle()

            viewModel.onCommand(TvCommand.Tap(RemoteKey.VolumeUp))
            settle()

            assertFalse(store.firstControlAchieved.first())
        }

    @Test
    fun aReadySessionNeverSetsFirstControlAchieved() =
        runTest(mainRule.dispatcher) {
            val viewModel = RemoteViewModel(livingRoom, entered(), profiles, store)
            subscribeTo(viewModel.state)
            settle()
            tvs.sessionFor(livingRoom)!!.ready()
            settle()

            assertFalse("reaching Ready is not a command", store.firstControlAchieved.first())
        }

    @Test
    fun aCommandBeforeTheSessionIsReadyWritesNothing() =
        runTest(mainRule.dispatcher) {
            val viewModel = RemoteViewModel(livingRoom, entered(), profiles, store)
            subscribeTo(viewModel.state)
            settle()
            val session = tvs.sessionFor(livingRoom)!!
            session.nextResult = CommandResult.Accepted
            settle()

            viewModel.onCommand(TvCommand.Tap(RemoteKey.VolumeUp))
            settle()

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
            subscribeTo(viewModel.state)
            settle()
            val dead = tvs.sessionFor(livingRoom)!!
            dead.publish(SessionState.Unreachable)
            settle()

            viewModel.onRetry()
            settle()

            assertEquals(ConnectionUi.Connecting, viewModel.state.value.connection)
            assertTrue("the dead session is released", dead.closed)
            assertEquals(
                "one television, opened again",
                listOf(livingRoom, livingRoom),
                tvs.openedIds,
            )
        }
}
