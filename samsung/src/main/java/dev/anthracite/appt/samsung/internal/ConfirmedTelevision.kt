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
 * S04 extends the record with what durable pairing needs and nothing else: the protocol UUID (the
 * plaintext identity check compares it against the saved one) and the candidate display name the
 * samsung-private device record retains. Both stay internal; no caller-visible type carries them.
 *
 * The in-memory record is the live evidence from the most recent scan or the samsung-private device
 * record from a previous process (data.md#samsung-private-device-record), so `open` works before
 * any scan of this process has run.
 *
 * @property host the address the television answered device-info on. Internal only.
 * @property tls true when the television demonstrates the TLS remote channel (port 8002), either by
 *   speaking it or by requiring token auth. Plaintext (port 8001) is the adopted fallback only.
 * @property adoptedChannel false when identity confirmed a Samsung television with no adopted
 *   control path. Such a television is opened as `Unsupported` and receives no command.
 * @property uuid the television's normalized protocol UUID, or null when it exposes none. Never
 *   caller-visible; the plaintext identity check and the device record are the only readers.
 * @property displayName the candidate display name from device-info, scrubbed, or null. Only the
 *   samsung-private device record retains it.
 */
internal data class ConfirmedTelevision(
    val id: TvId,
    val host: String,
    val tls: Boolean,
    val adoptedChannel: Boolean,
    val uuid: String? = null,
    val displayName: String? = null,
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
 * type. After a process death the in-memory record is gone, and `open` rebuilds the evidence from
 * the television's samsung-private device record (data.md#samsung-private-device-record) instead.
 */
internal class ConfirmedTelevisions {
    private val confirmed = java.util.concurrent.ConcurrentHashMap<TvId, ConfirmedTelevision>()

    fun record(television: ConfirmedTelevision) {
        confirmed[television.id] = television
    }

    fun latest(id: TvId): ConfirmedTelevision? = confirmed[id]
}
