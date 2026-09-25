package dev.anthracite.appt.remote

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvFailure
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

/** Remote's product shape and accessibility contracts (presentation.md#remote). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RemoteScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val commands = mutableListOf<TvCommand>()
    private var retries = 0

    private fun setRemote(state: RemoteUiState) {
        composeRule.setContent {
            AppTTheme {
                RemoteScreen(
                    state = state,
                    onCommand = { commands += it },
                    onRetry = { retries++ },
                )
            }
        }
    }

    private fun ready(keys: List<RemoteKey> = MINIMAL_REMOTE_KEYS) =
        RemoteUiState("Living Room TV", ConnectionUi.Ready, keys)

    @Test
    @Config(qualifiers = "w360dp-h1400dp")
    fun theMinimalControlSetSendsTypedCommands() {
        setRemote(ready())
        composeRule.onNodeWithTag(RemoteTestTags.TV_NAME).assertIsDisplayed()
        composeRule.onNodeWithText("Ready").assertIsDisplayed()

        composeRule.onNodeWithTag(RemoteTestTags.key(RemoteKey.VolumeUp)).performClick()
        composeRule.onNodeWithTag(RemoteTestTags.key(RemoteKey.Enter)).performClick()
        composeRule.onNodeWithTag(RemoteTestTags.key(RemoteKey.Left)).performClick()

        assertEquals(
            listOf(
                TvCommand.Tap(RemoteKey.VolumeUp),
                TvCommand.Tap(RemoteKey.Enter),
                TvCommand.Tap(RemoteKey.Left),
            ),
            commands,
        )
    }

    @Test
    fun onlyTheKeysTheTelevisionAcceptsAreRendered() {
        setRemote(ready(listOf(RemoteKey.VolumeUp, RemoteKey.Mute)))
        val rendered =
            composeRule
                .onAllNodesWithTag(RemoteTestTags.key(RemoteKey.VolumeUp))
                .fetchSemanticsNodes()
                .size
        assertEquals(1, rendered)
        composeRule.onNodeWithTag(RemoteTestTags.key(RemoteKey.VolumeUp)).assertIsDisplayed()
        composeRule.onNodeWithTag(RemoteTestTags.key(RemoteKey.VolumeDown)).assertDoesNotExist()
        composeRule.onNodeWithTag(RemoteTestTags.key(RemoteKey.Up)).assertDoesNotExist()
        assertEquals("no dead control is composed", 2, composeRule.clickableNodes().size)
    }

    @Test
    fun powerIsNotOnTheMinimalRemote() {
        setRemote(ready())
        composeRule.onNodeWithTag(RemoteTestTags.key(RemoteKey.Power)).assertDoesNotExist()
    }

    @Test
    fun noTechnicalIdentifierReachesTheScreen() {
        setRemote(ready())
        composeRule.assertNoTechnicalIdentifierIsExposed("8002", "KEY_VOLUP", "ws://")
    }

    @Test
    fun anUnreadySessionShowsStatusAndNoControls() {
        setRemote(RemoteUiState("Living Room TV", ConnectionUi.WaitingForApproval, emptyList()))
        val shown =
            composeRule
                .onAllNodesWithText("Approve AppT on your television to continue")
                .fetchSemanticsNodes()
        assertEquals("the status is repeated beside the empty pad", 2, shown.size)
        assertEquals(0, composeRule.clickableNodes().size)
    }

    @Test
    fun anUnreachableSessionOffersTryAgain() {
        setRemote(
            RemoteUiState(
                "Living Room TV",
                ConnectionUi.Unavailable(TvFailure.Unreachable),
                emptyList(),
            )
        )
        composeRule.onNodeWithTag(RemoteTestTags.RECOVERY).assertIsDisplayed()
        composeRule.onNodeWithTag(RemoteTestTags.RETRY).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun aRepairSessionExplainsTheApprovalInOrdinaryLanguage() {
        setRemote(
            RemoteUiState("Living Room TV", ConnectionUi.NeedsRepair(TvFailure.NeedsRepair), emptyList())
        )
        val shown =
            composeRule
                .onAllNodesWithText("AppT isn't allowed on this television yet.")
                .fetchSemanticsNodes()
        assertEquals("the reason is repeated beside the recovery action", 2, shown.size)
    }

    @Test
    @Config(qualifiers = "w360dp-h1400dp")
    fun everyControlMeetsTheTouchTargetFloorAndIsDescribedForTalkBack() {
        setRemote(ready())
        composeRule.assertEveryClickableMeetsTheTouchTargetFloor()
        composeRule.assertEveryControlIsDescribedForTalkBack()
    }
}
