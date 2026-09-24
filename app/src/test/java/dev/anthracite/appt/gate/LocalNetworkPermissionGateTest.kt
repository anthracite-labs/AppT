package dev.anthracite.appt.gate

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The V1 permission gate at targetSdk 36 (docs/architecture/discovery.md#permission-gate), and the
 * installed app's permission surface.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LocalNetworkPermissionGateTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val preferences =
        context.getSharedPreferences(LocalNetworkPermissionGate.PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun gate() = LocalNetworkPermissionGate(preferences, targetSdk = 36)

    @Test
    fun v1ProbesDoNotRequestNearbyWifiDevices() {
        // Nothing is requested at runtime for the V1 probes at target 36...
        assertEquals(emptyList<String>(), DiscoveryPermissions.runtimeRequestFor(36))

        // ...and the installed app neither declares nor can request the permissions the V1
        // probes must not use, while it does declare the four install-time ones they need.
        val packageInfo =
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val declared = packageInfo.requestedPermissions.orEmpty().toSet()
        listOf(
                "android.permission.NEARBY_WIFI_DEVICES",
                "android.permission.ACCESS_LOCAL_NETWORK",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_BACKGROUND_LOCATION",
            )
            .forEach { forbidden -> assertFalse("$forbidden must not be declared", forbidden in declared) }
        assertTrue(
            declared.containsAll(
                listOf(
                    "android.permission.INTERNET",
                    "android.permission.ACCESS_NETWORK_STATE",
                    "android.permission.ACCESS_WIFI_STATE",
                    "android.permission.CHANGE_WIFI_MULTICAST_STATE",
                )
            )
        )
        assertEquals("V1 stays at targetSdk 36", 36, context.applicationInfo.targetSdkVersion)
    }

    @Test
    fun aTargetPastV1MustAdoptTheLocalNetworkPermissionFirst() {
        assertThrows(IllegalStateException::class.java) { DiscoveryPermissions.runtimeRequestFor(37) }
        assertThrows(IllegalStateException::class.java) {
            LocalNetworkPermissionGate(preferences, targetSdk = 37)
        }
    }

    @Test
    fun explanationComesFirstAndContinueGrantsWithoutAPrompt() {
        preferences.edit().clear().commit()
        val gate = gate()
        assertEquals(LocalNetworkPhase.Explain, gate.phase.value)
        gate.acknowledge()
        assertEquals(LocalNetworkPhase.Granted, gate.phase.value)
    }

    @Test
    fun acknowledgmentIsRestoredAndDenialClearsIt() {
        preferences.edit().clear().commit()
        gate().acknowledge()
        assertEquals("restored after process death", LocalNetworkPhase.Granted, gate().phase.value)

        val gate = gate()
        gate.reportDenied()
        assertEquals(LocalNetworkPhase.Denied, gate.phase.value)
        assertEquals("the next visit explains again", LocalNetworkPhase.Explain, gate().phase.value)
    }
}
