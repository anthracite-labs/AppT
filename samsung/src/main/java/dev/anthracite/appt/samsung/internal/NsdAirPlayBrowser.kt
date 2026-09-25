package dev.anthracite.appt.samsung.internal

import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.net.InetAddress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * `NsdManager` browse for `_airplay._tcp`, keeping only services whose TXT record names
 * manufacturer Samsung (docs/architecture/discovery.md#probes). The browse runs only while the flow
 * is collected, i.e. for one scan, and is stopped when collection ends.
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
     * Resolves one service, bounded by [RESOLVE_TIMEOUT] so a resolve that never calls back cannot
     * hold up the services queued behind it for the rest of the scan.
     *
     * When the wait ends without an outcome (timeout, or the scan ending), the pending resolution
     * is withdrawn with `stopServiceResolution` on API 34+, so the listener is not left registered
     * and an immediate Scan again does not find the resolver busy. Before API 34 there is no way to
     * withdraw a resolve; the bound still keeps the queue moving, and a resolver that is still busy
     * can only fail the next resolve (`FAILURE_ALREADY_ACTIVE`), which then yields no candidate.
     *
     * `resolveService` is deprecated from API 34 in favour of `registerServiceInfoCallback`, but it
     * remains functional there and is the only resolve API across minSdk 29..33, so one code path
     * serves every supported level.
     */
    @Suppress("DEPRECATION")
    private suspend fun resolve(service: NsdServiceInfo): NsdServiceInfo? =
        awaitCallback<NsdServiceInfo>(RESOLVE_TIMEOUT) { deliver ->
            val listener =
                object : NsdManager.ResolveListener {
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) =
                        deliver(serviceInfo)

                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) =
                        deliver(null)
                }
            nsd.resolveService(service, listener)
            val withdraw: () -> Unit = { stopResolution(listener) }
            withdraw
        }

    private fun stopResolution(listener: NsdManager.ResolveListener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                nsd.stopServiceResolution(listener)
            } catch (ignored: IllegalArgumentException) {
                // The resolution already finished; there is nothing left to withdraw.
            }
        }
    }

    /** `NsdServiceInfo.host` is deprecated from API 34, where `hostAddresses` replaces it. */
    @Suppress("DEPRECATION")
    private fun hostOf(service: NsdServiceInfo): InetAddress? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            service.hostAddresses.firstOrNull(LanPolicy::isLanAddress)
        } else {
            service.host
        }

    private companion object {
        /** Per-resolve bound: a small fraction of the 10-second scan, so several services fit. */
        val RESOLVE_TIMEOUT: Duration = 2.seconds
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
