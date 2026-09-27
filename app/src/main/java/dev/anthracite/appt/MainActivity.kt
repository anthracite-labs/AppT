package dev.anthracite.appt

import android.os.Bundle
import android.provider.Settings
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
}
