package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.TvFailure
import java.io.IOException
import java.net.DatagramSocket
import java.net.Socket
import kotlinx.coroutines.flow.Flow

/**
 * The internal discovery seam: everything that touches the platform network stack.
 *
 * [DiscoveryScan] owns the policy (bound, lock lifetime, confirmation, identity, dedup) and talks
 * only to this interface, so the policy is exercised on the JVM against recorded fixtures. The
 * production adapter is [AndroidDiscoveryTransport]; the test adapter replays
 * `samsung/src/test/resources/samsung/fixtures/<case-id>/trace.jsonl`.
 *
 * This is a test detail of the module (samsung-interface.md: "Internal transports are a test detail
 * of the `samsung` module"), not a caller-facing interface.
 */
internal interface DiscoveryTransport {
    /** The active non-VPN Wi-Fi or Ethernet network, or null when none is up. */
    fun activeLan(): Lan?

    /** Acquires the Wi-Fi multicast lock for one scan. Closing the handle releases it. */
    fun holdMulticastLock(): AutoCloseable

    /**
     * Client-side probes on [lan]. Emits hosts whose probe response already identifies a Samsung
     * device; every other response is dropped inside the probe. Throws [ScanAbort] when the probes
     * cannot run at all.
     */
    fun candidates(lan: Lan): Flow<Candidate>

    /**
     * Reads the device-info document from [host] on [port], bounded to [MAX_DEVICE_INFO_BYTES].
     * Returns null when it cannot be read; the caller then emits no card.
     */
    suspend fun deviceInfo(lan: Lan, host: String, port: Int): String?
}

/** The network one scan is bound to. */
internal interface Lan {
    /** True on Wi-Fi, where the multicast lock is required for SSDP responses to arrive. */
    val requiresMulticastLock: Boolean

    fun bind(socket: DatagramSocket)

    fun bind(socket: Socket)
}

/** Which probe produced a candidate. Recorded for tests; never shown to callers. */
internal enum class Probe {
    RemoteControlReceiver,
    DialSamsung,
    AirPlaySamsung,
}

/**
 * A response that identified a Samsung device. A candidate is not a card: it must be confirmed by
 * device-info first.
 *
 * @property host the responding host, as the probe saw it. Internal only; it never reaches a
 *   caller-facing type.
 */
internal data class Candidate(val host: String, val probe: Probe)

/** The probes could not run; the scan ends with [failure], emitted once. */
internal class ScanAbort(val failure: TvFailure, cause: Throwable? = null) :
    IOException("discovery probes could not run", cause)

/** protocol.md#limits: device-info body. */
internal const val MAX_DEVICE_INFO_BYTES: Int = 64 * 1024
