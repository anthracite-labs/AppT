package dev.anthracite.appt.remote

import dev.anthracite.appt.samsung.RemoteSession
import dev.anthracite.appt.samsung.SamsungTvs
import dev.anthracite.appt.samsung.SessionSnapshot
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvId
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The television the Active Remote currently holds: its live session, and the session's latest
 * snapshot.
 *
 * Both are needed. The snapshot is what the surfaces render, and the session is what they act on (a
 * command, an approval retry). The host is the only place that holds the session, so a surface can
 * never reach one the host does not hold.
 */
data class ActiveRemoteSnapshot(
    val tvId: TvId,
    val session: RemoteSession,
    val snapshot: SessionSnapshot,
)

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
 * configuration change all hold interest, so a rotation never releases and never re-opens.
 * Releasing the last interest starts the 15-second grace [lifecycle.md] owns, and [close] is
 * immediate.
 */
class ActiveRemoteHost(
    private val samsungTvs: SamsungTvs,
    private val scope: CoroutineScope,
    private val entryAllowed: (TvId) -> Boolean = { true },
    /**
     * Called once per session, when that session reaches [SessionState.Ready].
     *
     * data.md: `lastOpenedAt` is written when the session reaches `Ready`. The host is the one
     * place that observes every session, so it is the one place that can notice the transition
     * without a surface re-reading a snapshot it has already seen.
     */
    private val onSessionReady: suspend (TvId) -> Unit = {},
) {
    private val mutableCurrent = MutableStateFlow<ActiveRemoteSnapshot?>(null)
    val current: StateFlow<ActiveRemoteSnapshot?> = mutableCurrent.asStateFlow()

    private val owners = mutableSetOf<Any>()
    private var observation: Job? = null
    private var grace: Job? = null

    /** The session the host holds. Reaching it through [current] is the only way to act on it. */
    private var heldSession: RemoteSession? = null

    /**
     * Opens (or reuses) the session for [tvId].
     *
     * An already retained television is returned as it is: no second socket, no re-pair, and no
     * gate re-evaluation, which is what makes Pairing → Remote a handoff rather than a second
     * connection. A denied entry opens nothing.
     *
     * Not a suspending function on purpose: `open` is not suspending either, so a caller can enter
     * the television and act on the result in the same frame rather than racing a recomposition.
     *
     * A session is reused only while it is still live. A dead one — `Unreachable`, `Unsupported`,
     * `Closed` — is closed and replaced, which is what makes Remote's Try again open a real socket
     * instead of re-reading a session that already ended (connection.md: `Unreachable → Connecting:
     * caller opens again`).
     */
    fun enter(tvId: TvId) {
        if (!entryAllowed(tvId)) return
        val held = mutableCurrent.value
        val retryingSameTelevision = held != null && held.tvId == tvId
        if (retryingSameTelevision && held.snapshot.state.isLive()) return
        // A retry from a visible Remote keeps its owner so releasing that surface still starts
        // grace. Entering a different television is an ownership handoff and must not carry the
        // old surface's retain into the new session.
        closeSession(clearOwners = !retryingSameTelevision)
        val session = samsungTvs.open(tvId, scope)
        heldSession = session
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
        closeSession(clearOwners = true)
    }

    /**
     * Replacing a dead session keeps the visible surfaces' retain ownership. Explicit close clears
     * owners because the television has been left; retrying the same visible Remote has not.
     */
    private fun closeSession(clearOwners: Boolean) {
        grace?.cancel()
        grace = null
        observation?.cancel()
        observation = null
        heldSession?.close()
        heldSession = null
        mutableCurrent.value = null
        if (clearOwners) owners.clear()
    }

    private fun observe(tvId: TvId, session: RemoteSession) {
        mutableCurrent.value = ActiveRemoteSnapshot(tvId, session, session.snapshot.value)
        observation?.cancel()
        observation =
            scope.launch {
                // Once per session: a rotation or a re-attach collects the same `Ready` snapshot
                // again and must not write the row twice.
                var reachedReady = false
                session.snapshot.collect { snapshot ->
                    mutableCurrent.value = ActiveRemoteSnapshot(tvId, session, snapshot)
                    if (snapshot.state == SessionState.Ready && !reachedReady) {
                        reachedReady = true
                        try {
                            onSessionReady(tvId)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (ignoredWriteFailure: IOException) {
                            // A failed local write. Losing the profile row must not terminate
                            // control of a live television, so it is contained here.
                        } catch (ignoredClosedStore: IllegalStateException) {
                            // Room reports a store that has already gone away this way. Same
                            // reasoning as above: metadata is recoverable, the session is not.
                            // Local diagnostics arrives in S13.
                        }
                    }
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

        /**
         * A state the host can still act on. `NeedsRepair` is live: the session object is usable
         * and `retryApproval` is how it recovers, so re-entering would throw away a recoverable
         * attempt.
         */
        fun SessionState.isLive(): Boolean =
            this == SessionState.Connecting ||
                this == SessionState.AwaitingTvApproval ||
                this == SessionState.Ready ||
                this == SessionState.NeedsRepair
    }
}
