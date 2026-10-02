package dev.anthracite.appt.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import dev.anthracite.appt.samsung.RepairReason
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvId
import dev.anthracite.appt.testing.FakePermissionGate
import dev.anthracite.appt.testing.FakeSamsungTvs
import dev.anthracite.appt.testing.PREFERENCES_FILE_NAME
import dev.anthracite.appt.tokens.AppTTheme
import dev.anthracite.appt.welcome.WelcomeTestTags
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.junit.After
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
    /** Counts every entry decision, so recreation can prove the gate is not re-evaluated. */
    private val entryDecisions = AtomicInteger()

    private val host =
        ActiveRemoteHost(
            samsungTvs = tvs,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            entryAllowed = {
                entryDecisions.incrementAndGet()
                true
            },
            onSessionReady = { tvId -> profiles.markOpened(tvId) },
        )
    private val livingRoom = TvId(FakeSamsungTvs.LIVING_ROOM_ID)

    /** Replaces the Activity as the graph's lifecycle owner, to drive ON_STOP/ON_START. */
    private class HostLifecycle : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle
            get() = registry
    }

    private val observations = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val firstControlObserverStarted = AtomicBoolean(false)
    private val firstControlAchieved = AtomicBoolean(false)

    /**
     * The generation a fresh Activity composition is built on. Bumping it disposes the whole graph
     * composition and builds a new one over the same application-scoped objects, which is what a
     * recreated Activity does to the graph; the `NavHostController` remembered outside the key
     * keeps the route, which is what the platform's own saved instance state restores.
     *
     * The genuinely platform-driven recreation (a real Activity destroy/create on rotation) is
     * recorded by the stock-debug Engineering Verifier on the exact-head APK; this key is the
     * deterministic JVM model of the same event.
     */
    private val recreationGeneration = mutableIntStateOf(0)

    /**
     * @param lifecycleOwner replaces the Activity as the graph's owner, to drive ON_STOP/ON_START.
     */
    private fun setGraph(lifecycleOwner: LifecycleOwner? = null) {
        composeRule.setContent {
            val controller = rememberNavController()
            key(recreationGeneration.intValue) {
                val graph: @Composable () -> Unit = {
                    AppTTheme {
                        AppTNavGraph(
                            samsungTvs = tvs,
                            permissionGate = gate,
                            appSettings = AppSettingsLauncher {},
                            activeRemoteHost = host,
                            tvProfiles = profiles,
                            preferenceStore = store,
                            navController = controller,
                        )
                    }
                }
                if (lifecycleOwner == null) graph()
                else {
                    CompositionLocalProvider(
                        LocalLifecycleOwner provides lifecycleOwner,
                        content = graph,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** The touchpad is offered once the persisted navigation mode reaches the screen. */
    private fun touchpadShown(): Boolean =
        composeRule.onAllNodesWithTag(RemoteTestTags.TOUCHPAD).fetchSemanticsNodes().isNotEmpty()

    /** One configuration change: the Activity's composition is destroyed and built again. */
    private fun recreateActivity() {
        composeRule.runOnUiThread { recreationGeneration.intValue++ }
        composeRule.waitForIdle()
    }

    private fun openDiscovery() {
        composeRule.onNodeWithTag(WelcomeTestTags.PRIMARY_ACTION).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LocalNetworkTestTags.CONTINUE).performClick()
        composeRule.waitForIdle()
    }

    @After
    fun stopObservations() {
        observations.cancel()
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

        val row = dao.current().single()
        assertEquals(livingRoom.value, row.tvId)
        assertEquals("Living Room TV", row.friendlyName)
        assertEquals(NameSource.TV, row.nameSource)
        assertNull("a newly remembered television has never been opened", row.lastOpenedAt)

        assertEquals(listOf(livingRoom), tvs.openedIds)
        val session = tvs.sessionFor(livingRoom)!!
        session.publish(SessionState.AwaitingTvApproval)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PairingTestTags.TITLE).assertExists()
        composeRule.onNodeWithTag(PairingTestTags.WAITING).assertExists()

        session.nextResult = CommandResult.Accepted
        session.ready(setOf(RemoteKey.VolumeUp, RemoteKey.VolumeDown))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(RemoteTestTags.CONTROLS).assertExists()
        composeRule.onNodeWithTag(RemoteTestTags.key(RemoteKey.VolumeUp)).assertExists()
        assertEquals(
            "reaching Ready records when the television was last opened",
            1L,
            dao.current().single().lastOpenedAt,
        )

        val firstControlObserver =
            observations.launch {
                store.firstControlAchieved
                    .onEach { achieved -> if (!achieved) firstControlObserverStarted.set(true) }
                    .first { it }
                firstControlAchieved.set(true)
            }
        try {
            composeRule.waitUntil(
                conditionDescription = "first-control observer received its initial value",
                timeoutMillis = 10_000L,
            ) {
                firstControlObserverStarted.get()
            }

            composeRule.onNodeWithTag(RemoteTestTags.key(RemoteKey.VolumeUp)).performClick()
            composeRule.waitForIdle()
            composeRule.waitUntil(
                conditionDescription = "first accepted command persisted",
                timeoutMillis = 10_000L,
            ) {
                firstControlAchieved.get()
            }
        } finally {
            firstControlObserver.cancel()
        }

        assertEquals(listOf(TvCommand.Tap(RemoteKey.VolumeUp)), session.commands)
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
    fun backgroundReleasesRemote() {
        val lifecycleOwner = HostLifecycle()
        setGraph(lifecycleOwner)
        openDiscovery()
        tvs.latest.send(FakeSamsungTvs.found())
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Living Room TV").performClick()
        composeRule.waitForIdle()
        composeRule.waitForIdle()
        val session = tvs.sessionFor(livingRoom)!!
        session.ready(setOf(RemoteKey.VolumeUp, RemoteKey.VolumeDown))
        composeRule.waitForIdle()
        assertEquals(1, host.ownerCount)

        // onStop is app backgrounding: the Remote retain is released into the grace window.
        composeRule.runOnUiThread { lifecycleOwner.registry.currentState = Lifecycle.State.CREATED }
        composeRule.waitForIdle()
        assertEquals("onStop released the Remote retain", 0, host.ownerCount)
        assertFalse("grace keeps the socket for the return", session.closed)

        // Returning inside grace re-retains the same session: no second open, no gap.
        composeRule.runOnUiThread { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.waitForIdle()
        assertEquals(1, host.ownerCount)
        assertFalse(session.closed)
        assertEquals(listOf(livingRoom), tvs.openedIds)
        composeRule.onNodeWithTag(RemoteTestTags.CONTROLS).assertExists()
    }

    @Test
    fun rotationKeepsSession() {
        setGraph()
        openDiscovery()
        tvs.latest.send(FakeSamsungTvs.found())
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Living Room TV").performClick()
        composeRule.waitForIdle()
        composeRule.waitForIdle()
        val session = tvs.sessionFor(livingRoom)!!
        session.ready(setOf(RemoteKey.VolumeUp, RemoteKey.VolumeDown))
        composeRule.waitForIdle()
        val savedEntryDecisions = entryDecisions.get()
        val scansBefore = tvs.discoverCalls
        // The one acknowledgement is the user's Continue on the local-network explanation.
        assertEquals("the user acknowledged the gate exactly once", 1, gate.acknowledgeCalls)
        val acknowledgementsBefore = gate.acknowledgeCalls
        assertEquals(1, host.ownerCount)

        // A configuration change destroys and recreates the Activity: the composition is built
        // again from scratch over the same application-scoped host, and the screen re-attaches to
        // the session it already owns.
        recreateActivity()

        assertEquals("no second open across recreation", listOf(livingRoom), tvs.openedIds)
        assertFalse("the retained session survives recreation", session.closed)
        assertEquals(
            "recreation does not re-evaluate the entry decision",
            savedEntryDecisions,
            entryDecisions.get(),
        )
        assertEquals("recreation does not rescan", scansBefore, tvs.discoverCalls)
        assertEquals("no gate decision was made", 0, gate.deniedCalls)
        assertEquals(
            "recreation adds no gate acknowledgement to the user's",
            acknowledgementsBefore,
            gate.acknowledgeCalls,
        )
        assertEquals("the recreated screen re-retains the same session", 1, host.ownerCount)
        assertEquals(
            "the host still holds the same television",
            livingRoom,
            host.current.value?.tvId,
        )
        assertEquals(
            "and it is still the session the first Activity opened",
            session,
            host.current.value?.session,
        )
    }

    /**
     * docs/architecture/lifecycle.md: the route and its `tvId` survive recreation, and the screen
     * that comes back is the same one for the same session, behind its own explicit confirmation.
     *
     * The TvList scroll and rename-draft halves of the contract arrive with those surfaces in S09;
     * the genuinely platform-driven recreation is recorded by the Engineering Verifier.
     */
    @Test
    fun recreationRestoresRouteAndDrafts() {
        setGraph()
        openDiscovery()
        tvs.latest.send(FakeSamsungTvs.found())
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Living Room TV").performClick()
        composeRule.waitForIdle()
        composeRule.waitForIdle()
        val session = tvs.sessionFor(livingRoom)!!
        session.ready(setOf(RemoteKey.VolumeUp, RemoteKey.VolumeDown))
        composeRule.waitForIdle()
        // The saved identity was rejected on a later open: Remote shows the re-pair surface.
        session.publish(SessionState.NeedsRepair, repairReason = RepairReason.TokenRejected)
        composeRule.waitForIdle()

        // The route's television is named from the persisted profile, not from composition state.
        composeRule.onNodeWithTag(RemoteTestTags.TV_NAME).assertTextEquals("Living Room TV")

        recreateActivity()

        assertEquals(
            "the route and its tvId survive recreation",
            livingRoom,
            host.current.value?.tvId,
        )
        assertEquals("and it is still the same session", session, host.current.value?.session)
        assertEquals("recreation does not re-open the television", 1, tvs.openedIds.size)
        assertFalse("the session is untouched", session.closed)
        composeRule.onNodeWithTag(RemoteTestTags.TV_NAME).assertTextEquals("Living Room TV")
        // The saved-identity recovery surface is still the one for this session, behind its own
        // explicit confirmation.
        composeRule.onNodeWithTag(RemoteTestTags.RECOVERY).assertExists()
        composeRule.onNodeWithText("Re-pair").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RemoteTestTags.REPAIR_CONFIRM).assertExists()
    }

    /**
     * docs/architecture/lifecycle.md: transient UI state resets on recreation while nothing else
     * does. The touchpad's in-flight drag is the transient surface that exists at S06; S09's sheet
     * and S11's edit mode extend this test when those surfaces land.
     */
    @Test
    @Config(qualifiers = "w360dp-h1400dp")
    fun recreationDropsTransientUiOnly() {
        setGraph()
        openDiscovery()
        tvs.latest.send(FakeSamsungTvs.found())
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Living Room TV").performClick()
        composeRule.waitForIdle()
        composeRule.waitForIdle()
        val session = tvs.sessionFor(livingRoom)!!
        session.ready(setOf(RemoteKey.VolumeUp, RemoteKey.VolumeDown), pointer = true)
        composeRule.waitForIdle()
        val savedEntryDecisions = entryDecisions.get()

        composeRule.onNodeWithTag(RemoteTestTags.NAVIGATION_MODE).performClick()
        composeRule.waitUntil(
            conditionDescription = "the touchpad is offered",
            timeoutMillis = 10_000L,
        ) {
            touchpadShown()
        }

        composeRule.onNodeWithTag(RemoteTestTags.TOUCHPAD).performTouchInput {
            down(center)
            moveBy(Offset(60f, 0f))
            moveBy(Offset(60f, 0f))
            up()
        }
        composeRule.waitForIdle()
        val pointerMoves = session.commands.filterIsInstance<TvCommand.PointerMove>()
        assertTrue("an in-flight drag writes pointer moves", pointerMoves.isNotEmpty())
        val commandsBefore = session.commands.toList()

        // The configuration change drops the gesture with the composition that held it.
        recreateActivity()

        assertEquals("nothing is replayed across recreation", commandsBefore, session.commands)
        assertFalse("the session survives", session.closed)
        assertEquals("no second open across recreation", listOf(livingRoom), tvs.openedIds)
        assertEquals(
            "recreation does not re-evaluate the entry decision",
            savedEntryDecisions,
            entryDecisions.get(),
        )
        composeRule.onNodeWithTag(RemoteTestTags.TOUCHPAD).assertExists()

        // The rebuilt pad carries no gesture state: a fresh drag is a fresh sequence.
        composeRule.onNodeWithTag(RemoteTestTags.TOUCHPAD).performTouchInput {
            down(center)
            moveBy(Offset(0f, 60f))
            moveBy(Offset(0f, 60f))
            up()
        }
        composeRule.waitForIdle()
        assertTrue(
            "the touchpad still works after recreation",
            session.commands.size > commandsBefore.size,
        )
    }

    /** docs/architecture/lifecycle.md: socket loss is a quiet inline status, never a modal loop. */
    @Test
    fun networkLossShowsReconnectingNotDialog() {
        setGraph()
        openDiscovery()
        tvs.latest.send(FakeSamsungTvs.found())
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Living Room TV").performClick()
        composeRule.waitForIdle()
        composeRule.waitForIdle()
        val session = tvs.sessionFor(livingRoom)!!
        session.ready(setOf(RemoteKey.VolumeUp, RemoteKey.VolumeDown))
        composeRule.waitForIdle()

        session.publish(SessionState.Reconnecting)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(RemoteTestTags.STATUS).assertTextEquals("Reconnecting")
        // A quiet inline status, not a modal: neither recovery surface may appear.
        composeRule.onNodeWithTag(RemoteTestTags.REPAIR_CONFIRM).assertDoesNotExist()
        composeRule.onNodeWithTag(RemoteTestTags.RETRY).assertDoesNotExist()
    }

    /** Source with KDoc and line comments removed, so the check reads code rather than prose. */
    private fun codeOnly(source: String): String =
        source
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//[^\n]*"), "")

    @Test
    fun `recreation is left to the platform`() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("expected to run from the app module", manifest.isFile)
        val declared = manifest.readText()
        assertFalse(
            "the app must not opt out of Activity recreation (lifecycle.md)",
            declared.contains("configChanges"),
        )
        val activity =
            codeOnly(File("src/main/java/dev/anthracite/appt/MainActivity.kt").readText())
        assertFalse(
            "the Activity must not own the session",
            activity.contains("ActiveRemoteHost") || activity.contains("RemoteSession"),
        )
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
