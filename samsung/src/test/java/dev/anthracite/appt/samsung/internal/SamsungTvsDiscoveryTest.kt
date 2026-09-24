package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.ControlAvailability
import dev.anthracite.appt.samsung.DiscoveredTv
import dev.anthracite.appt.samsung.DiscoveryEvent
import dev.anthracite.appt.samsung.SamsungTvs
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `SamsungTvs.discover()` behaviour, driven through the production adapter ([SamsungTvsImpl] and
 * [DiscoveryScan]) against recorded fixtures. Time is virtual, so the 10-second bound is real.
 *
 * Scans are launched as ordinary test coroutines, not in `backgroundScope`: `advanceUntilIdle()`
 * stops once only background work remains, so a background scan would never run to its bound.
 * Every scan here ends by itself (bound, failure) or is cancelled by the test, and `runTest`
 * additionally fails if one were left running.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SamsungTvsDiscoveryTest {

    private var minted = 0

    private fun tvs(transport: FixtureTransport): SamsungTvs = SamsungTvsImpl {
        DiscoveryScan(transport, mintId = { "local-test-${++minted}" })
    }

    /** Runs one full scan of [caseId] to completion and returns its events. */
    private fun TestScope.scan(transport: FixtureTransport): List<DiscoveryEvent> {
        val events = mutableListOf<DiscoveryEvent>()
        launch { tvs(transport).discover().toList(events) }
        advanceUntilIdle()
        return events
    }

    private fun List<DiscoveryEvent>.found(): List<DiscoveredTv> =
        filterIsInstance<DiscoveryEvent.Found>().map { it.tv }

    @Test
    fun discoveryEndsAtBound() = runTest {
        val transport = FixtureTransport(Fixture.load("late-tv"))
        val events = mutableListOf<DiscoveryEvent>()
        var finishedAt = -1L
        var lockHeldAtFinish = true
        launch {
            tvs(transport)
                .discover()
                .onEach {
                    if (it == DiscoveryEvent.Finished) {
                        finishedAt = currentTime
                        lockHeldAtFinish = transport.lockHeld
                    }
                }
                .toList(events)
        }

        advanceTimeBy(9_999)
        runCurrent()
        assertEquals("the late television confirmed before the bound", 1, events.found().size)
        assertFalse("still scanning just before the bound", DiscoveryEvent.Finished in events)

        advanceUntilIdle()
        assertEquals(10_000L, finishedAt)
        assertEquals(DiscoveryEvent.Finished, events.last())
        assertEquals(listOf("Late TV"), events.found().map { it.name })
        assertFalse("multicast lock released before Finished", lockHeldAtFinish)
        assertEquals(listOf(FixtureTransport.ACQUIRE, FixtureTransport.RELEASE), transport.lockLog)
        assertEquals("probes stopped at the bound", 0, transport.probesRunning)
        assertFalse(
            "a host that answered after the bound is never contacted",
            transport.outbound.any { it.host == "[host-c]" },
        )
    }

    @Test
    fun secondDiscoverCancelsFirst() = runTest {
        val transport = FixtureTransport(Fixture.load("ssdp-tizen-tv"))
        val tvs = tvs(transport)
        val first = mutableListOf<DiscoveryEvent>()
        val second = mutableListOf<DiscoveryEvent>()

        val firstJob = launch { tvs.discover().toList(first) }
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, first.found().size)

        val secondJob = launch { tvs.discover().toList(second) }
        runCurrent()
        assertTrue("the previous scan is cancelled", firstJob.isCancelled)
        assertTrue(
            "a cancelled scan neither fails nor finishes",
            first.none { it is DiscoveryEvent.Failed || it == DiscoveryEvent.Finished },
        )
        // The first scan released its lock before the second acquired one: one lock owner.
        assertEquals(
            listOf(FixtureTransport.ACQUIRE, FixtureTransport.RELEASE, FixtureTransport.ACQUIRE),
            transport.lockLog,
        )

        advanceUntilIdle()
        assertTrue(secondJob.isCompleted && !secondJob.isCancelled)
        assertEquals(DiscoveryEvent.Finished, second.last())
        assertEquals(1, second.found().size)
        assertFalse(transport.lockHeld)
    }

    @Test
    fun cancellingCollectionStopsProbesAndReleasesTheLock() = runTest {
        val transport = FixtureTransport(Fixture.load("late-tv"))
        val events = mutableListOf<DiscoveryEvent>()
        val job = launch { tvs(transport).discover().toList(events) }
        advanceTimeBy(9_950)
        runCurrent()
        assertEquals(1, transport.probesRunning)
        assertTrue(transport.lockHeld)

        job.cancel()
        runCurrent()
        assertEquals(0, transport.probesRunning)
        assertFalse(transport.lockHeld)
        assertTrue(events.none { it == DiscoveryEvent.Finished || it is DiscoveryEvent.Failed })
    }

    @Test
    fun blockedLocalNetworkFailsOnceAndReleasesTheLock() = runTest {
        val transport =
            FixtureTransport(
                Fixture.load("ssdp-tizen-tv"),
                abort = ScanAbort(TvFailure.LocalNetworkDenied),
                abortAtMs = 10,
            )
        val events = scan(transport)
        assertEquals(listOf(DiscoveryEvent.Failed(TvFailure.LocalNetworkDenied)), events)
        assertFalse(transport.lockHeld)
        assertEquals(0, transport.probesRunning)
        assertTrue("the failed scan contacted no host", transport.outbound.isEmpty())
    }

    @Test
    fun noUsableNetworkFailsOnceWithoutTakingTheLock() = runTest {
        val transport = FixtureTransport(Fixture.load("ssdp-tizen-tv"), lan = null)
        assertEquals(listOf(DiscoveryEvent.Failed(TvFailure.Unreachable)), scan(transport))
        assertTrue(transport.lockLog.isEmpty())
        assertTrue(transport.outbound.isEmpty())
    }

    @Test
    fun ethernetScanDoesNotTakeTheMulticastLock() = runTest {
        val transport =
            FixtureTransport(
                Fixture.load("ssdp-tizen-tv"),
                lan = TestLan(requiresMulticastLock = false),
            )
        val events = scan(transport)
        assertEquals(DiscoveryEvent.Finished, events.last())
        assertTrue(transport.lockLog.isEmpty())
    }

    @Test
    fun confirmedTelevisionBecomesOneFriendlyCard() = runTest {
        val transport = FixtureTransport(Fixture.load("ssdp-tizen-tv"))
        val events = scan(transport)
        assertEquals(
            listOf(
                DiscoveryEvent.Found(
                    DiscoveredTv(
                        id = TvId("3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e"),
                        name = "Living Room TV",
                        remembered = false,
                        stableIdentity = true,
                        availability = ControlAvailability.NeedsPairing,
                    )
                ),
                DiscoveryEvent.Finished,
            ),
            events,
        )
        // The RemoteControlReceiver and DIAL replies came from one host: one device-info read,
        // on port 8001 of that host only.
        assertEquals(listOf(FixtureTransport.Request("[host-a]", 8001)), transport.outbound)
    }

    @Test
    fun dialRepliesAreKeptOnlyWhenTheyIdentifySamsung() = runTest {
        val transport = FixtureTransport(Fixture.load("dial-filter"))
        val events = scan(transport)
        assertEquals(listOf("Bedroom TV"), events.found().map { it.name })
        assertEquals(listOf(FixtureTransport.Request("[host-b]", 8001)), transport.outbound)
    }

    @Test
    fun samsungAirPlayTelevisionIsConfirmed() = runTest {
        val events = scan(FixtureTransport(Fixture.load("airplay-samsung-tv")))
        assertEquals(listOf("Kitchen TV"), events.found().map { it.name })
    }

    @Test
    fun soundbarProducesNoCard() = runTest {
        val transport = FixtureTransport(Fixture.load("soundbar-airplay"))
        val events = scan(transport)
        assertEquals(listOf(DiscoveryEvent.Finished), events)
        // The non-Samsung AirPlay speaker is never contacted.
        assertEquals(listOf(FixtureTransport.Request("[host-b]", 8001)), transport.outbound)
    }

    @Test
    fun bluRayPlayerProducesNoCard() = runTest {
        assertEquals(
            listOf(DiscoveryEvent.Finished),
            scan(FixtureTransport(Fixture.load("bluray-rcr"))),
        )
    }

    @Test
    fun unsupportedNoKeysIsUnsupportedAndSendsNoKeyFrame() = runTest {
        val transport = FixtureTransport(Fixture.load("unsupported-no-keys"))
        val events = scan(transport)
        val tv = events.found().single()
        assertEquals(ControlAvailability.Unsupported, tv.availability)
        assertEquals("Older TV", tv.name)
        // The only traffic discovery produced is the device-info read. The transport has no
        // operation that could carry a key, text or launch frame, or a token.
        assertEquals(listOf(FixtureTransport.Request("[host-a]", 8001)), transport.outbound)
    }

    @Test
    fun oneTelevisionOnTwoHostsIsOneCard() = runTest {
        val events = scan(FixtureTransport(Fixture.load("duplicate-uuid")))
        val tv = events.found().single()
        assertEquals(TvId("3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e"), tv.id)
        assertTrue(tv.stableIdentity)
    }

    @Test
    fun stableIdentitySurvivesRediscovery() = runTest {
        val transport = FixtureTransport(Fixture.load("rediscovery-address-change"))
        val tvs = tvs(transport)
        val first = mutableListOf<DiscoveryEvent>()
        val second = mutableListOf<DiscoveryEvent>()
        launch { tvs.discover().toList(first) }
        advanceUntilIdle()
        launch { tvs.discover().toList(second) }
        advanceUntilIdle()

        val before = first.found().single()
        val after = second.found().single()
        assertEquals("same television, new address, same id", before.id, after.id)
        assertTrue(before.stableIdentity && after.stableIdentity)
        assertEquals(
            listOf(
                FixtureTransport.Request("[host-a]", 8001),
                FixtureTransport.Request("[host-b]", 8001),
            ),
            transport.outbound,
        )
    }

    @Test
    fun mintedIdentityIsLocal() = runTest {
        val events = scan(FixtureTransport(Fixture.load("no-stable-uuid")))
        val tvs = events.found()
        assertEquals("two televisions without a UUID stay two cards", 2, tvs.size)
        tvs.forEach { tv ->
            assertFalse(tv.stableIdentity)
            assertTrue("minted on this phone", tv.id.value.startsWith("local-test-"))
            assertFalse("never derived from an address", tv.id.value.contains("host"))
            assertFalse("never derived from the name", tv.id.value.contains("Guest"))
        }
        assertNotEquals(tvs[0].id, tvs[1].id)
    }

    @Test
    fun televisionsSharingANameStayTwoCards() = runTest {
        val tvs = scan(FixtureTransport(Fixture.load("same-name-two-tvs"))).found()
        assertEquals(listOf("Samsung TV", "Samsung TV"), tvs.map { it.name })
        assertNotEquals(tvs[0].id, tvs[1].id)
    }

    @Test
    fun unreadableDeviceInfoProducesNoCard() = runTest {
        val transport = FixtureTransport(Fixture.load("device-info-unreadable"))
        assertEquals(listOf(DiscoveryEvent.Finished), scan(transport))
        assertEquals(3, transport.outbound.size)
        assertTrue(transport.outbound.all { it.port == 8001 })
    }

    @Test
    fun deviceInfoIsOnlyRequestedFromCandidatesOnPort8001() = runTest {
        val fixtures = Fixture.directories().map { it.name }
        fixtures.forEach { caseId ->
            val transport = FixtureTransport(Fixture.load(caseId))
            scan(transport)
            val candidateHosts = Fixture.load(caseId).probes.map { it.host }.toSet()
            assertTrue(
                "$caseId: device-info only on candidate hosts, port 8001",
                transport.outbound.all { it.host in candidateHosts && it.port == 8001 },
            )
            assertEquals(
                "$caseId: each host is read at most once per scan",
                transport.outbound.distinct(),
                transport.outbound,
            )
        }
    }
}
