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

    /**
     * discovery.md: scans and internal rediscovery share one lock owner inside the module. The lock
     * is one process-wide instance held until the last holder releases it, so a rediscovery that
     * overlaps a scan can never release the lock the scan is still using, and reference counting
     * stays off because only this holder ever touches the lock.
     */
    override fun holdMulticastLock(): AutoCloseable = MulticastLock.acquire(wifi)

    override fun candidates(lan: Lan): Flow<Candidate> {
        val network = (lan as AndroidLan).network
        val ssdpCandidates = ssdp.replies(lan)
        val airPlayCandidates = NsdAirPlayBrowser(nsd, network).samsungHosts()
        return merge(ssdpCandidates, airPlayCandidates).mapNotNull { (address, probe) ->
            address.takeIf(LanPolicy::isLanAddress)?.hostAddress?.let { Candidate(it, probe) }
        }
    }

    override suspend fun deviceInfo(lan: Lan, host: String, port: Int): String? {
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

    /**
     * The single multicast-lock owner. `AndroidDiscoveryTransport` instances are created per scan
     * and per rediscovery, so the lock cannot live on an instance.
     */
    private object MulticastLock {
        private var held: WifiManager.MulticastLock? = null
        private var holders = 0

        @Synchronized
        fun acquire(wifi: WifiManager): AutoCloseable {
            if (holders == 0) {
                held =
                    wifi.createMulticastLock(MULTICAST_LOCK_TAG).apply {
                        setReferenceCounted(false)
                        acquire()
                    }
            }
            holders++
            return AutoCloseable { release() }
        }

        @Synchronized
        private fun release() {
            holders = (holders - 1).coerceAtLeast(0)
            if (holders == 0) {
                held?.takeIf { it.isHeld }?.release()
                held = null
            }
        }
    }

    private companion object {
        const val MULTICAST_LOCK_TAG = "AppT discovery"
    }
}
