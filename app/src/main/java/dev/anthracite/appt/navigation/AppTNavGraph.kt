package dev.anthracite.appt.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.anthracite.appt.AppSettingsLauncher
import dev.anthracite.appt.data.TvProfiles
import dev.anthracite.appt.discovery.DiscoveryScreen
import dev.anthracite.appt.discovery.DiscoveryViewModel
import dev.anthracite.appt.gate.LocalNetworkPhase
import dev.anthracite.appt.gate.PermissionGate
import dev.anthracite.appt.localnetwork.LocalNetworkScreen
import dev.anthracite.appt.localnetwork.LocalNetworkViewModel
import dev.anthracite.appt.pairing.PairingScreen
import dev.anthracite.appt.pairing.PairingViewModel
import dev.anthracite.appt.preferences.PreferenceStore
import dev.anthracite.appt.remote.ActiveRemoteHost
import dev.anthracite.appt.remote.RemoteScreen
import dev.anthracite.appt.remote.RemoteViewModel
import dev.anthracite.appt.samsung.SamsungTvs
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvId
import dev.anthracite.appt.welcome.WelcomeScreen

/**
 * The single Navigation Compose graph, with type-safe Kotlin-serialization routes
 * (presentation.md#route-graph).
 *
 * S02 flow: Welcome → `Find my TV` → LocalNetwork explanation → Continue grants the gate →
 * Discovery, which scans immediately. The explanation is popped once granted, so Back from
 * Discovery returns to Welcome. A scan that reports local-network access blocked replaces Discovery
 * with the explanation in its Denied phase; Try again returns to Discovery with a fresh scan.
 * Nothing loops on its own.
 *
 * S03 flow (Issue #79): choosing a controllable Discovery card enters the television through
 * [ActiveRemoteHost] and navigates to Pairing, which observes that session. When the session reaches
 * `Ready`, Pairing hands the same session to Remote. Cancel from Pairing closes the session and
 * returns to Discovery.
 */
@Composable
fun AppTNavGraph(
    samsungTvs: SamsungTvs,
    permissionGate: PermissionGate,
    appSettings: AppSettingsLauncher,
    activeRemoteHost: ActiveRemoteHost,
    tvProfiles: TvProfiles,
    preferenceStore: PreferenceStore,
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
                activeRemoteHost = activeRemoteHost,
                tvProfiles = tvProfiles,
                onDenied = {
                    navController.navigate(LocalNetworkRoute) {
                        popUpTo<DiscoveryRoute> { inclusive = true }
                    }
                },
                onPicked = { tvId -> navController.navigate(PairingRoute(tvId.value)) },
            )
        }
        composable<PairingRoute> { entry ->
            val route = entry.toRoute<PairingRoute>()
            PairingDestination(
                tvId = route.tvId,
                activeRemoteHost = activeRemoteHost,
                tvProfiles = tvProfiles,
                onCancel = {
                    activeRemoteHost.close()
                    navController.popBackStack()
                },
                onApproved = {
                    navController.navigate(RemoteRoute(route.tvId)) {
                        popUpTo<PairingRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<RemoteRoute> { entry ->
            val route = entry.toRoute<RemoteRoute>()
            RemoteDestination(
                tvId = route.tvId,
                activeRemoteHost = activeRemoteHost,
                tvProfiles = tvProfiles,
                preferenceStore = preferenceStore,
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
private fun DiscoveryDestination(
    samsungTvs: SamsungTvs,
    gate: PermissionGate,
    activeRemoteHost: ActiveRemoteHost,
    tvProfiles: TvProfiles,
    onDenied: () -> Unit,
    onPicked: (TvId) -> Unit,
) {
    val viewModel = viewModel { DiscoveryViewModel(samsungTvs, gate, tvProfiles) }
    val activity = LocalActivity.current
    // Scans run only while Discovery is in the foreground (discovery.md). A configuration change
    // keeps the ViewModel and its scan, so it is not treated as leaving the foreground.
    LifecycleStartEffect(viewModel) {
        viewModel.onStarted()
        onStopOrDispose { if (activity?.isChangingConfigurations != true) viewModel.onStopped() }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gatePhase by gate.phase.collectAsStateWithLifecycle()
    val currentOnDenied by rememberUpdatedState(onDenied)
    val currentOnPicked by rememberUpdatedState(onPicked)
    // A blocked local network returns to the explanation (discovery.md#permission-gate).
    LaunchedEffect(gatePhase) { if (gatePhase == LocalNetworkPhase.Denied) currentOnDenied() }
    DiscoveryScreen(state = state, onRescan = viewModel::onRescan, onPick = viewModel::onPick)
    // The route, not the screen, enters the television and navigates: `onPick` writes the profile
    // row first, and this runs once that row exists. Entering is the host's job, so one television
    // is opened once and never a second time, and a configuration change re-reads `selected`
    // rather than entering again.
    LaunchedEffect(viewModel.selected) { selected ->
        selected?.let {
            activeRemoteHost.enter(it)
            currentOnPicked(it)
        }
    }
}

/**
 * Pairing for one television.
 *
 * The session is the host's, so this destination observes it rather than opening one: Pairing and
 * Remote read the same session, and a configuration change re-attaches to the same ViewModel rather
 * than issuing a second `open`.
 */
@Composable
private fun PairingDestination(
    tvId: String,
    activeRemoteHost: ActiveRemoteHost,
    tvProfiles: TvProfiles,
    onCancel: () -> Unit,
    onApproved: () -> Unit,
) {
    val viewModel = viewModel {
        PairingViewModel(TvId(tvId), activeRemoteHost, tvProfiles)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current by activeRemoteHost.current.collectAsStateWithLifecycle()
    val currentOnApproved by rememberUpdatedState(onApproved)
    // presentation.md: "Success transitions directly into Remote". The handoff happens once, when
    // the session the host already holds becomes Ready.
    LaunchedEffect(current?.session?.state) {
        if (current?.session?.state == SessionState.Ready) currentOnApproved()
    }
    PairingScreen(
        state = state,
        onCancel = onCancel,
        onRetryApproval = viewModel::onRetryApproval,
    )
}

/** Remote for the same television, on the session Pairing opened. */
@Composable
private fun RemoteDestination(
    tvId: String,
    activeRemoteHost: ActiveRemoteHost,
    tvProfiles: TvProfiles,
    preferenceStore: PreferenceStore,
) {
    val viewModel = viewModel {
        RemoteViewModel(TvId(tvId), activeRemoteHost, tvProfiles, preferenceStore)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    RemoteScreen(state = state, onCommand = viewModel::onCommand, onRetry = viewModel::onRetry)
}
