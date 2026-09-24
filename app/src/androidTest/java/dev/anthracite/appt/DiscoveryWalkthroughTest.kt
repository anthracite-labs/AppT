package dev.anthracite.appt

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dev.anthracite.appt.discovery.DiscoveryTestTags
import dev.anthracite.appt.gate.LocalNetworkPermissionGate
import dev.anthracite.appt.localnetwork.LocalNetworkTestTags
import dev.anthracite.appt.welcome.WelcomeTestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #73's walkthrough on a real device/emulator with the installed debug APK: Welcome → the
 * local-network explanation → Continue → Discovery, which scans with the real `:samsung` adapter
 * and real sockets, and reaches a terminal state within its bound. An emulator has no Samsung
 * television, so the scan ends in the empty state (or, on an emulator with no Wi-Fi/Ethernet
 * network, the ordinary-language failure panel); both offer Scan again.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class DiscoveryWalkthroughTest {

    @get:Rule val composeRule = createEmptyComposeRule()

    @Before
    fun explainAgain() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(LocalNetworkPermissionGate.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun findMyTvExplainsThenScansAndEndsWithinTheBound() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithTag(WelcomeTestTags.PRIMARY_ACTION).performClick()
            composeRule.onNodeWithTag(LocalNetworkTestTags.EXPLANATION).assertIsDisplayed()

            composeRule.onNodeWithTag(LocalNetworkTestTags.CONTINUE).performClick()
            composeRule.onNodeWithTag(DiscoveryTestTags.TITLE).assertIsDisplayed()

            // discover() is bounded at 10 s; allow scheduling slack on a slow emulator.
            composeRule.waitUntil(timeoutMillis = SCAN_BOUND_WITH_SLACK_MILLIS) {
                composeRule
                    .onAllNodes(hasTestTag(DiscoveryTestTags.RESCAN))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule.onNodeWithTag(DiscoveryTestTags.RESCAN).assertIsDisplayed()
        }
    }

    private companion object {
        const val SCAN_BOUND_WITH_SLACK_MILLIS = 20_000L
    }
}
