package dev.anthracite.appt.localnetwork

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anthracite.appt.gate.LocalNetworkPhase
import dev.anthracite.appt.testing.assertEveryClickableMeetsTheTouchTargetFloor
import dev.anthracite.appt.testing.assertEveryControlIsDescribedForTalkBack
import dev.anthracite.appt.testing.assertNoTechnicalIdentifierIsExposed
import dev.anthracite.appt.testing.clickableNodes
import dev.anthracite.appt.tokens.AppTTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The local-network explanation (presentation.md#localnetwork, discovery.md#permission-gate). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LocalNetworkScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private var continues = 0
    private var retries = 0
    private var settings = 0

    private fun setScreen(phase: LocalNetworkPhase) {
        composeRule.setContent {
            AppTTheme {
                LocalNetworkScreen(
                    state = LocalNetworkUiState.of(phase),
                    onContinue = { continues++ },
                    onRetry = { retries++ },
                    onOpenSettings = { settings++ },
                )
            }
        }
    }

    @Test
    fun explainsThenOffersExactlyOneContinue() {
        setScreen(LocalNetworkPhase.Explain)
        composeRule.onNodeWithTag(LocalNetworkTestTags.TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(LocalNetworkTestTags.EXPLANATION).assertIsDisplayed()
        assertEquals(1, composeRule.clickableNodes().size)
        composeRule.onNodeWithTag(LocalNetworkTestTags.CONTINUE).performClick()
        assertEquals(1, continues)
        composeRule.assertEveryClickableMeetsTheTouchTargetFloor()
        composeRule.assertEveryControlIsDescribedForTalkBack()
        composeRule.assertNoTechnicalIdentifierIsExposed()
    }

    @Test
    fun deniedOffersRetryAndAppSettings() {
        setScreen(LocalNetworkPhase.Denied)
        composeRule.onNodeWithTag(LocalNetworkTestTags.DENIED_MESSAGE).assertIsDisplayed()
        composeRule.onNodeWithTag(LocalNetworkTestTags.RETRY).performClick()
        composeRule.onNodeWithTag(LocalNetworkTestTags.OPEN_SETTINGS).performClick()
        assertEquals(1, retries)
        assertEquals(1, settings)
        assertEquals("Continue is not offered once denied", 0, continues)
        composeRule.assertEveryClickableMeetsTheTouchTargetFloor()
        composeRule.assertEveryControlIsDescribedForTalkBack()
    }

    @Test
    fun grantedShowsNoActionWhileAdvancing() {
        setScreen(LocalNetworkPhase.Granted)
        assertEquals(0, composeRule.clickableNodes().size)
    }
}
