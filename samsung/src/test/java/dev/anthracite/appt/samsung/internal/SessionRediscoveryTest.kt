package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The internal rediscovery seam for supervised reconnect
 * (docs/architecture/connection.md#supervised-reconnect, discovery.md#dedup-and-rediscovery).
 *
 * Rediscovery is not the caller-facing scan: it emits no card, it is never started by a screen, and
 * it accepts only the television whose protocol UUID the saved pairing recorded. Everything
 * credential-shaped is left exactly as the pairing stored it, so the next connect still enforces
 * the saved security identity and a different television on a rediscovered address fails closed.
 *
 * The `rediscover-same-uuid` fixture is the case docs/architecture/testing.md#fixtures names: the
 * saved UUID answers at a new address while a different UUID answers too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRediscoveryTest {
    private val savedUuid = "3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e"
    private val otherUuid = "9c8b7a6d-5e4f-4321-8abc-def012345678"

    private fun television(uuid: String? = savedUuid) =
        ConfirmedTelevision(
            id = TvId(savedUuid),
            host = "[host-a]",
            tls = true,
            adoptedChannel = true,
            uuid = uuid,
            displayName = "Living Room TV",
        )

    /** A rediscovery over the case fixture, with the same fixture every call. */
    private fun rediscovery() = LanSessionRediscovery {
        FixtureTransport(Fixture.load("rediscover-same-uuid"), lan = TestLan())
    }

    @Test
    fun theSavedUuidIsAcceptedAtItsNewAddress() = runTest {
        val saved = television()

        val found = rediscovery().rediscover(saved)

        assertEquals("only the saved UUID may be accepted", savedUuid, found?.uuid)
        assertEquals("the new address replaces the saved one", "[host-b]", found?.host)
        assertEquals("the identity and its id are untouched", saved.id, found?.id)
        assertEquals(
            "the adopted channel and its credentials are untouched",
            listOf(saved.tls to saved.adoptedChannel),
            listOf(found!!.tls to found.adoptedChannel),
        )
        assertEquals("the television-reported name is kept", "Living Room TV", found.displayName)
    }

    @Test
    fun aDifferentUuidOnTheSameRouteIsNeverAccepted() = runTest {
        // The television this session holds is not the one that answers: no match, no address.
        val other = television(uuid = otherUuid)

        assertNull(
            "a different UUID is not this session's television",
            rediscovery().rediscover(other),
        )
    }

    @Test
    fun aTelevisionWithoutAStableUuidIsNeverRediscovered() = runTest {
        assertNull(
            "a minted id has no televised identity to match",
            rediscovery().rediscover(television(uuid = null)),
        )
    }

    @Test
    fun aRouteWithoutAUsableLanIsNeverProbed() = runTest {
        val transport = FixtureTransport(Fixture.load("rediscover-same-uuid"), lan = null)
        val onVpn = LanSessionRediscovery { transport }

        assertNull("a VPN-only route has no LAN to rediscover on", onVpn.rediscover(television()))
        assertTrue("nothing is probed on that route", transport.outbound.isEmpty())
        assertTrue("and no multicast lock is taken", transport.lockLog.isEmpty())
    }

    @Test
    fun theSharedMulticastLockIsReleasedWhenRediscoveryEnds() = runTest {
        val transport = FixtureTransport(Fixture.load("rediscover-same-uuid"), lan = TestLan())

        LanSessionRediscovery { transport }.rediscover(television())

        assertEquals(
            "the lock is acquired and released around the bounded probe",
            listOf(FixtureTransport.ACQUIRE, FixtureTransport.RELEASE),
            transport.lockLog,
        )
        assertEquals("the rediscovery is over", 0, transport.probesRunning)
    }

    @Test
    fun rediscoveryIsBounded() = runTest {
        // A transport that answers nothing stays inside the bound and returns no match.
        val silent = FixtureTransport(Fixture.load("soundbar-airplay"), lan = TestLan())

        assertNull(LanSessionRediscovery { silent }.rediscover(television()))
        assertTrue(
            "the bound is the documented five seconds",
            testScheduler.currentTime >= LanSessionRediscovery.REDISCOVERY_BOUND.inWholeMilliseconds,
        )
    }
}
