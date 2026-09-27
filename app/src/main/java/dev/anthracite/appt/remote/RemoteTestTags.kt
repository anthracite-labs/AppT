package dev.anthracite.appt.remote

import dev.anthracite.appt.samsung.RemoteKey

/** Test tags for the Remote surface, one per node a test needs to find. */
object RemoteTestTags {
    const val TV_NAME = "remote:tv-name"
    const val STATUS = "remote:status"
    const val CONTROLS = "remote:controls"
    const val RECOVERY = "remote:recovery"
    const val RETRY = "remote:retry"

    /** The tag for one key's control, so a test can press a key it knows is rendered. */
    fun key(key: RemoteKey): String = "remote:key:" + key.name
}
