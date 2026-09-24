package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.TvFailure
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Locale

/**
 * Pure network policy for discovery (docs/architecture/discovery.md#what-a-scan-is).
 *
 * Kept free of Android types so each rule is a plain unit test.
 */
internal object LanPolicy {

    /** How a usable network should be treated for one scan, or null when it is not usable. */
    enum class Kind {
        WiFi,
        Ethernet,
    }

    /**
     * V1 binds discovery to the active non-VPN Wi-Fi or Ethernet network. A VPN is never used and
     * never bypassed: when the active network is a VPN, there is no usable LAN.
     */
    fun classify(isVpn: Boolean, hasWifi: Boolean, hasEthernet: Boolean): Kind? =
        when {
            isVpn -> null
            hasWifi -> Kind.WiFi
            hasEthernet -> Kind.Ethernet
            else -> null
        }

    /**
     * A candidate must be an IPv4 address on the local network. Anything else (a public address
     * advertised by a misbehaving responder, IPv6, loopback) is dropped before any connection is
     * opened, so device-info is only ever fetched from the home network.
     */
    fun isLanAddress(address: InetAddress): Boolean =
        address is Inet4Address && (address.isSiteLocalAddress || address.isLinkLocalAddress)

    /**
     * Maps a probe failure onto the single failure the scan emits.
     *
     * Android reports a blocked local-network socket (for example, Android 16 local-network
     * protection on an opted-in device) as a [SecurityException] or as a socket error carrying
     * `EPERM`/`EACCES`. That is [TvFailure.LocalNetworkDenied]: the gate returns to the
     * explanation and nothing retries in a loop. Any other failure to use the network is
     * [TvFailure.Unreachable].
     */
    fun failureFor(error: Exception): TvFailure =
        when {
            error is SecurityException -> TvFailure.LocalNetworkDenied
            error is IOException && error.mentionsPermissionDenial() ->
                TvFailure.LocalNetworkDenied
            else -> TvFailure.Unreachable
        }

    private fun Throwable.mentionsPermissionDenial(): Boolean {
        val messages = generateSequence(this) { it.cause }.mapNotNull { it.message }
        return messages.any { message ->
            val upper = message.uppercase(Locale.ROOT)
            DENIAL_MARKERS.any { it in upper }
        }
    }

    private val DENIAL_MARKERS =
        listOf("EPERM", "EACCES", "OPERATION NOT PERMITTED", "PERMISSION DENIED")
}
