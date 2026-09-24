package dev.anthracite.appt.samsung.internal

import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.net.InetAddress
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * `NsdManager` browse for `_airplay._tcp`, keeping only services whose TXT record names
 * manufacturer Samsung (docs/architecture/discovery.md#probes). The browse runs only while the
 * flow is collected, i.e. for one scan, and is stopped when collection ends.
 */
internal class NsdAirPlayBrowser(private val nsd: NsdManager, private val network: Network) {

    fun samsungHosts(): Flow<Pair<InetAddress, Probe>> = callbackFlow {
        val found = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listener = DiscoveryCallbacks(onFound = { found.trySend(it) })
        start(listener)
        launch {
            // Resolved one at a time: before API 34 the platform rejects concurrent resolves.
            for (service in found) {
                val resolved = resolve(service) ?: continue
                val host = hostOf(resolved)
                if (host != null && AirPlayTxt.identifiesSamsung(resolved.attributes)) {
                    send(host to Probe.AirPlaySamsung)
                }
            }
        }
        awaitClose {
            found.close()
            stop(listener)
        }
    }

    private fun start(listener: NsdManager.DiscoveryListener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            nsd.discoverServices(
                AirPlayTxt.SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                network,
                Runnable::run,
                listener,
            )
        } else {
            // Before API 33 the browse cannot be pinned to a network; it runs on the default one,
            // which is the Wi-Fi/Ethernet network activeLan() already required.
            nsd.discoverServices(AirPlayTxt.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun stop(listener: NsdManager.DiscoveryListener) {
        try {
            nsd.stopServiceDiscovery(listener)
        } catch (ignored: IllegalArgumentException) {
            // The browse never started (onStartDiscoveryFailed); there is nothing to stop.
        }
    }

    /**
     * `resolveService` is deprecated from API 34 in favour of `registerServiceInfoCallback`, but it
     * remains functional there and is the only resolve API across minSdk 29..33, so one code path
     * serves every supported level.
     */
    @Suppress("DEPRECATION")
    private suspend fun resolve(service: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            nsd.resolveService(
                service,
                object : NsdManager.ResolveListener {
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        continuation.resume(serviceInfo)
                    }

                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        continuation.resume(null)
                    }
                },
            )
        }

    /** `NsdServiceInfo.host` is deprecated from API 34, where `hostAddresses` replaces it. */
    @Suppress("DEPRECATION")
    private fun hostOf(service: NsdServiceInfo): InetAddress? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            service.hostAddresses.firstOrNull(LanPolicy::isLanAddress)
        } else {
            service.host
        }

    private class DiscoveryCallbacks(private val onFound: (NsdServiceInfo) -> Unit) :
        NsdManager.DiscoveryListener {
        override fun onServiceFound(serviceInfo: NsdServiceInfo) = onFound(serviceInfo)

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            // A lost service needs no action: cards are only emitted after confirmation.
        }

        override fun onDiscoveryStarted(serviceType: String) {
            // Nothing to do; results arrive through onServiceFound.
        }

        override fun onDiscoveryStopped(serviceType: String) {
            // Stopped by awaitClose at the end of the scan.
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            // The AirPlay probe is one of three; SSDP still runs, so the scan continues.
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            // Nothing further to release.
        }
    }
}
