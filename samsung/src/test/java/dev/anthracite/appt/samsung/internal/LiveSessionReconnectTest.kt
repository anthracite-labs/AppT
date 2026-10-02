package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.RepairReason
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The S06 supervised-reconnect contract, driven through the production session machine
 * (docs/architecture/connection.md#supervised-reconnect, docs/architecture/testing.md).
 *
 * Time is virtual, so the documented 0.5s/1s/2s/4s/8s schedule and the 45-second budget are real
 * scheduler values. `reconnect-backoff` is the fixture docs/architecture/testing.md names; the
 * other cases script their sockets inline so a case can end a socket exactly where its assertion
 * needs it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveSessionReconnectTest {
    private val secrets = InMemorySamsungStore()

    private val tvId = TvId("3f2d1c0b-8a7e-4b5c-9d6e-1f2a3b4c5d6e")

    private val television =
        ConfirmedTelevision(
            id = tvId,
            host = "[host-a]",
            tls = true,
            adoptedChannel = true,
            uuid = tvId.value,
            displayName = "Living Room TV",
        )

    /** The token and pin a saved-pairing case stages; never a committed fixture value. */
    private val savedToken = "resume-token-value-injected-by-the-test"
    private val savedPin = "aa".repeat(32)
    private val otherPin = "bb".repeat(32)

    private val connectedFrame =
        """{"event":"ms.channel.connect","data":{"token":"[fixture-token]"}}"""

    private fun connected(atMs: Long = 0): SessionEvent.Frame =
        SessionEvent.Frame(atMs, connectedFrame)

    private fun ends(atMs: Long): SessionEvent.Close = SessionEvent.Close(atMs)

    private fun script(vararg events: SessionEvent): SessionFixture =
        SessionFixture("script", events.toList(), emptyList())

    @Test
    fun reconnectBackoff() = runTest {
        val transport =
            ScriptedSessionTransport(
                SessionFixture.load("reconnect-backoff"),
                // The adopted socket opens; every reconnect attempt is refused.
                refusesConnection = true,
                refusalScript = listOf(false),
                now = { testScheduler.currentTime },
            )
        val session =
            LiveSession(
                television,
                transport,
                this,
                secrets,
                nowMillis = { testScheduler.currentTime },
            )

        advanceUntilIdle()

        assertEquals(SessionState.Unreachable, session.snapshot.value.state)
        assertEquals(
            "one adopted socket, then exactly the budgeted reconnect attempts",
            1 + LiveSession.RECONNECT_ATTEMPT_LIMIT,
            transport.connects.size,
        )
        // The fixture's socket ends at 120 ms; the reconnect attempts must then land exactly on
        // the documented schedule.
        val expectedAttempts =
            LiveSession.RECONNECT_SCHEDULE_MS.runningFold(120L) { at, wait -> at + wait }.drop(1)
        assertEquals(
            "each reconnect attempt waits the documented backoff since the socket was lost",
            expectedAttempts,
            transport.attemptTimes.drop(1),
        )
        assertTrue(
            "the whole schedule fits inside the reconnect budget",
            LiveSession.RECONNECT_SCHEDULE_MS.sum() <= LiveSession.RECONNECT_BUDGET_MS,
        )

        // `Unreachable` ends automatic recovery: nothing retries, however long the holder waits.
        val attemptsAtStop = transport.connects.size
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(attemptsAtStop, transport.connects.size)
        assertEquals(SessionState.Unreachable, session.snapshot.value.state)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun commandsDuringReconnectAreRejectedAndNothingIsReplayed() = runTest {
        val transport =
            ScriptedSessionTransport(
                script(connected(), ends(50)),
                refusesConnection = true,
                refusalScript = listOf(false),
                now = { testScheduler.currentTime },
            )
        val session =
            LiveSession(
                television,
                transport,
                this,
                secrets,
                nowMillis = { testScheduler.currentTime },
            )
        advanceTimeBy(60)
        runCurrent()
        assertEquals(SessionState.Reconnecting, session.snapshot.value.state)

        val result = session.command(TvCommand.Tap(RemoteKey.VolumeUp))
        advanceUntilIdle()

        assertEquals(CommandResult.Rejected(TvFailure.Unavailable), result)
        assertEquals(
            "a command while recovering writes nothing and is never replayed",
            emptyList<String>(),
            transport.sent,
        )
        assertEquals(SessionState.Unreachable, session.snapshot.value.state)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun rediscoverSameUuid() = runTest {
        val recorded = mutableListOf<ConfirmedTelevision>()
        val transport =
            ScriptedSessionTransport(
                script(connected(), ends(120)),
                script(connected()),
                // The saved address is tried once and refused; the rediscovered one accepts.
                refusalScript = listOf(false, true),
                now = { testScheduler.currentTime },
            )
        val session =
            LiveSession(
                television,
                transport,
                this,
                secrets,
                rediscovery = rediscoveredAt("[host-b]"),
                recordTelevision = { recorded += it },
                nowMillis = { testScheduler.currentTime },
            )

        advanceUntilIdle()

        assertEquals(SessionState.Ready, session.snapshot.value.state)
        assertEquals(
            "the session recovered on the rediscovered address without user action",
            listOf("[host-a]", "[host-a]", "[host-b]"),
            transport.connects.map { it.host },
        )
        assertEquals(
            "the rediscovered evidence replaces the saved address for the next attempt",
            listOf("[host-b]"),
            recorded.map { it.host },
        )
        assertNull(session.snapshot.value.repairReason)

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun aRediscoveredAddressWithADifferentIdentityNeedsRepairAndGetsNoToken() = runTest {
        secrets.saveRawSecret(tvId, PairingSecret(token = savedToken, pin = savedPin))
        secrets.saveRawDevice(
            tvId,
            SamsungDeviceRecord(
                uuid = tvId.value,
                lastAddress = "[host-a]",
                tls = true,
                adoptedChannel = true,
                displayName = "Living Room TV",
                stableIdentity = true,
            ),
        )
        val transport =
            ScriptedSessionTransport(
                script(connected(), ends(120)),
                // The saved address matches the saved pin; the rediscovered address does not.
                refusalScript = listOf(false, true, false),
                presentedPinScript = listOf(savedPin, savedPin, otherPin),
                now = { testScheduler.currentTime },
            )
        val session =
            LiveSession(
                television,
                transport,
                this,
                secrets,
                rediscovery = rediscoveredAt("[host-b]"),
                nowMillis = { testScheduler.currentTime },
            )

        advanceUntilIdle()

        assertEquals(SessionState.NeedsRepair, session.snapshot.value.state)
        assertEquals(RepairReason.IdentityChanged, session.snapshot.value.repairReason)
        assertEquals(
            "the saved token is presented only to the identity it was saved under",
            listOf(true),
            transport.attemptedTokens,
        )
        assertEquals(
            "a settled saved-identity failure stops automatic recovery",
            listOf("[host-a]", "[host-a]", "[host-b]"),
            transport.connects.map { it.host },
        )

        session.close()
        advanceUntilIdle()
    }

    @Test
    fun aSecondLossAfterRecoveryReconnectsAgain() = runTest {
        // Socket one is the adopted connection that drops; socket two is the rediscovered address,
        // which settles `Ready` and then drops as well; socket three is the recovery after that
        // second loss. A fresh loss after a settled session starts a fresh bounded budget.
        val transport =
            ScriptedSessionTransport(
                script(ends(20)),
                script(connected(), ends(90)),
                script(connected()),
                refusalScript = listOf(false, true),
                now = { testScheduler.currentTime },
            )
        val session =
            LiveSession(
                television,
                transport,
                this,
                secrets,
                rediscovery = rediscoveredAt("[host-b]"),
                nowMillis = { testScheduler.currentTime },
            )

        advanceUntilIdle()

        assertEquals(
            "the session that was lost after recovering reconnects again instead of stopping",
            SessionState.Ready,
            session.snapshot.value.state,
        )
        assertEquals(
            "adopted, refused, rediscovered, then lost again and recovered once more",
            listOf("[host-a]", "[host-a]", "[host-b]", "[host-b]"),
            transport.connects.map { it.host },
        )

        session.close()
        advanceUntilIdle()
    }

    /** A rediscovery that always answers with the saved television at [host] and nothing else. */
    private fun rediscoveredAt(host: String) =
        object : SessionRediscovery {
            override suspend fun rediscover(television: ConfirmedTelevision): ConfirmedTelevision =
                television.copy(host = host)
        }
}
