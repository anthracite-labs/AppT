package dev.anthracite.appt.pairing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.testing.assertEveryClickableMeetsTheTouchTargetFloor
import dev.anthracite.appt.testing.assertEveryControlIsDescribedForTalkBack
import dev.anthracite.appt.testing.assertNoTechnicalIdentifierIsExposed
import dev.anthracite.appt.tokens.AppTTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Pairing's product shape and accessibility contracts (presentation.md#pairing). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PairingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private var cancels = 0
    private var retries = 0

    private fun setPairing(state: PairingUiState) {
        composeRule.setContent {
            AppTTheme {
                PairingScreen(
                    state = state,
                    onCancel = { cancels++ },
                    onRetryApproval = { retries++ },
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h1200dp")
    fun waitingDirectsTheUserToTheTelevisionInOrdinaryLanguage() {
        setPairing(
            PairingUiState(
                tvName = "Living Room TV",
                phase = PairingPhase.WaitingForApproval,
                recallHintVisible = true,
            )
        )
        composeRule.onNodeWithTag(PairingTestTags.TITLE).assertIsDisplayed()
        composeRule
            .onNodeWithTag(PairingTestTags.INSTRUCTION)
            .assertTextEquals(
                "Approve AppT on Living Room TV. Look for the message on your television and choose Allow."
            )
        composeRule.onNodeWithTag(PairingTestTags.RECALL_HINT).assertIsDisplayed()
        composeRule.assertNoTechnicalIdentifierIsExposed("8002", "wss", "KEY_VOLUP")
    }

    @Test
    fun anUnnamedTelevisionUsesTheGenericLabel() {
        setPairing(
            PairingUiState(
                tvName = "",
                phase = PairingPhase.WaitingForApproval,
                recallHintVisible = true,
            )
        )
        composeRule
            .onNodeWithTag(PairingTestTags.INSTRUCTION)
            .assertTextEquals(
                "Approve AppT on your television. Look for the message on your television and choose Allow."
            )
    }

    @Test
    fun connectingIsNotAnErrorAndOffersCancel() {
        setPairing(PairingUiState("Living Room TV", PairingPhase.Connecting, false))
        composeRule.onNodeWithTag(PairingTestTags.CONNECTING).assertIsDisplayed()
        composeRule.onNodeWithText("Connecting to your television…").assertIsDisplayed()
        composeRule.onNodeWithTag(PairingTestTags.CANCEL).performClick()
        assertEquals(1, cancels)
    }

    @Test
    fun cancelIsAlwaysAvailable() {
        setPairing(PairingUiState("Living Room TV", PairingPhase.WaitingForApproval, true))
        composeRule.onNodeWithTag(PairingTestTags.CANCEL).performClick()
        assertEquals(1, cancels)
    }

    @Test
    fun aDeniedApprovalOffersRetryInOrdinaryLanguage() {
        setPairing(
            PairingUiState("Living Room TV", PairingPhase.Failed(TvFailure.NeedsRepair), false)
        )
        composeRule
            .onNodeWithTag(PairingTestTags.FAILURE_MESSAGE)
            .assertTextEquals(
                "AppT isn't allowed on this television yet. Allow it on the television, then try again."
            )
        composeRule.onNodeWithTag(PairingTestTags.RETRY).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun anUnreachableTelevisionExplainsTheHomeNetwork() {
        setPairing(
            PairingUiState("Living Room TV", PairingPhase.Failed(TvFailure.Unreachable), false)
        )
        composeRule.onNodeWithTag(PairingTestTags.FAILED).assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun anUnsupportedTelevisionSaysSoPlainly() {
        setPairing(
            PairingUiState("Living Room TV", PairingPhase.Failed(TvFailure.Unsupported), false)
        )
        composeRule
            .onNodeWithTag(PairingTestTags.FAILURE_MESSAGE)
            .assertTextEquals("AppT can't control this television yet.")
    }

    @Test
    @Config(qualifiers = "w360dp-h1200dp")
    fun everyControlMeetsTheTouchTargetFloorAndIsDescribedForTalkBack() {
        setPairing(
            PairingUiState(
                tvName = "Living Room TV",
                phase = PairingPhase.Failed(TvFailure.NeedsRepair),
                recallHintVisible = false,
            )
        )
        composeRule.assertEveryClickableMeetsTheTouchTargetFloor()
        composeRule.assertEveryControlIsDescribedForTalkBack()
    }
}
