package dev.anthracite.appt.flow

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anthracite.appt.AppSettingsLauncher
import dev.anthracite.appt.data.FakeTvProfileDao
import dev.anthracite.appt.data.NameSource
import dev.anthracite.appt.data.TvProfiles
import dev.anthracite.appt.discovery.DiscoveryTestTags
import dev.anthracite.appt.localnetwork.LocalNetworkTestTags
import dev.anthracite.appt.navigation.AppTNavGraph
import dev.anthracite.appt.pairing.PairingTestTags
import dev.anthracite.appt.preferences.PreferenceStore
import dev.anthracite.appt.remote.ActiveRemoteHost
import dev.anthracite.appt.remote.RemoteTestTags
import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvId
import dev.anthracite.appt.testing.FakePermissionGate
import dev.anthracite.appt.testing.FakeSamsungTvs
import dev.anthracite.appt.tokens.AppTTheme
import dev.anthracite.appt.welcome.WelcomeTestTags
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The S03 acceptance flow (Issue #79, flows.md#first-run-to-first-control): choose a card, approve
 * on the television, and the first command is written.
 *
 * It runs through the production navigation graph and the fake `SamsungTvs`, so it proves the whole
 * path rather than one ViewModel: the profile row is written, the session is opened exactly once,
 * Pairing observes that same session, and an accepted command sets `firstControlAchieved`.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PairingToFirstControlFlowTest {
    @get:Rule val composeRule = createComposeRule()

    @get:Rule val folder = TemporaryFolder()

    private val tvs = FakeSamsungTvs()
    private val gate = FakePermissionGate()
    private val dao = FakeTvProfileDao()
    private val profiles = TvProfiles(dao) { 1L }
    private val store =
        PreferenceStore(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { File(folder.newFolder(), "preferences") },
            )
        )
    // Wired exactly as AppTApplication wires it: the host notices the transition to `Ready` and the
    // profile row records when the television was last opened.
    private val host =
        ActiveRemoteHost(
            samsungTvs = tvs,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            onSessionReady = { tvId -> profiles.markOpened(tvId) },
        )
    private val livingRoom = TvId(FakeSamsungTvs.LIVING_ROOM_ID)

    private fun setGraph() {
        composeRule.setContent {
            val navController = rememberNavController()
            AppTTheme {
                AppTNavGraph(
                    samsungTvs = tvs,
                    permissionGate = gate,
                    appSettings = AppSettingsLauncher {},
                    activeRemoteHost = host,
                    tvProfiles = profiles,
                    preferenceStore = store,
                    navController = navController,
                )
            }
        }
    }

    private fun openDiscovery() {
        composeRule.onNodeWithTag(WelcomeTestTags.PRIMARY_ACTION).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LocalNetworkTestTags.CONTINUE).performClick()
        composeRule.waitForIdle()
    }

    private fun firstControlAchieved(): Boolean = runBlocking {
        withTimeout(10.seconds) { store.firstControlAchieved.first { it } }
    }

    @Test
    @Config(qualifiers = "w360dp-h1400dp")
    fun cardToPairingToReadyToRemoteToFirstAcceptedCommand() {
        setGraph()
        openDiscovery()
        tvs.latest.send(FakeSamsungTvs.found())
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Living Room TV").performClick()
        composeRule.waitForIdle()
        composeRule.waitForIdle()

        // The card selection wrote exactly one device-local profile row, named after the
        // television.
        val row = dao.current().single()
        assertEquals(livingRoom.value, row.tvId)
        assertEquals("Living Room TV", row.friendlyName)
        assertEquals(NameSource.TV, row.nameSource)
        assertNull("a newly remembered television has never been opened", row.lastOpenedAt)

        // Pairing is showing, and it is asking the user to allow AppT on the television.
        composeRule.onNodeWithTag(PairingTestTags.TITLE).assertExists()
        composeRule.onNodeWithTag(PairingTestTags.WAITING).assertExists()

        // Exactly one session: the television is opened once, and never a second time.
        assertEquals(listOf(livingRoom), tvs.openedIds)
        val session = tvs.sessionFor(livingRoom)!!
        session.nextResult = CommandResult.Accepted
        session.ready(setOf(RemoteKey.VolumeUp, RemoteKey.VolumeDown))
        composeRule.waitForIdle()

        // The same session handed off to Remote, which shows the volume control it was told about.
        composeRule.onNodeWithTag(RemoteTestTags.CONTROLS).assertExists()
        composeRule.onNodeWithTag(RemoteTestTags.key(RemoteKey.VolumeUp)).assertExists()
        assertEquals(
            "reaching Ready records when the television was last opened",
            1L,
            dao.current().single().lastOpenedAt,
        )

        composeRule.onNodeWithTag(RemoteTestTags.key(RemoteKey.VolumeUp)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(TvCommand.Tap(RemoteKey.VolumeUp)), session.commands)
        assertTrue("the first accepted command is recorded", firstControlAchieved())
        assertEquals("still one session after the command", listOf(livingRoom), tvs.openedIds)
        assertFalse("the session was not closed by the handoff", session.closed)
    }

    @Test
    fun anUnsupportedCardOpensNoSessionAndWritesNoRow() {
        setGraph()
        openDiscovery()
        tvs.latest.send(
            FakeSamsungTvs.found(
                id = FakeSamsungTvs.OLDER_ID,
                name = "Older TV",
                availability = dev.anthracite.appt.samsung.ControlAvailability.Unsupported,
            )
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Older TV").performClick()
        composeRule.waitForIdle()

        assertTrue("no profile row for an Unsupported television", dao.current().isEmpty())
        assertEquals("no session for an Unsupported television", emptyList<TvId>(), tvs.openedIds)
        composeRule.onNodeWithTag(DiscoveryTestTags.TITLE).assertExists()
    }

    @Test
    fun cancellingPairingClosesTheSessionAndReturnsToDiscovery() {
        setGraph()
        openDiscovery()
        tvs.latest.send(FakeSamsungTvs.found())
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Living Room TV").performClick()
        composeRule.waitForIdle()
        composeRule.waitForIdle()
        val session = tvs.sessionFor(livingRoom)!!
        session.publish(SessionState.AwaitingTvApproval)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PairingTestTags.CANCEL).performClick()
        composeRule.waitForIdle()

        assertTrue("the session is released", session.closed)
        assertFalse("no session is retained after a cancel", host.current.value != null)
        composeRule.onNodeWithTag(DiscoveryTestTags.TITLE).assertExists()
    }
}
