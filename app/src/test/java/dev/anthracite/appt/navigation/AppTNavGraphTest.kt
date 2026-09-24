package dev.anthracite.appt.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anthracite.appt.AppSettingsLauncher
import dev.anthracite.appt.discovery.DiscoveryTestTags
import dev.anthracite.appt.gate.LocalNetworkPhase
import dev.anthracite.appt.localnetwork.LocalNetworkTestTags
import dev.anthracite.appt.samsung.DiscoveryEvent
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.testing.FakePermissionGate
import dev.anthracite.appt.testing.FakeSamsungTvs
import dev.anthracite.appt.tokens.AppTTheme
import dev.anthracite.appt.welcome.WelcomeTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The route graph and the S02 flow (Issue #73): Welcome → LocalNetwork → Discovery, with a denied
 * local network returning to the explanation and no retry loop.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppTNavGraphTest {

    @get:Rule val composeRule = createComposeRule()

    private val tvs = FakeSamsungTvs()
    private val gate = FakePermissionGate()
    private lateinit var navController: NavHostController

    /**
     * @param host replaces the activity as the graph's lifecycle owner, to drive ON_STOP/ON_START.
     */
    private fun setGraph(host: LifecycleOwner? = null) {
        composeRule.setContent {
            val graph: @Composable () -> Unit = {
                navController = rememberNavController()
                AppTTheme {
                    AppTNavGraph(
                        samsungTvs = tvs,
                        permissionGate = gate,
                        appSettings = AppSettingsLauncher {},
                        navController = navController,
                    )
                }
            }
            if (host == null) graph()
            else CompositionLocalProvider(LocalLifecycleOwner provides host, content = graph)
        }
        composeRule.waitForIdle()
    }

    private fun openDiscovery() {
        composeRule.onNodeWithTag(WelcomeTestTags.PRIMARY_ACTION).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LocalNetworkTestTags.CONTINUE).performClick()
        composeRule.waitForIdle()
    }

    private class HostLifecycle : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle
            get() = registry
    }

    private fun backStackRoutes(): List<String> =
        navController.currentBackStack.value.mapNotNull { entry ->
            val destination = entry.destination
            when {
                destination.hasRoute<WelcomeRoute>() -> "Welcome"
                destination.hasRoute<LocalNetworkRoute>() -> "LocalNetwork"
                destination.hasRoute<DiscoveryRoute>() -> "Discovery"
                else -> null
            }
        }

    @Test
    fun `the graph holds Welcome, LocalNetwork and Discovery and opens on Welcome`() {
        setGraph()
        val destinations = navController.graph.iterator().asSequence().toList()
        assertEquals(3, destinations.size)
        assertTrue(destinations.any { it.hasRoute<WelcomeRoute>() })
        assertTrue(destinations.any { it.hasRoute<LocalNetworkRoute>() })
        assertTrue(destinations.any { it.hasRoute<DiscoveryRoute>() })
        assertTrue(navController.graph.findStartDestination().hasRoute<WelcomeRoute>())
        composeRule.onNodeWithTag(WelcomeTestTags.VALUE_PROPOSITION).assertExists()
        composeRule.onNodeWithTag(WelcomeTestTags.PRIMARY_ACTION).assertExists()
    }

    @Test
    fun `Find my TV explains first and Continue scans immediately`() {
        setGraph()
        composeRule.onNodeWithTag(WelcomeTestTags.PRIMARY_ACTION).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LocalNetworkTestTags.EXPLANATION).assertExists()
        assertEquals("nothing touches the network before Continue", 0, tvs.discoverCalls)

        composeRule.onNodeWithTag(LocalNetworkTestTags.CONTINUE).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DiscoveryTestTags.TITLE).assertExists()
        assertEquals("Discovery starts one scan with no separate setup step", 1, tvs.discoverCalls)
        assertEquals(
            "Back from Discovery returns to Welcome",
            listOf("Welcome", "Discovery"),
            backStackRoutes(),
        )
    }

    @Test
    fun `a denied local network returns to the explanation without looping`() {
        setGraph()
        composeRule.onNodeWithTag(WelcomeTestTags.PRIMARY_ACTION).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LocalNetworkTestTags.CONTINUE).performClick()
        composeRule.waitForIdle()

        tvs.latest.send(DiscoveryEvent.Failed(TvFailure.LocalNetworkDenied))
        composeRule.waitForIdle()
        assertEquals(LocalNetworkPhase.Denied, gate.phase.value)
        composeRule.onNodeWithTag(LocalNetworkTestTags.DENIED_MESSAGE).assertExists()
        composeRule.onNodeWithTag(LocalNetworkTestTags.RETRY).assertExists()
        assertEquals(listOf("Welcome", "LocalNetwork"), backStackRoutes())
        assertEquals("no automatic retry", 1, tvs.discoverCalls)

        composeRule.onNodeWithTag(LocalNetworkTestTags.RETRY).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DiscoveryTestTags.TITLE).assertExists()
        assertEquals("Try again starts one fresh scan", 2, tvs.discoverCalls)
    }

    @Test
    fun `Discovery scans only in the foreground and resumes with one fresh scan`() {
        val host = HostLifecycle()
        setGraph(host)
        openDiscovery()
        assertEquals(1, tvs.discoverCalls)

        composeRule.runOnUiThread { host.registry.currentState = Lifecycle.State.CREATED }
        composeRule.waitForIdle()
        assertTrue("leaving the foreground cancels the scan", tvs.latest.cancelled)
        assertEquals("nothing scans in the background", 1, tvs.discoverCalls)

        composeRule.runOnUiThread { host.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.waitForIdle()
        assertEquals("returning starts exactly one fresh scan", 2, tvs.discoverCalls)
        assertFalse(tvs.latest.cancelled)
    }

    @Test
    fun `returning after a finished scan does not scan again`() {
        val host = HostLifecycle()
        setGraph(host)
        openDiscovery()
        tvs.latest.send(DiscoveryEvent.Finished)
        composeRule.waitForIdle()

        composeRule.runOnUiThread { host.registry.currentState = Lifecycle.State.CREATED }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { host.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.waitForIdle()
        assertEquals(1, tvs.discoverCalls)
    }

    @Test
    fun `leaving Discovery cancels its scan`() {
        setGraph()
        composeRule.onNodeWithTag(WelcomeTestTags.PRIMARY_ACTION).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LocalNetworkTestTags.CONTINUE).performClick()
        composeRule.waitForIdle()

        composeRule.runOnUiThread { navController.popBackStack() }
        composeRule.waitForIdle()
        assertEquals(listOf("Welcome"), backStackRoutes())
        assertTrue(tvs.latest.cancelled)
    }
}
