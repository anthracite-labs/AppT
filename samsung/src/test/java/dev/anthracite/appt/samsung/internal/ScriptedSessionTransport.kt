package dev.anthracite.appt.samsung.internal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * The scripted [SessionTransport] adapter
 * (docs/architecture/modules.md#internal-seams-inside-samsung, testing.md#fake-adapters).
 *
 * It replays fixture frames with their delays, can end the socket, can refuse the connection, and
 * carries a generated test certificate identity. It records every frame the session writes, every
 * socket it opened, and — for the S04 fail-closed identity contract — every attempted URL and
 * whether it carried the saved token, which is how `identityMismatchDoesNotSendToken` and
 * `noPlaintextTokenAfterTlsPairing` are proven.
 *
 * The saved-identity behavior mirrors the production TLS adapter: when the caller resumes with a
 * saved pin on the TLS channel, the scripted television presents [presentedPin]; a mismatch is
 * [ConnectionAttempt.IdentityMismatch] with no socket, no URL and no token, exactly as a failed
 * saved-pin handshake leaves nothing on the wire.
 */
internal class ScriptedSessionTransport(
    private vararg val fixtures: SessionFixture,
    private val certificateIdentity: String? = null,
    /**
     * The SPKI the scripted television presents when a saved pin is checked. Defaults to
     * [certificateIdentity], so a test that scripts one identity is consistent.
     */
    private val presentedPin: String? = null,
    private val connectDelayMs: Long = 0,
    private val refusesConnection: Boolean = false,
    private val cancellationBarrier: CompletableDeferred<Unit>? = null,
) : SessionTransport {

    /** Every frame the session wrote, in order. */
    val sent = mutableListOf<String>()

    /** Every socket this transport opened. A command must never add a second one. */
    val sockets = mutableListOf<ScriptedSessionConnection>()

    /** Every television this transport was asked to reach, in order. */
    val connects = mutableListOf<ConfirmedTelevision>()

    /**
     * The URL of every connection attempt that got as far as opening a socket, in order. A token
     * appears here only when the saved identity matched and the channel is TLS — the exact property
     * `identityMismatchDoesNotSendToken` asserts on.
     */
    val attemptedUrls = mutableListOf<String>()

    /** Whether each entry of [attemptedUrls] carried the saved token. */
    val attemptedTokens = mutableListOf<Boolean>()

    /** How many attempts the scripted television refused on the saved identity check. */
    var identityMismatches: Int = 0
        private set

    override suspend fun connect(
        television: ConfirmedTelevision,
        saved: PairingSecret?,
    ): ConnectionAttempt {
        connects += television
        val requiredPin = saved?.pin?.takeIf { television.tls }
        if (requiredPin != null) {
            val presented = presentedPin ?: certificateIdentity
            if (presented != requiredPin) {
                identityMismatches++
                return ConnectionAttempt.IdentityMismatch
            }
        }
        if (connectDelayMs > 0) delay(connectDelayMs)
        if (refusesConnection) return ConnectionAttempt.Unreachable
        // The token rides the URL only after the saved pin matched, and never on the plaintext
        // channel — the same discipline [OkHttpSessionTransport] enforces on the wire.
        val token = saved?.token?.takeIf { requiredPin != null }
        attemptedUrls += RemoteChannel.remoteUrl(television, token)
        attemptedTokens += token != null
        // A retry opens a fresh connection, and a television that refused to answer the first time
        // is free to answer the second. The last script is reused once the list runs out, so the
        // single-fixture case replays the same television behaviour on every attempt.
        val fixture = fixtures.getOrElse(sockets.size) { fixtures.last() }
        val connection =
            ScriptedSessionConnection(fixture, certificateIdentity, sent, cancellationBarrier)
        sockets += connection
        return ConnectionAttempt.Opened(connection)
    }
}

internal class ScriptedSessionConnection(
    private val fixture: SessionFixture,
    override val certificateIdentity: String?,
    private val sent: MutableList<String>,
    private val cancellationBarrier: CompletableDeferred<Unit>?,
) : SessionConnection {

    var closed = false
        private set

    override val frames: Flow<String> = flow {
        try {
            var now = 0L
            for (event in fixture.inbound) {
                delay(event.atMs - now)
                now = event.atMs
                when (event) {
                    is SessionEvent.Frame -> emit(event.body)
                    is SessionEvent.Close -> return@flow
                }
            }
            // The script is exhausted and the television has not ended the session: the socket
            // stays
            // open, as a live one does, until the holder releases it.
            awaitCancellation()
        } finally {
            // Tests can hold cancellation cleanup open to exercise races between an expired
            // approval attempt and retryApproval().
            cancellationBarrier?.let { barrier -> withContext(NonCancellable) { barrier.await() } }
        }
    }

    override suspend fun send(frame: String): Boolean {
        sent += frame
        return true
    }

    override fun close() {
        closed = true
    }
}
