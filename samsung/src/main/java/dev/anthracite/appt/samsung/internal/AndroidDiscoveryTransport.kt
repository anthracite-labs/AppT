package dev.anthracite.appt.samsung.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.wifi.WifiManager
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge

/**
 * The production [DiscoveryTransport]: thin Android glue around the pure, tested pieces
 * ([LanPolicy], [SsdpClient], [Ssdp], [AirPlayTxt], [DeviceInfoHttp]).
 *
 * Nothing here runs outside a scan: no background work, no service, no resident listener.
 */
internal class AndroidDiscoveryTransport(context: Context) : DiscoveryTransport {
    private val connectivity =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val nsd = context.applicationContext.getSystemService(NsdManager::class.java)
    private val ssdp = SsdpClient(SsdpClient.multicastTarget())
    private val deviceInfoHttp = DeviceInfoHttp()

    override fun activeLan(): Lan? {
        val network = connectivity.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        val kind =
            LanPolicy.classify(
                isVpn =
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
                hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
            )
        return kind?.let { AndroidLan(network, requiresMulticastLock = it == LanPolicy.Kind.WiFi) }
    }

    override fun holdMulticastLock(): AutoCloseable {
        val lock = wifi.createMulticastLock(MULTICAST_LOCK_TAG).apply { setReferenceCounted(false) }
        lock.acquire()
        return AutoCloseable { if (lock.isHeld) lock.release() }
    }

    override fun candidates(lan: Lan): Flow<Candidate> {
        val network = (lan as AndroidLan).network
        val ssdpCandidates = ssdp.replies(lan)
        val airPlayCandidates = NsdAirPlayBrowser(nsd, network).samsungHosts()
        return merge(ssdpCandidates, airPlayCandidates).mapNotNull { (address, probe) ->
            address.takeIf(LanPolicy::isLanAddress)?.hostAddress?.let { Candidate(it, probe) }
        }
    }

    override suspend fun deviceInfo(lan: Lan, host: String, port: Int): String? {
        // `host` is always the numeric address a probe reported (see candidates), so this parses
        // the literal and performs no name lookup.
        val address =
            try {
                InetAddress.getByName(host)
            } catch (ignored: UnknownHostException) {
                null
            }
        return address?.takeIf(LanPolicy::isLanAddress)?.let { deviceInfoHttp.get(lan, it, port) }
    }

    private class AndroidLan(val network: Network, override val requiresMulticastLock: Boolean) :
        Lan {
        override fun bind(socket: DatagramSocket) = network.bindSocket(socket)

        override fun bind(socket: Socket) = network.bindSocket(socket)
    }

    private companion object {
        const val MULTICAST_LOCK_TAG = "AppT discovery"
    }
}
