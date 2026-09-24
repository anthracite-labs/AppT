package dev.anthracite.appt.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.anthracite.appt.AppSettingsLauncher
import dev.anthracite.appt.discovery.DiscoveryScreen
import dev.anthracite.appt.discovery.DiscoveryViewModel
import dev.anthracite.appt.gate.LocalNetworkPhase
import dev.anthracite.appt.gate.PermissionGate
import dev.anthracite.appt.localnetwork.LocalNetworkScreen
import dev.anthracite.appt.localnetwork.LocalNetworkViewModel
import dev.anthracite.appt.samsung.SamsungTvs
import dev.anthracite.appt.welcome.WelcomeScreen

/**
 * The single Navigation Compose graph, with type-safe Kotlin-serialization routes
 * (presentation.md#route-graph).
 *
 * S02 flow: Welcome → `Find my TV` → LocalNetwork explanation → Continue grants the gate →
 * Discovery, which scans immediately. The explanation is popped once granted, so Back from
 * Discovery returns to Welcome. A scan that reports local-network access blocked replaces
 * Discovery with the explanation in its Denied phase; Try again returns to Discovery with a fresh
 * scan. Nothing loops on its own.
 */
@Composable
fun AppTNavGraph(
    samsungTvs: SamsungTvs,
    permissionGate: PermissionGate,
    appSettings: AppSettingsLauncher,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = WelcomeRoute, modifier = modifier) {
        composable<WelcomeRoute> {
            WelcomeScreen(onFindMyTv = { navController.navigate(LocalNetworkRoute) })
        }
        composable<LocalNetworkRoute> {
            LocalNetworkDestination(
                gate = permissionGate,
                appSettings = appSettings,
                onGranted = {
                    navController.navigate(DiscoveryRoute) {
                        popUpTo<LocalNetworkRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<DiscoveryRoute> {
            DiscoveryDestination(
                samsungTvs = samsungTvs,
                gate = permissionGate,
                onDenied = {
                    navController.navigate(LocalNetworkRoute) {
                        popUpTo<DiscoveryRoute> { inclusive = true }
                    }
                },
            )
        }
    }
}

@Composable
private fun LocalNetworkDestination(
    gate: PermissionGate,
    appSettings: AppSettingsLauncher,
    onGranted: () -> Unit,
) {
    val viewModel = viewModel { LocalNetworkViewModel(gate, appSettings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnGranted by rememberUpdatedState(onGranted)
    // presentation.md: "Granted advances automatically to Discovery".
    LaunchedEffect(state.phase) { if (state.phase == LocalNetworkPhase.Granted) currentOnGranted() }
    LocalNetworkScreen(
        state = state,
        onContinue = viewModel::onContinue,
        onRetry = viewModel::onRetry,
        onOpenSettings = viewModel::onOpenSettings,
    )
}

@Composable
private fun DiscoveryDestination(samsungTvs: SamsungTvs, gate: PermissionGate, onDenied: () -> Unit) {
    val viewModel = viewModel { DiscoveryViewModel(samsungTvs, gate) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gatePhase by gate.phase.collectAsStateWithLifecycle()
    val currentOnDenied by rememberUpdatedState(onDenied)
    // A blocked local network returns to the explanation (discovery.md#permission-gate).
    LaunchedEffect(gatePhase) { if (gatePhase == LocalNetworkPhase.Denied) currentOnDenied() }
    DiscoveryScreen(state = state, onRescan = viewModel::onRescan, onPick = viewModel::onPick)
}
