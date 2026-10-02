package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.DiscoveryEvent
import dev.anthracite.appt.samsung.TvFailure
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The network-route contract for discovery and reconnect (lifecycle.md, discovery.md,
 * security.md#B7): V1 binds to the active non-VPN Wi-Fi or Ethernet network, and a VPN is neither
 * used nor bypassed. There is no route that reaches a television through a VPN: with a VPN-only
 * route the scan has no usable LAN and reports the single failure the gate already understands.
 *
 * The fixture set is never even probed here, so the test also proves that nothing is sent on that
 * route: `FixtureTransport` records every outbound device-info request and every lock acquisition.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VpnRouteTest {

    private fun productionSources(): List<File> {
        val root = File("src/main")
        assertTrue(
            "expected to run from the samsung module, ran from ${root.absolutePath}",
            root.isDirectory,
        )
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun appSources(): List<File> {
        val root = File("../app/src/main")
        assertTrue(
            "expected to run from the app module, ran from ${root.absolutePath}",
            root.isDirectory,
        )
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * Every classify() input that contains a VPN transport is unusable, whatever else it reports.
     */
    @Test
    fun vpnDoesNotBypass() = runTest {
        listOf(true, false).forEach { wifi ->
            listOf(true, false).forEach { ethernet ->
                assertEquals(
                    "a VPN route is never a usable LAN (wifi=$wifi ethernet=$ethernet)",
                    null,
                    LanPolicy.classify(isVpn = true, hasWifi = wifi, hasEthernet = ethernet),
                )
            }
        }
    }

    /** A VPN-only route yields `Unreachable`, and nothing is probed on the way there. */
    @Test
    fun aVpnOnlyRouteIsUnreachableAndProbesNothing() = runTest {
        val transport = FixtureTransport(Fixture.load("ssdp-tizen-tv"), lan = null)
        val events = mutableListOf<DiscoveryEvent>()

        DiscoveryScan(
                transport,
                ConfirmedTelevisions(),
                InMemorySamsungStore(),
                readDispatcher = Dispatchers.Unconfined,
            )
            .run { events += it }

        assertEquals(listOf(DiscoveryEvent.Failed(TvFailure.Unreachable)), events)
        assertEquals(
            "no device-info request is sent over a VPN",
            emptyList<Any>(),
            transport.outbound,
        )
        assertEquals("no multicast lock is taken", emptyList<String>(), transport.lockLog)
    }

    /** No production source mentions an API that could route traffic around the LAN. */
    @Test
    fun noVpnBypassApiExistsInProductionSources() {
        val bypassApis =
            Regex(
                """\b(bindProcessToNetwork|setUnderlyingNetworks|VpnService|VpnManager|addTransportType)\b"""
            )
        val offenders =
            (productionSources() + appSources()).flatMap { source ->
                source
                    .readLines()
                    .mapIndexed { index, line -> "${source.path}:${index + 1}" to line }
                    .filter { (_, line) -> bypassApis.containsMatchIn(line) }
                    .map { (at, _) -> at }
            }
        assertEquals(emptyList<String>(), offenders)
    }
}
