package dev.anthracite.appt.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.anthracite.appt.welcome.WelcomeScreen

/**
 * The single Navigation Compose graph, with type-safe Kotlin-serialization routes
 * (presentation.md#route-graph).
 *
 * S01's graph has exactly one destination. `Find my TV` has no destination to reach yet — the
 * local-network explanation is S02 — so the action is wired to a caller-supplied callback that the
 * app leaves empty rather than inventing a placeholder route.
 */
@Composable
fun AppTNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    onFindMyTv: () -> Unit = {},
) {
    NavHost(navController = navController, startDestination = WelcomeRoute, modifier = modifier) {
        composable<WelcomeRoute> { WelcomeScreen(onFindMyTv = onFindMyTv) }
    }
}
