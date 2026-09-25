package dev.anthracite.appt.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthracite.appt.data.TvProfiles
import dev.anthracite.appt.gate.PermissionGate
import dev.anthracite.appt.samsung.DiscoveredTv
import dev.anthracite.appt.samsung.DiscoveryEvent
import dev.anthracite.appt.samsung.SamsungTvs
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Collects `SamsungTvs.discover()` for the Discovery screen (presentation.md#discovery).
 * * The first scan starts as soon as the ViewModel exists: Discovery is only reached once the gate
 *   is granted, and discovery.md requires `discover()` immediately after that. There is no separate
 *   setup step.
 * * A ViewModel survives rotation, so recreation never re-scans. After process death a new
 *   ViewModel is created for the restored route, which starts exactly one fresh bounded scan and
 *   then stops ("Scanning restarts once on restoration, then stops").
 * * Scans are foreground-only (discovery.md: "No scan runs in the background"). [onStopped] cancels
 *   a running scan when the screen leaves the foreground; the next [onStarted] starts one fresh
 *   bounded scan in its place. A scan that already ended is not restarted, and the first
 *   [onStarted] after creation does not duplicate the initial scan.
 * * Each [onRescan] starts a fresh `discover()`.
 * * [onPick] cancels the scan, upserts the device-local profile row for the selected card, and
 *   publishes it as [selected] so the route can enter the television through `ActiveRemoteHost`
 *   (data.md#ownership-rules). Opening the session and pairing navigation are not this ViewModel's
 *   job.
 * * `Failed(LocalNetworkDenied)` is reported to the gate, which returns the user to the
 *   explanation; nothing here retries it.
 */
class DiscoveryViewModel(
    private val samsungTvs: SamsungTvs,
    private val gate: PermissionGate,
    private val tvProfiles: TvProfiles,
) : ViewModel() {

    private val mutableState = MutableStateFlow(DiscoveryUiState.Initial)
    val state: StateFlow<DiscoveryUiState> = mutableState.asStateFlow()

    private val mutableSelected = MutableStateFlow<TvId?>(null)

    /**
     * The television the user chose, once its profile row is written. The route enters it and
     * navigates to Pairing; a configuration change re-reads this rather than picking again.
     */
    val selected: StateFlow<TvId?> = mutableSelected.asStateFlow()

    private var scan: Job? = null

    /**
     * The cards discovery confirmed, kept so a selection can be recorded without widening the UI
     * state with a type the screen never renders.
     */
    private val discovered = mutableMapOf<TvId, DiscoveredTv>()

    /** A running scan was cancelled by [onStopped] and is owed a fresh one on [onStarted]. */
    private var resumeOnStart = false

    init {
        startScan()
    }

    fun onRescan() = startScan()

    /** The Discovery screen is visible again (lifecycle ON_START). */
    fun onStarted() {
        if (resumeOnStart) startScan()
    }

    /**
     * The Discovery screen left the foreground (lifecycle ON_STOP, excluding configuration changes,
     * which keep this ViewModel and its scan).
     */
    fun onStopped() {
        if (scan?.isActive != true) return
        scan?.cancel()
        scan = null
        resumeOnStart = true
    }

    /**
     * The user chose a card.
     *
     * Unsupported cards have no control affordance, so a pick of one is not an intent: no row is
     * written and no session is opened. A `NeedsPairing` or `ReadyToOpen` card cancels the scan,
     * upserts the profile row, and publishes the selection.
     */
    fun onPick(tvId: TvId) {
        val card = mutableState.value.cards.firstOrNull { it.tvId == tvId }
        if (card == null || card.state == CardState.Unsupported) return
        val tv = discovered[tvId] ?: return
        scan?.cancel()
        scan = null
        mutableState.update { current ->
            if (current.scan == ScanPhase.Scanning) current.copy(scan = ScanPhase.Finished)
            else current
        }
        viewModelScope.launch {
            // The row is written before the session opens: remembering is the user's action, and
            // the television is not opened for a card this phone has not recorded.
            tvProfiles.rememberSelected(card.tvId, card.label, tv.stableIdentity)
            mutableSelected.value = card.tvId
        }
    }

    private fun startScan() {
        resumeOnStart = false
        scan?.cancel()
        mutableState.value = DiscoveryUiState.Initial
        scan =
            viewModelScope.launch {
                samsungTvs.discover().collect { event ->
                    if (event is DiscoveryEvent.Found) discovered[event.tv.id] = event.tv
                    mutableState.update { it.reduce(event) }
                    if (event == DiscoveryEvent.Failed(TvFailure.LocalNetworkDenied))
                        gate.reportDenied()
                }
            }
    }
}
