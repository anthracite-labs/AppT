package dev.anthracite.appt.samsung

import android.content.Context
import dev.anthracite.appt.samsung.internal.SamsungTvsImpl

/**
 * The composition entry point for the production [SamsungTvs] adapter.
 *
 * `app` may import only `dev.anthracite.appt.samsung` (docs/architecture/modules.md), so the
 * internal adapter is reached through this factory rather than by name. The caller owns the single
 * instance: one [SamsungTvs] per process, so one scan owner and one multicast-lock owner.
 */
object SamsungModule {
    fun samsungTvs(context: Context): SamsungTvs = SamsungTvsImpl.create(context.applicationContext)
}
