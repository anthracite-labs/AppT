package dev.anthracite.appt.remote

import dev.anthracite.appt.samsung.RemoteKey

/** Stable tags used by Remote semantics and behavior tests. */
object RemoteTestTags {
    const val TV_NAME = "remote:tv-name"
    const val STATUS = "remote:status"
    const val STATUS_PANEL = "remote:status-panel"
    const val CONTROLS = "remote:controls"
    const val DIRECTIONAL_PAD = "remote:directional-pad"
    const val TOUCHPAD = "remote:touchpad"
    const val NAVIGATION_MODE = "remote:navigation-mode"
    const val SETTINGS = "remote:settings"
    const val RECOVERY = "remote:recovery"
    const val RETRY = "remote:retry"
    const val REPAIR = "remote:repair"
    const val REPAIR_CONFIRM = "remote:repair-confirm"
    const val REPAIR_CANCEL = "remote:repair-cancel"

    fun key(key: RemoteKey): String = "remote:key:" + key.name
}
