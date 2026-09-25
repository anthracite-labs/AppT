package dev.anthracite.appt.samsung.internal

import dev.anthracite.appt.samsung.CommandResult
import dev.anthracite.appt.samsung.RemoteKey
import dev.anthracite.appt.samsung.RemoteSession
import dev.anthracite.appt.samsung.RepairReason
import dev.anthracite.appt.samsung.SessionSnapshot
import dev.anthracite.appt.samsung.SessionState
import dev.anthracite.appt.samsung.TvCapabilities
import dev.anthracite.appt.samsung.TvCommand
import dev.anthracite.appt.samsung.TvFailure
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The first-contact live session machine (docs/architecture/connection.md).
 *
 * One socket, one television, one command path. The state path S03 supports is
 * `Connecting → AwaitingTvApproval → Ready`, with `NeedsRepair` for a denied or timed-out approval,
 * `Unreachable` when the television cannot be reached, `Unsupported` when there is no adopted
 * channel, and `Closed` when the holder releases the session.
 *
 * What this class deliberately does not do:
 * * It never persists a pairing token or an SPKI pin. The token delivered by the approval event and
 *   the candidate certificate identity are transient live-session state, discarded on close. Durable
 *   saved pairing is S04.
 * * It never opens a second socket. `command` writes on the connection this attempt already holds.
 * * It never lets malformed or oversized television traffic escape. [RemoteChannel.parseEvent]
 *   contains it and the frame is dropped.
 * * It never reconnects automatically. Supervised reconnect is S06.
 * * It never touches a cloud participant.
 */
internal class LiveSession(
    private val television: ConfirmedTelevision,
    private val transport: SessionTransport,
    private val scope: CoroutineScope,
    private val approvalWait: Duration = APPROVAL_WAIT,
) : RemoteSession {

    private val mutableSnapshot =
        MutableStateFlow(SessionSnapshot(SessionState.Connecting, TvCapabilities(emptySet())))
    override val snapshot: StateFlow<SessionSnapshot> = mutableSnapshot.asStateFlow()

    private var attempt: Job? = null
    private var openConnection: SessionConnection? = null
    private var approvalTimer: Job? = null

    /** A user-requested retry of the approval prompt, after the previous attempt has ended. */
    private val retrySignals = Channel<Unit>(Channel.CONFLATED)

    /**
     * Live capability evidence for this television. Empty until the adopted channel connects, then the
     * standard remote keys (commands.md#evidence: `Ready` is the evidence the channel accepts them).
     */
    private var channelKeys: Set<RemoteKey> = emptySet()

    /** Transient first-contact material. Never written to a durable store, a file, or a log. */
    private var approvalToken: String? = null

    private var candidateCertificate: String? = null

    init {
        attempt = scope.launch { sessionLoop() }
    }

    override suspend fun command(command: TvCommand): CommandResult {
        val connection =
            if (mutableSnapshot.value.state == SessionState.Ready) openConnection else null
                ?: return CommandResult.Rejected(TvFailure.Unavailable)
        val frame =
            when (command) {
                is TvCommand.Tap -> RemoteChannel.tapFrame(command.key)
            }
        return if (connection.send(frame)) {
            CommandResult.Accepted
        } else {
            CommandResult.Rejected(TvFailure.Unavailable)
        }
    }

    override suspend fun retryApproval() {
        val current = mutableSnapshot.value
        val reason = current.repairReason
        val retryable =
            current.state == SessionState.NeedsRepair &&
                (reason == RepairReason.ApprovalDenied || reason == RepairReason.ApprovalTimedOut)
        if (!retryable) return
        // The previous attempt has already ended, so asking again costs no extra socket.
        publish(SessionState.Connecting)
        retrySignals.trySend(Unit)
    }

    override fun close() {
        attempt?.cancel()
        approvalTimer?.cancel()
        publish(SessionState.Closed)
    }

    private suspend fun sessionLoop() {
        while (true) {
            runAttempt()
            if (mutableSnapshot.value.state == SessionState.Closed) return
            retrySignals.receive()
        }
    }

    private suspend fun runAttempt() {
        if (!television.adoptedChannel) {
            publish(SessionState.Unsupported)
            return
        }
        val connection = transport.connect(television)
        if (connection == null) {
            publish(SessionState.Unreachable)
            return
        }
        openConnection = connection
        candidateCertificate = connection.certificateIdentity
        try {
            connection.frames.collect { frame -> onFrame(frame) }
            // The television ended the session without a close from the holder.
            onConnectionLost()
        } finally {
            connection.close()
            openConnection = null
            approvalTimer?.cancel()
            approvalToken = null
            candidateCertificate = null
            if (!coroutineContext.isActive) publish(SessionState.Closed)
        }
    }

    private fun onFrame(frame: String) {
        // Over-limit or malformed input is dropped here and the session continues.
        val event = RemoteChannel.parseEvent(frame) ?: return
        when (event.name) {
            RemoteChannel.EVENT_CONNECT -> onApproved(event.token)
            RemoteChannel.EVENT_UNAUTHORIZED -> onUnauthorized()
            RemoteChannel.EVENT_TIME_OUT -> onTimeOut()
            RemoteChannel.EVENT_CLIENT_DISCONNECT -> onConnectionLost()
            else -> Unit
        }
    }

    /**
     * The successful channel-connect event moves the live session to `Ready`. First contact sends no
     * token, so an unauthorized event before that means the approval prompt is in progress.
     */
    private fun onApproved(token: String?) {
        approvalTimer?.cancel()
        // Transient live-session evidence only. S04 persists the token with the pin.
        approvalToken = token
        channelKeys = STANDARD_REMOTE_KEYS
        publish(SessionState.Ready)
    }

    private fun onUnauthorized() {
        if (mutableSnapshot.value.state != SessionState.Connecting) return
        publish(SessionState.AwaitingTvApproval)
        startApprovalTimer()
    }

    private fun onTimeOut() {
        if (mutableSnapshot.value.state == SessionState.AwaitingTvApproval) {
            publish(SessionState.NeedsRepair, RepairReason.ApprovalTimedOut)
        } else {
            onConnectionLost()
        }
    }

    private fun onConnectionLost() {
        when (mutableSnapshot.value.state) {
            // The prompt ended without approval: the same recovery as a denial.
            SessionState.AwaitingTvApproval ->
                publish(SessionState.NeedsRepair, RepairReason.ApprovalDenied)
            SessionState.NeedsRepair -> Unit
            else -> publish(SessionState.Unreachable)
        }
    }

    private fun startApprovalTimer() {
        approvalTimer?.cancel()
        approvalTimer =
            scope.launch {
                delay(approvalWait)
                if (mutableSnapshot.value.state == SessionState.AwaitingTvApproval) {
                    publish(SessionState.NeedsRepair, RepairReason.ApprovalTimedOut)
                }
            }
    }

    private fun publish(state: SessionState, repairReason: RepairReason? = null) {
        mutableSnapshot.value = SessionSnapshot(state, TvCapabilities(channelKeys), repairReason)
    }

    companion object {
        /** connection.md: the approval wait. */
        val APPROVAL_WAIT: Duration = 45.seconds

        /**
         * commands.md#evidence: `Ready` on the adopted channel is the evidence that the standard
         * remote keys are accepted. Per-key rejection tracking arrives with the slice that implements
         * rejection; S03 has none to apply.
         */
        val STANDARD_REMOTE_KEYS: Set<RemoteKey> = RemoteKey.entries.toSet()
    }
}
