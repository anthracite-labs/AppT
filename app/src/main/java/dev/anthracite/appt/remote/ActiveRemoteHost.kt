package dev.anthracite.appt.remote

import dev.anthracite.appt.samsung.RemoteSession
import dev.anthracite.appt.samsung.SamsungTvs
import dev.anthracite.appt.samsung.SessionSnapshot
import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** The television the Active Remote currently holds, and its live session snapshot. */
data class ActiveRemoteSnapshot(val tvId: TvId, val session: SessionSnapshot)

/**
 * The application-scoped owner of the single Active Remote (lifecycle.md, presentation.md).
 *
 * `enter` is the only path that opens a session, so no screen or ViewModel calls `SamsungTvs.open`
 * and the licensing gate cannot be bypassed by reaching a surface directly. S03 has no account, no
 * billing and no entitlement, so the entry decision is always allowed; the decision point is the
 * [entryAllowed] parameter, where sync.md#remote-entry-gate's `AccountGate` plugs in when the
 * account slice lands.
 *
 * Interest counting is [retain]/[release], not navigation: Pairing, Remote, a sheet and a
 * configuration change all hold interest, so a rotation never releases and never re-opens. Releasing
 * the last interest starts the 15-second grace [lifecycle.md] owns, and [close] is immediate.
 */
class ActiveRemoteHost(
    private val samsungTvs: SamsungTvs,
    private val scope: CoroutineScope,
    private val entryAllowed: (TvId) -> Boolean = { true },
) {
    private val mutableCurrent = MutableStateFlow<ActiveRemoteSnapshot?>(null)
    val current: StateFlow<ActiveRemoteSnapshot?> = mutableCurrent.asStateFlow()

    private val owners = mutableSetOf<Any>()
    private var observation: Job? = null
    private var grace: Job? = null

    /**
     * Opens (or reuses) the session for [tvId].
     *
     * An already retained television is returned as it is: no second socket, no re-pair, and no
     * gate re-evaluation, which is what makes Pairing → Remote a handoff rather than a second
     * connection. A denied entry opens nothing.
     *
     * Not a suspending function on purpose: `open` is not suspending either, so a caller can enter
     * the television and act on the result in the same frame rather than racing a recomposition.
     */
    fun enter(tvId: TvId) {
        if (!entryAllowed(tvId)) return
        val held = mutableCurrent.value
        if (held != null && held.tvId == tvId) return
        close()
        val session = samsungTvs.open(tvId, scope)
        observe(tvId, session)
    }

    /** Takes an interest in the held session. Idempotent for the same [owner]. */
    fun retain(owner: Any) {
        owners += owner
        grace?.cancel()
        grace = null
    }

    /** Releases an interest. The last release starts the grace window. */
    fun release(owner: Any) {
        owners -= owner
        if (owners.isEmpty() && mutableCurrent.value != null) startGrace()
    }

    /** Releases the socket now. Closing is not forgetting: S03 persists no pairing material. */
    fun close() {
        grace?.cancel()
        grace = null
        observation?.cancel()
        observation = null
        mutableCurrent.value?.session?.close()
        mutableCurrent.value = null
        owners.clear()
    }

    private fun observe(tvId: TvId, session: RemoteSession) {
        mutableCurrent.value = ActiveRemoteSnapshot(tvId, session.snapshot.value)
        observation?.cancel()
        observation =
            scope.launch {
                session.snapshot.collect { snapshot ->
                    mutableCurrent.value = ActiveRemoteSnapshot(tvId, snapshot)
                }
            }
    }

    private fun startGrace() {
        grace?.cancel()
        grace =
            scope.launch {
                delay(GRACE)
                close()
            }
    }

    private companion object {
        /** lifecycle.md: the grace window owned by the host, in milliseconds. */
        const val GRACE = 15_000L
    }
}
