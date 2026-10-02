package dev.anthracite.appt.samsung.internal

import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.net.InetAddress
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
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
        // A service the platform reports as lost before it is resolved is no longer a candidate:
        // the next scan re-discovers whatever is still advertised.
        val lost = Collections.synchronizedSet(mutableSetOf<String>())
        // The platform decided the browse is over: there is nothing to stop and no more to queue.
        val ended = AtomicBoolean(false)
        val listener =
            DiscoveryCallbacks(
                onFound = { found.trySend(it) },
                onServiceGone = { service -> service.serviceName?.let { lost += it } },
                onEnded = {
                    ended.set(true)
                    found.close()
                },
            )
        start(listener)
        launch {
            for (service in found) {
                // A service the platform has already withdrawn is not a candidate any more, and a
                // service that never resolves contributes nothing to this scan.
                val candidate = if (lost.contains(service.serviceName)) null else resolve(service)
                if (candidate != null && AirPlayTxt.identifiesSamsung(candidate.attributes)) {
                    val host = hostOf(candidate)
                    if (host != null) send(host to Probe.AirPlaySamsung)
                }
            }
        }
        awaitClose {
            found.close()
            if (!ended.get()) stop(listener)
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
            nsd.discoverServices(AirPlayTxt.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun stop(listener: NsdManager.DiscoveryListener) {
        try {
            nsd.stopServiceDiscovery(listener)
        } catch (expectedStopRace: IllegalArgumentException) {}
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
            } catch (ignored: IllegalArgumentException) {}
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

    /**
     * The platform's view of one browse. Every callback carries real information: a found service
     * is queued for resolution, a lost one is withdrawn from that queue, and every way the browse
     * can end (stopped, or failed to start or stop) ends the queue so the scan never waits on a
     * browse the platform is no longer running.
     */
    private class DiscoveryCallbacks(
        private val onFound: (NsdServiceInfo) -> Unit,
        private val onServiceGone: (NsdServiceInfo) -> Unit,
        private val onEnded: () -> Unit,
    ) : NsdManager.DiscoveryListener {
        override fun onServiceFound(serviceInfo: NsdServiceInfo) = onFound(serviceInfo)

        override fun onServiceLost(serviceInfo: NsdServiceInfo) = onServiceGone(serviceInfo)

        /** The browse is confirmed running; candidates then arrive through [onServiceFound]. */
        override fun onDiscoveryStarted(serviceType: String) = Unit

        override fun onDiscoveryStopped(serviceType: String) = onEnded()

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = onEnded()

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = onEnded()
    }
}
