package dev.anthracite.appt.samsung.internal

import kotlinx.coroutines.flow.Flow

/**
 * The internal session-transport seam (docs/architecture/modules.md#internal-seams-inside-samsung).
 *
 * Production uses [ProductionSessionTransport]: OkHttp WebSocket and TLS on port 8002, and a
 * bounded raw-socket WebSocket on plaintext port 8001. The scripted test adapter replays fixture
 * frames, delays, closes and generated certificate identities. Both adapters exist, so this is a
 * real seam rather than an implementation detail.
 *
 * This is a test detail of the `samsung` module, not a caller-facing interface: `app` never sees
 * it, and the wire format, ports, key strings, URL and TLS mechanics stay behind it
 * (docs/architecture/samsung-interface.md).
 */
internal interface SessionTransport {
    /**
     * Opens the live socket to [television] under the saved pairing [saved], or without one for
     * first contact.
     *
     * Identity is checked before anything credential-shaped is placed on the wire
     * (docs/architecture/connection.md#ordering-relative-to-secrets): a saved TLS pin is compared
     * during the handshake, and the token is attached to the remote-channel URL only after that
     * comparison has passed. A mismatch therefore reaches the caller as
     * [ConnectionAttempt.IdentityMismatch] with no token sent, written, or staged anywhere.
     *
     * Containment is the adapter's job: nothing about a socket, TLS or parser failure crosses the
     * seam as a thrown exception.
     */
    suspend fun connect(television: ConfirmedTelevision, saved: PairingSecret?): ConnectionAttempt
}

/**
 * The outcome of one connection attempt.
 *
 * The states are deliberately distinct because the session machine treats them differently:
 * [Opened] continues into the frame exchange, [IdentityMismatch] is the fail-closed
 * `NeedsRepair(IdentityChanged)` path on which no token was transmitted, and [Unreachable] is the
 * bounded-recovery path.
 */
internal sealed interface ConnectionAttempt {
    /** The socket is open and the identity, where one was saved, matched. */
    data class Opened(val connection: SessionConnection) : ConnectionAttempt

    /**
     * The television presented a different security identity than the saved one. The connection
     * never completed, and no token was attached to any URL or frame on this attempt.
     */
    data object IdentityMismatch : ConnectionAttempt

    /** The socket could not be opened within the bound. */
    data object Unreachable : ConnectionAttempt
}

/** One open socket. Held by the session for the lifetime of one connection attempt. */
internal interface SessionConnection {

    /**
     * Inbound frames, in arrival order, each already bounded by the adapter. The flow completes
     * when the socket ends. Malformed content is contained by the parser, not by the caller.
     */
    val frames: Flow<String>

    /**
     * The SHA-256 of the SubjectPublicKeyInfo the television presented, or null on the plaintext
     * channel. On first contact this is the candidate pin: it stays in memory for the session and
     * is persisted only together with a successful approval. On a resumed connection the saved pin
     * was already verified before the connection opened, and this value matches it.
     */
    val certificateIdentity: String?

    /**
     * Writes one already-encoded frame. Returns false when the frame could not be written,
     * including when the unsent-frame cap is already reached, so the caller reports
     * [dev.anthracite.appt.samsung.CommandResult.Rejected] instead of growing a burst.
     */
    suspend fun send(frame: String): Boolean

    /** Releases the socket. */
    fun close()
}
