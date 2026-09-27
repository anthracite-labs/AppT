package dev.anthracite.appt.remote

import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.RepairReason
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvId
import dev.anthracite.appt.testing.FakeSamsungTvs
import dev.anthracite.appt.testing.settle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * lifecycle.md: `ActiveRemoteHost` owns the one live session, so no screen calls `SamsungTvs.open`,
 * a configuration change re-attaches instead of re-opening, and the last release starts the
 * 15-second grace.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveRemoteHostTest {
    private val tvs = FakeSamsungTvs()
    private val livingRoom = TvId(FakeSamsungTvs.LIVING_ROOM_ID)
    private val bedroom = TvId(FakeSamsungTvs.OLDER_ID)

    /**
     * The host runs on [TestScope.backgroundScope] rather than the test scope: its session
     * observation and grace coroutines outlive the test body, and `runTest` cancels the background
     * scope when the test finishes. Because of that the tests drive time with [settle], not with
     * `advanceUntilIdle`, which upstream stops before background coroutines have run.
     */
    private fun TestScope.host() = ActiveRemoteHost(tvs, backgroundScope)

    @Test
    fun enterOpensOneSessionForOneTelevision() = runTest {
        val host = host()
        host.enter(livingRoom)
        settle()

        assertEquals(listOf(livingRoom), tvs.openedIds)
        assertEquals(livingRoom, host.current.value?.tvId)
        assertEquals(SessionState.Connecting, host.current.value?.snapshot?.state)
    }

    @Test
    fun enteringATelevisionWhoseSessionEndedOpensANewOne() = runTest {
        val host = host()
        host.enter(livingRoom)
        settle()
        val dead = tvs.sessionFor(livingRoom)!!
        dead.publish(SessionState.Unreachable)
        settle()

        host.enter(livingRoom)
        settle()

        assertTrue("the ended session is released", dead.closed)
        assertEquals(listOf(livingRoom, livingRoom), tvs.openedIds)
        assertEquals(
            "the replacement reports Connecting, not the state that failed",
            SessionState.Connecting,
            host.current.value?.snapshot?.state,
        )
    }

    @Test
    fun aRepairableSessionIsNotReplaced() = runTest {
        val host = host()
        host.enter(livingRoom)
        settle()
        val repairable = tvs.sessionFor(livingRoom)!!
        repairable.publish(
            SessionState.NeedsRepair,
            repairReason = dev.anthracite.appt.samsung.RepairReason.ApprovalDenied,
        )
        settle()

        host.enter(livingRoom)
        settle()

        assertFalse("retryApproval is how a repairable session recovers", repairable.closed)
        assertEquals(listOf(livingRoom), tvs.openedIds)
    }

    @Test
    fun retryingADeadSessionKeepsTheVisibleRemoteOwner() = runTest {
        val host = host()
        host.enter(livingRoom)
        host.retain("remote")
        settle()
        tvs.sessionFor(livingRoom)!!.publish(SessionState.Unreachable)
        settle()

        host.enter(livingRoom)
        settle()
        val replacement = tvs.sessionFor(livingRoom)!!

        host.release("remote")
        advanceTimeBy(14_999)
        runCurrent()
        assertFalse("the retained replacement survives until grace expires", replacement.closed)

        advanceTimeBy(2)
        runCurrent()
        assertTrue(replacement.closed)
    }

    @Test
    fun enteringTheSameTelevisionAgainOpensNoSecondSession() = runTest {
        val host = host()
        host.enter(livingRoom)
        settle()
        host.enter(livingRoom)
        settle()

        assertEquals("no second open, so no second socket", listOf(livingRoom), tvs.openedIds)
        assertFalse("the first session is not replaced", tvs.sessionFor(livingRoom)!!.closed)
    }

    @Test
    fun enteringAnotherTelevisionClosesTheFirst() = runTest {
        val host = host()
        host.enter(livingRoom)
        settle()
        host.enter(bedroom)
        settle()

        assertTrue(tvs.sessionFor(livingRoom)!!.closed)
        assertEquals(listOf(livingRoom, bedroom), tvs.openedIds)
        assertEquals(bedroom, host.current.value?.tvId)
    }

    @Test
    fun theSnapshotFollowsTheSessionWithoutPolling() = runTest {
        val host = host()
        host.enter(livingRoom)
        settle()

        tvs.sessionFor(livingRoom)!!.ready()
        settle()

        assertEquals(SessionState.Ready, host.current.value?.snapshot?.state)
        assertEquals(RemoteKey.entries.toSet(), host.current.value?.snapshot?.capabilities?.keys)
    }

    @Test
    fun aCommandIsSentOnTheRetainedSession() = runTest {
        val host = host()
        host.enter(livingRoom)
        settle()
        val session = tvs.sessionFor(livingRoom)!!
        session.ready()
        session.nextResult = CommandResult.Accepted
        settle()

        val result = host.current.value!!.session.command(TvCommand.Tap(RemoteKey.VolumeUp))

        assertEquals(CommandResult.Accepted, result)
        assertEquals(listOf(TvCommand.Tap(RemoteKey.VolumeUp)), session.commands)
    }

    @Test
    fun aDeniedEntryOpensNothing() = runTest {
        val host = ActiveRemoteHost(tvs, backgroundScope, entryAllowed = { false })
        host.enter(livingRoom)
        settle()

        assertEquals("no session, no socket", emptyList<TvId>(), tvs.openedIds)
        assertNull(host.current.value)
    }

    /** data.md: `lastOpenedAt` is written when the session reaches `Ready`, once per session. */
    @Test
    fun reachingReadyReportsTheTelevisionOnce() = runTest {
        val ready = mutableListOf<TvId>()
        val host =
            ActiveRemoteHost(tvs, backgroundScope, onSessionReady = { tvId -> ready += tvId })
        host.enter(livingRoom)
        settle()
        val session = tvs.sessionFor(livingRoom)!!

        session.ready()
        settle()
        session.ready(setOf(RemoteKey.Mute))
        settle()

        assertEquals("one report per session, not one per snapshot", listOf(livingRoom), ready)
        host.close()
        settle()
    }

    @Test
    fun aReadyProfileWriteFailureDoesNotStopSessionObservation() = runTest {
        val host =
            ActiveRemoteHost(
                samsungTvs = tvs,
                scope = backgroundScope,
                onSessionReady = { throw IllegalStateException("profile store unavailable") },
            )
        host.enter(livingRoom)
        settle()
        val session = tvs.sessionFor(livingRoom)!!

        session.ready()
        settle()
        assertEquals(SessionState.Ready, host.current.value?.snapshot?.state)

        session.publish(SessionState.Unreachable)
        settle()

        assertEquals(
            "persistence failure does not detach the live session observer",
            SessionState.Unreachable,
            host.current.value?.snapshot?.state,
        )
        host.close()
        settle()
    }

    @Test
    fun aSessionThatNeverReachesReadyReportsNothing() = runTest {
        val ready = mutableListOf<TvId>()
        val host =
            ActiveRemoteHost(tvs, backgroundScope, onSessionReady = { tvId -> ready += tvId })
        host.enter(livingRoom)
        settle()
        tvs.sessionFor(livingRoom)!!.publish(SessionState.AwaitingTvApproval)
        settle()

        assertEquals(emptyList<TvId>(), ready)
        host.close()
        settle()
    }

    @Test
    fun theLastReleaseStartsGraceAndTheSessionSurvivesIt() = runTest {
        val host = host()
        host.enter(livingRoom)
        settle()
        host.retain("pairing")
        host.release("pairing")

        advanceTimeBy(14_999)
        runCurrent()
        assertEquals("grace keeps the session", livingRoom, host.current.value?.tvId)
        assertFalse(tvs.sessionFor(livingRoom)!!.closed)

        advanceTimeBy(2)
        runCurrent()
        assertTrue("grace elapsed closes the session", tvs.sessionFor(livingRoom)!!.closed)
        assertNull(host.current.value)
    }

    @Test
    fun returningInsideGraceKeepsTheSameSession() = runTest {
        val host = host()
        host.enter(livingRoom)
        settle()
        host.retain("remote")
        host.release("remote")
        advanceTimeBy(5_000)

        host.retain("remote")
        advanceTimeBy(20_000)
        runCurrent()

        assertFalse(
            "returning inside grace reuses the session",
            tvs.sessionFor(livingRoom)!!.closed,
        )
        assertEquals(listOf(livingRoom), tvs.openedIds)
    }

    @Test
    fun oneOwnerReleasingDoesNotStartGrace() = runTest {
        val host = host()
        host.enter(livingRoom)
        settle()
        host.retain("pairing")
        host.retain("remote")
        host.release("pairing")

        advanceTimeBy(30_000)
        runCurrent()

        assertFalse(tvs.sessionFor(livingRoom)!!.closed)
        assertEquals(1, tvs.openedIds.size)
    }

    @Test
    fun closeIsImmediateAndIdempotent() = runTest {
        val host = host()
        host.enter(livingRoom)
        settle()

        host.close()
        settle()
        host.close()
        settle()

        assertTrue(tvs.sessionFor(livingRoom)!!.closed)
        assertNull(host.current.value)
    }

    @Test
    fun aReleasedHostHoldsNothingToSendThrough() = runTest {
        val host = host()
        host.enter(livingRoom)
        settle()

        host.close()
        settle()

        assertNull("nothing routes a command through a closed host", host.current.value)
        assertTrue(tvs.sessionFor(livingRoom)!!.closed)
    }
}
