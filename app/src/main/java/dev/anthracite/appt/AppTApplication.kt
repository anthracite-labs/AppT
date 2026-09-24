package dev.anthracite.appt

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.anthracite.appt.gate.LocalNetworkPermissionGate
import dev.anthracite.appt.gate.PermissionGate
import dev.anthracite.appt.samsung.SamsungModule
import dev.anthracite.appt.samsung.SamsungTvs

/**
 * The application-scope composition root.
 *
 * modules.md names Hilt as the eventual composition mechanism, but S02 has exactly two
 * application-scoped objects, so they are composed here by hand instead of adding a DI framework
 * and its annotation processing in a discovery slice. Each is created once per process: one
 * [SamsungTvs] is one scan owner and one multicast-lock owner.
 */
class AppTApplication : Application() {
    val samsungTvs: SamsungTvs by lazy { SamsungModule.samsungTvs(this) }

    val permissionGate: PermissionGate by lazy {
        LocalNetworkPermissionGate(
            getSharedPreferences(LocalNetworkPermissionGate.PREFERENCES_NAME, MODE_PRIVATE),
            applicationInfo.targetSdkVersion,
        )
    }

    val appSettings: AppSettingsLauncher = AppSettingsLauncher {
        startActivity(
            Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts(PACKAGE_SCHEME, packageName, null),
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private companion object {
        const val PACKAGE_SCHEME = "package"
    }
}
