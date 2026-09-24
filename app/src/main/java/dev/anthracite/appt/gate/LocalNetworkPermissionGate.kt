package dev.anthracite.appt.gate

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The production [PermissionGate].
 *
 * The acknowledgment is restored across process death (presentation.md#process-death-restoration),
 * so it is kept in private preferences. Those preferences are excluded from backup and device
 * transfer by the application's backup rules, like every other local domain.
 *
 * At targetSdk 36 there is no system prompt for the V1 probes (see [DiscoveryPermissions]):
 * [acknowledge] grants directly and the gate never enters [LocalNetworkPhase.Requesting]. A denial
 * observed at scan time clears the acknowledgment, so the next visit shows the explanation again.
 */
class LocalNetworkPermissionGate(private val preferences: SharedPreferences, targetSdk: Int) :
    PermissionGate {

    init {
        check(DiscoveryPermissions.runtimeRequestFor(targetSdk).isEmpty()) {
            "the V1 gate has no runtime request flow"
        }
    }

    private val mutablePhase =
        MutableStateFlow(
            if (preferences.getBoolean(KEY_ACKNOWLEDGED, false)) LocalNetworkPhase.Granted
            else LocalNetworkPhase.Explain
        )

    override val phase: StateFlow<LocalNetworkPhase> = mutablePhase.asStateFlow()

    override fun acknowledge() {
        preferences.edit { putBoolean(KEY_ACKNOWLEDGED, true) }
        mutablePhase.value = LocalNetworkPhase.Granted
    }

    override fun reportDenied() {
        preferences.edit { putBoolean(KEY_ACKNOWLEDGED, false) }
        mutablePhase.value = LocalNetworkPhase.Denied
    }

    companion object {
        const val PREFERENCES_NAME: String = "appt.localNetworkGate"
        private const val KEY_ACKNOWLEDGED = "acknowledged"
    }
}
