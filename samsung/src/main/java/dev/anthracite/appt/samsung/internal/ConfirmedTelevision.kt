package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.TvId

/**
 * The most recent confirmed control evidence for one television, private to this module
 * (docs/architecture/protocol.md#device-info-handling, connection.md).
 *
 * S02 confirms a television with device-info but deliberately does not expose its address to `app`.
 * S03 keeps that privacy: this record is how `open(tvId)` reaches the selected television without
 * an address, host, port, MAC, protocol generation, or raw device-info field ever crossing the
 * seam.
 *
 * The record is in-memory only. Durable Samsung-private reconnect and pairing storage is S04+; S03
 * needs the evidence only for the immediate session.
 *
 * @property host the address the television answered device-info on. Internal only.
 * @property tls true when the television demonstrates the TLS remote channel (port 8002), either by
 *   speaking it or by requiring token auth. Plaintext (port 8001) is the adopted fallback only.
 * @property adoptedChannel false when identity confirmed a Samsung television with no adopted
 *   control path. Such a television is opened as `Unsupported` and receives no command.
 */
internal data class ConfirmedTelevision(
    val id: TvId,
    val host: String,
    val tls: Boolean,
    val adoptedChannel: Boolean,
) {
    /** The remote-channel port for this television's adopted channel. Internal only. */
    val remotePort: Int
        get() = if (tls) TLS_REMOTE_PORT else PLAINTEXT_REMOTE_PORT

    companion object {
        /** protocol.md#endpoints: the adopted TLS remote channel. */
        const val TLS_REMOTE_PORT = 8002

        /** protocol.md#endpoints: the adopted plaintext fallback. */
        const val PLAINTEXT_REMOTE_PORT = 8001
    }
}

/**
 * The private, in-memory record of the most recent confirmed television per [TvId].
 *
 * Discovery writes it when a candidate is confirmed; `open` reads it. It holds no caller-visible
 * type and no durable state, so a process death simply means the next `discover()` re-confirms.
 */
internal class ConfirmedTelevisions {
    private val confirmed = java.util.concurrent.ConcurrentHashMap<TvId, ConfirmedTelevision>()

    fun record(television: ConfirmedTelevision) {
        confirmed[television.id] = television
    }

    fun latest(id: TvId): ConfirmedTelevision? = confirmed[id]
}
