package dev.anthracite.appt

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dev.anthracite.appt.navigation.AppTNavGraph
import dev.anthracite.appt.tokens.AppTTheme
import dev.anthracite.appt.tokens.LocalMotionDurationScale

/**
 * The single activity (presentation.md#route-graph: "One activity").
 *
 * It declares no `android:configChanges`, so rotation and window resizing recreate it, as
 * lifecycle.md requires. Nothing the activity holds owns a session: the graph's screens observe the
 * application-scoped `ActiveRemoteHost`, so recreation re-attaches instead of re-opening.
 */
class MainActivity : ComponentActivity() {
    /** Installed only while the Remote destination is lifecycle-started. */
    internal var remoteVolumeKeyHandler: ((KeyEvent) -> Boolean)? = null

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (dispatchRemoteVolumeKeyEvent(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (dispatchRemoteVolumeKeyEvent(keyCode, event)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun dispatchRemoteVolumeKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }
        return remoteVolumeKeyHandler?.invoke(event) == true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AppTApplication
        setContent {
            CompositionLocalProvider(
                LocalMotionDurationScale provides platformMotionDurationScale()
            ) {
                AppTTheme {
                    AppTNavGraph(
                        samsungTvs = app.samsungTvs,
                        permissionGate = app.permissionGate,
                        appSettings = app.appSettings,
                        activeRemoteHost = app.activeRemoteHost,
                        tvProfiles = app.tvProfiles,
                        preferenceStore = app.preferenceStore,
                        appVersion = currentVersionName(),
                        diagnostics = app.localDiagnostics,
                    )
                }
            }
        }
    }

    /**
     * Reads the system animator duration scale. A user who has turned animations off reports `0f`,
     * which the motion tokens translate into zero-duration variants.
     */
    private fun platformMotionDurationScale(): Float =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    @Suppress("DEPRECATION")
    private fun currentVersionName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager
                .getPackageInfo(
                    packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0),
                )
                .versionName
                .orEmpty()
        } else {
            packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        }
}
