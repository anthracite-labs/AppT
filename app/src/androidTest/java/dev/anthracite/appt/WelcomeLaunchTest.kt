package dev.anthracite.appt

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.anthracite.appt.welcome.WelcomeTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runtime acceptance for Issue #27: "A debug APK builds, installs on an Android
 * device/emulator, and opens to Welcome."
 *
 * This runs on a real device/emulator against the installed debug APK, so it is
 * evidence of the launched application rather than of a composable rendered in
 * isolation. It deliberately launches [MainActivity] the way the launcher does
 * — through the activity's own lifecycle, with the real theme, the real
 * navigation graph and the real start destination — and then asserts on what
 * the user actually sees.
 *
 * `createEmptyComposeRule` is used rather than `createAndroidComposeRule`
 * because the latter would set its own content; here the activity must supply
 * its own, or the test would not be testing app launch at all.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class WelcomeLaunchTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun launchingTheAppOpensDirectlyToWelcome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // The Welcome surface is the start destination, so these are
            // present without any navigation having been performed.
            composeRule.onNodeWithTag(WelcomeTestTags.VALUE_PROPOSITION)
                .assertIsDisplayed()
            composeRule.onNodeWithTag(WelcomeTestTags.REASSURANCE)
                .assertIsDisplayed()
            composeRule.onNodeWithTag(WelcomeTestTags.PRIMARY_ACTION)
                .assertIsDisplayed()
        }
    }
}
