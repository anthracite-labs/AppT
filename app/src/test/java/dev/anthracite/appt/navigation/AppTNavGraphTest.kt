package dev.anthracite.appt.navigation

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anthracite.appt.tokens.AppTTheme
import dev.anthracite.appt.welcome.WelcomeTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The S01 acceptance criterion "the navigation graph contains only
 * `WelcomeRoute`", asserted against the graph itself rather than by reading
 * the source.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppTNavGraphTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the graph contains exactly one destination and it is WelcomeRoute`() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            AppTTheme { AppTNavGraph(navController = navController) }
        }
        composeRule.waitForIdle()

        val destinations = navController.graph.iterator().asSequence().toList()
        assertEquals(
            "S01's graph must contain only WelcomeRoute; later routes arrive with their slices",
            1,
            destinations.size,
        )

        // ...and that one destination is WelcomeRoute itself, not merely
        // "some single route".
        assertTrue(
            "the only destination must be WelcomeRoute, but was ${destinations.single().route}",
            destinations.single().hasRoute<WelcomeRoute>(),
        )

        // The start destination is that same route, so the app opens on Welcome.
        assertEquals(
            navController.graph.findStartDestination().id,
            navController.currentBackStackEntry?.destination?.id,
        )
    }

    @Test
    fun `the app opens on Welcome`() {
        composeRule.setContent {
            AppTTheme { AppTNavGraph() }
        }
        composeRule.onNodeWithTag(WelcomeTestTags.VALUE_PROPOSITION).assertExists()
        composeRule.onNodeWithTag(WelcomeTestTags.PRIMARY_ACTION).assertExists()
    }
}
