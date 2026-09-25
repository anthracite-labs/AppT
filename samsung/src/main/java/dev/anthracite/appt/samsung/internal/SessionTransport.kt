package dev.anthracite.appt.samsung.internal

import kotlinx.coroutines.flow.Flow

/**
 * The internal session-transport seam (docs/architecture/modules.md#internal-seams-inside-samsung).
 *
 * Production uses [OkHttpSessionTransport] (OkHttp WebSocket and TLS); the scripted test adapter
 * replays fixture frames, delays, closes and generated certificate identities. Both adapters exist,
 * so this is a real seam rather than an implementation detail.
 *
 * This is a test detail of the `samsung` module, not a caller-facing interface: `app` never sees
 * it, and the wire format, ports, key strings, URL and TLS mechanics stay behind it
 * (docs/architecture/samsung-interface.md).
 */
internal interface SessionTransport {
    /**
     * Opens the live socket to [television].
     *
     * Returns null when the socket cannot be opened at all; the caller then reports the television
     * as unreachable. Containment is the adapter's job: nothing about a socket, TLS or parser
     * failure crosses the seam as a thrown exception.
     */
    suspend fun connect(television: ConfirmedTelevision): SessionConnection?
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
     * is discarded on close. Persisting it with the token is S04.
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
