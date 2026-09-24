package dev.anthracite.appt.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthracite.appt.gate.PermissionGate
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
 * * Each [onRescan] starts a fresh `discover()`.
 * * [onPick] cancels the scan. S02 ends there: opening a session, creating a Room row, and pairing
 *   navigation belong to S03+.
 * * `Failed(LocalNetworkDenied)` is reported to the gate, which returns the user to the
 *   explanation; nothing here retries it.
 */
class DiscoveryViewModel(private val samsungTvs: SamsungTvs, private val gate: PermissionGate) :
    ViewModel() {

    private val mutableState = MutableStateFlow(DiscoveryUiState.Initial)
    val state: StateFlow<DiscoveryUiState> = mutableState.asStateFlow()

    private var scan: Job? = null

    init {
        startScan()
    }

    fun onRescan() = startScan()

    fun onPick(tvId: TvId) {
        val card = mutableState.value.cards.firstOrNull { it.tvId == tvId }
        // Unsupported cards have no control affordance, so a pick of one is not an intent.
        if (card == null || card.state == CardState.Unsupported) return
        scan?.cancel()
        scan = null
        mutableState.update { current ->
            if (current.scan == ScanPhase.Scanning) current.copy(scan = ScanPhase.Finished)
            else current
        }
    }

    private fun startScan() {
        scan?.cancel()
        mutableState.value = DiscoveryUiState.Initial
        scan =
            viewModelScope.launch {
                samsungTvs.discover().collect { event ->
                    mutableState.update { it.reduce(event) }
                    if (event == DiscoveryEvent.Failed(TvFailure.LocalNetworkDenied))
                        gate.reportDenied()
                }
            }
    }
}
