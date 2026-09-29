package dev.anthracite.appt

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.room.Room
import dev.anthracite.appt.data.AppTDatabase
import dev.anthracite.appt.data.TvProfiles
import dev.anthracite.appt.gate.LocalNetworkPermissionGate
import dev.anthracite.appt.gate.PermissionGate
import dev.anthracite.appt.preferences.PreferenceStore
import dev.anthracite.appt.remote.ActiveRemoteHost
import dev.anthracite.appt.samsung.SamsungModule
import dev.anthracite.appt.samsung.SamsungTvs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The application-scope composition root.
 *
 * modules.md names Hilt as the eventual composition mechanism, but S03 still has only a handful of
 * application-scoped objects, so they are composed here by hand instead of adding a DI framework
 * and its annotation processing in a pairing slice. Each is created once per process: one
 * [SamsungTvs] is one scan owner and one multicast-lock owner, one [AppTDatabase] is one database,
 * and one [ActiveRemoteHost] is one Active Remote.
 *
 * [ActiveRemoteHost] is application-scoped on purpose (lifecycle.md): it owns the single live
 * session, so a configuration change re-attaches to it instead of opening a second socket, and its
 * scope outlives every Activity.
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

    /** The device-local application store (data.md#room): `appt.db`, version 1. */
    val database: AppTDatabase by lazy {
        Room.databaseBuilder(this, AppTDatabase::class.java, AppTDatabase.NAME).build()
    }

    /** The `TvProfile` store (data.md#ownership-rules). */
    val tvProfiles: TvProfiles by lazy { TvProfiles(database.tvProfileDao()) }

    /** The typed preference keys (data.md#datastore). */
    val preferenceStore: PreferenceStore by lazy { PreferenceStore(this) }

    /** The single Active Remote (lifecycle.md). Owns the one live session. */
    val activeRemoteHost: ActiveRemoteHost by lazy {
        ActiveRemoteHost(
            samsungTvs = samsungTvs,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            // data.md: `lastOpenedAt` is written when the session reaches `Ready`, which is the
            // host's transition to notice rather than a surface's.
            onSessionReady = { tvId -> tvProfiles.markOpened(tvId) },
        )
    }

    private companion object {
        const val PACKAGE_SCHEME = "package"
    }
}
