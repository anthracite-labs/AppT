package dev.anthracite.appt.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anthracite.appt.preferences.InteractionPreferences
import dev.anthracite.appt.preferences.NavigationMode
import dev.anthracite.appt.testing.assertEveryClickableMeetsTheTouchTargetFloor
import dev.anthracite.appt.testing.assertEveryControlIsDescribedForTalkBack
import dev.anthracite.appt.tokens.AppTTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(pointerAvailable: Boolean = false) {
        composeRule.setContent {
            AppTTheme {
                SettingsScreen(
                    state =
                        SettingsUiState(
                            interaction =
                                InteractionPreferences(
                                    hapticsEnabled = true,
                                    volumeButtonsControlTv = true,
                                    navigationMode = NavigationMode.Directional,
                                ),
                            pointerAvailable = pointerAvailable,
                            appVersion = "0.1.0",
                        ),
                    onBack = {},
                    onHaptics = {},
                    onPhoneVolumeButtons = {},
                    onNavigationMode = {},
                )
            }
        }
    }

    @Test
    fun rendersOnlyFunctionalInteractionAndAboutSections() {
        show()
        composeRule.onNodeWithText("Interaction").assertIsDisplayed()
        composeRule.onNodeWithText("Haptics").assertIsDisplayed()
        composeRule.onNodeWithText("Phone volume buttons").assertIsDisplayed()
        composeRule.onNodeWithText("Navigation mode").assertIsDisplayed()
        composeRule.onNodeWithText("About").assertIsDisplayed()
        composeRule.onNodeWithText("App version").assertIsDisplayed()
        composeRule.onNodeWithText("0.1.0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Account & License").assertDoesNotExist()
        composeRule.onNodeWithText("TVs").assertDoesNotExist()
        composeRule.onNodeWithText("Privacy & Diagnostics").assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.POINTER).assertDoesNotExist()
    }

    @Test
    fun pointerChoiceAppearsOnlyWhenTheLiveCapabilityAllowsIt() {
        show(pointerAvailable = true)
        composeRule.onNodeWithTag(SettingsTestTags.POINTER).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.DIRECTIONAL).assertIsDisplayed()
    }

    @Test
    fun settingsControlsHaveAccessibleLabelsAndTargets() {
        show(pointerAvailable = true)
        composeRule.assertEveryClickableMeetsTheTouchTargetFloor()
        composeRule.assertEveryControlIsDescribedForTalkBack()
    }
}
