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
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The live session machine (docs/architecture/connection.md).
 *
 * One socket, one television, one command path. The state paths:
 * * First contact (S03): `Connecting → AwaitingTvApproval → Ready`, with `NeedsRepair` for a denied
 *   or timed-out approval.
 * * Saved pairing (S04): a saved token resumes only after the saved security identity matched (TLS
 *   SPKI pin on the TLS channel, protocol UUID on the plaintext channel); the token is attached to
 *   the attempted URL only after the TLS pin check has passed. Approval success persists the token
 *   and pin atomically together with the samsung-private device record. `unauthorized` after a
 *   saved token was sent is `NeedsRepair(TokenRejected)` and stops — no reconnect, no loop.
 *
 * What this class deliberately does not do:
 * * It never sends a saved token to a television whose saved identity did not match, never sends a
 *   saved token on the plaintext channel, and never persists anything after its [SessionGeneration]
 *   was superseded (connection.md#evidence-and-race-handling-harvest).
 * * It never falls back to plaintext storage or an empty pairing when secrets are unavailable
 *   (`NeedsRepair` without a repair reason is the caller-facing `SecretsUnavailable` surface).
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
    private val secrets: SamsungSecretStore,
    private val generation: SessionGeneration = SessionGeneration.ALWAYS_CURRENT,
    private val approvalWait: Duration = APPROVAL_WAIT,
    private val capabilityDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val diagnostics: SamsungDiagnosticRecorder = SamsungDiagnosticRecorder(),
) : RemoteSession {

    private val mutableSnapshot =
        MutableStateFlow(SessionSnapshot(SessionState.Connecting, TvCapabilities(emptySet())))
    override val snapshot: StateFlow<SessionSnapshot> = mutableSnapshot.asStateFlow()

    private var attempt: Job? = null
    private var openConnection: SessionConnection? = null
    private var approvalTimer: Job? = null

    /** The in-flight frame collection, so an approval that goes unanswered can end it. */
    private var collecting: Job? = null

    /** True once the holder released this session, so an ended attempt is a release, not a loss. */
    private val released = AtomicBoolean(false)

    /** A user-requested retry of the approval prompt, after the previous attempt has ended. */
    private val retrySignals = Channel<Unit>(Channel.CONFLATED)

    /**
     * Live capability evidence for this television. Empty until the adopted channel connects, then
     * the standard remote keys (commands.md#evidence: `Ready` is the evidence the channel accepts
     * them).
     */
    private var channelConnected = false

    /** Last explicit per-key negative evidence, kept Samsung-private and bounded by the enum. */
    private val rejectedKeyEvidence =
        AtomicReference(secrets.loadDevice(television.id)?.rejectedKeys.orEmpty())

    /** A conflated signal makes the IO writer read the newest set without blocking dispatch. */
    private val capabilityUpdates = Channel<Unit>(Channel.CONFLATED)

    /** The saved pairing this attempt resumed with, or null on first contact. */
    private var resumedFrom: PairingSecret? = null

    /**
     * True when this attempt's connection carried the saved token on the wire: only the TLS path
     * with a saved pin that matched can truthfully say so.
     */
    private var resumedWithTokenSent = false

    init {
        scope.launch(capabilityDispatcher) {
            while (capabilityUpdates.receiveCatching().isSuccess) {
                val record = secrets.loadDevice(television.id) ?: continue
                try {
                    secrets.saveDevice(
                        television.id,
                        record.copy(rejectedKeys = rejectedKeyEvidence.get()),
                    )
                } catch (ignored: IOException) {
                }
            }
        }
        attempt = scope.launch { sessionLoop() }
    }

    override suspend fun command(command: TvCommand): CommandResult {
        val connection =
            (if (mutableSnapshot.value.state == SessionState.Ready) openConnection else null)
                ?: run {
                    diagnostics.commandUnavailable()
                    return CommandResult.Rejected(TvFailure.Unavailable)
                }
        val frame =
            when (command) {
                is TvCommand.Tap -> RemoteChannel.tapFrame(command.key)
                is TvCommand.PointerMove,
                TvCommand.PointerClick -> {
                    diagnostics.commandUnavailable()
                    return CommandResult.Rejected(TvFailure.Unavailable)
                }
            }
        return when (connection.sendCommand(frame)) {
            CommandWriteResult.Written -> {
                diagnostics.commandWritten()
                CommandResult.Accepted
            }
            CommandWriteResult.LocalRefused -> {
                diagnostics.commandUnavailable()
                CommandResult.Rejected(TvFailure.Unavailable)
            }
            CommandWriteResult.TelevisionRejected -> {
                diagnostics.commandRejected()
                recordRejectedKey(command.key)
                CommandResult.Rejected(TvFailure.Rejected)
            }
        }
    }

    override suspend fun retryApproval() {
        val current = mutableSnapshot.value
        val reason = current.repairReason
        val retryable =
            current.state == SessionState.NeedsRepair &&
                (reason == RepairReason.ApprovalDenied || reason == RepairReason.ApprovalTimedOut)
        if (!retryable) return
        retrySignals.trySend(Unit)
    }

    override suspend fun confirmRepair() {
        val current = mutableSnapshot.value
        if (current.state != SessionState.NeedsRepair) return
        if (
            current.repairReason != RepairReason.TokenRejected &&
                current.repairReason != RepairReason.IdentityChanged
        ) {
            return
        }
        try {
            secrets.discardSecret(television.id)
        } catch (ignored: IOException) {
            return
        }
        retrySignals.trySend(Unit)
    }

    override fun close() {
        released.set(true)
        attempt?.cancel()
        approvalTimer?.cancel()
        capabilityUpdates.close()
        publish(SessionState.Closed)
    }

    private suspend fun sessionLoop() {
        while (true) {
            runAttempt()
            if (mutableSnapshot.value.state == SessionState.Closed) return
            retrySignals.receive()
            if (mutableSnapshot.value.state == SessionState.Closed) return
            publish(SessionState.Connecting)
        }
    }

    private suspend fun runAttempt() {
        if (!television.adoptedChannel) {
            publish(SessionState.Unsupported)
            return
        }
        val saved =
            when (val stored = secrets.loadSecret(television.id)) {
                is StoredSecret.Available -> stored.secret
                StoredSecret.Unavailable -> {
                    publish(SessionState.NeedsRepair, repairReason = null)
                    return
                }
                StoredSecret.Absent -> null
            }
        if (saved != null && !television.tls && savedUuidIdentityChanged()) {
            publish(SessionState.NeedsRepair, RepairReason.IdentityChanged)
            return
        }
        when (val result = transport.connect(television, saved)) {
            is ConnectionAttempt.Opened -> runConnection(result.connection, saved)
            ConnectionAttempt.IdentityMismatch ->
                publish(SessionState.NeedsRepair, RepairReason.IdentityChanged)
            ConnectionAttempt.Unreachable -> publish(SessionState.Unreachable)
        }
    }

    /** The saved protocol UUID compared against the television's current one. */
    private fun savedUuidIdentityChanged(): Boolean {
        val savedUuid = secrets.loadDevice(television.id)?.uuid ?: return false
        val presentUuid = television.uuid ?: return false
        return savedUuid != presentUuid
    }

    private suspend fun runConnection(connection: SessionConnection, saved: PairingSecret?) {
        openConnection = connection
        resumedFrom = saved
        resumedWithTokenSent = saved?.token != null && television.tls && saved.pin != null
        try {
            coroutineScope {
                collecting = launch { collectFrames(connection) }
                collecting?.join()
            }
            onConnectionLost()
        } finally {
            collecting = null
            connection.close()
            openConnection = null
            approvalTimer?.cancel()
            resumedFrom = null
            resumedWithTokenSent = false
            if (released.get()) publish(SessionState.Closed)
        }
    }

    private fun onFrame(frame: String) {
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
     * The successful channel-connect event.
     *
     * On a resumed connection the saved identity already matched, so this is a trusted session: a
     * token the television reissued replaces the stored one atomically. On first contact this is
     * the approval, and the token and pin are persisted in one atomic secret write together with
     * the samsung-private device record.
     */
    private fun onApproved(token: String?) {
        approvalTimer?.cancel()
        channelConnected = true
        if (!generation.isActive()) return
        val saved = resumedFrom
        if (saved == null) {
            val pin = connectionPin
            try {
                persistPairing(token = token, pin = pin)
            } catch (ignored: IOException) {
                publish(SessionState.NeedsRepair, repairReason = null)
                endUnansweredAttempt()
                return
            }
        } else {
            try {
                if (token != null && token != saved.token) {
                    secrets.saveSecret(television.id, PairingSecret(token = token, pin = saved.pin))
                }
            } catch (ignored: IOException) {
                publish(SessionState.NeedsRepair, repairReason = null)
                endUnansweredAttempt()
                return
            }
        }
        publish(SessionState.Ready)
    }

    /**
     * The pin to persist with a first approval: the SPKI this connection presented on the TLS
     * channel, or null on the plaintext channel, whose identity is the protocol UUID saved in the
     * device record instead.
     */
    private val connectionPin: String?
        get() = openConnection?.certificateIdentity?.takeIf { television.tls }

    /**
     * The approval write: the samsung-private device record first, then the token and pin as one
     * atomic secret write (data.md#samsung-secret-record). A completed secret write therefore
     * always has a device record beside it, so reopen and address continuity work.
     */
    private fun persistPairing(token: String?, pin: String?) {
        secrets.saveDevice(
            television.id,
            SamsungDeviceRecord(
                uuid = television.uuid,
                lastAddress = television.host,
                tls = television.tls,
                adoptedChannel = television.adoptedChannel,
                displayName = television.displayName,
                stableIdentity = television.uuid != null,
            ),
        )
        secrets.saveSecret(television.id, PairingSecret(token = token, pin = pin))
    }

    private fun onUnauthorized() {
        if (resumedWithTokenSent) {
            publish(SessionState.NeedsRepair, RepairReason.TokenRejected)
            endUnansweredAttempt()
            return
        }
        if (mutableSnapshot.value.state != SessionState.Connecting) return
        publish(SessionState.AwaitingTvApproval)
        startApprovalTimer()
    }

    private fun onTimeOut() {
        if (mutableSnapshot.value.state == SessionState.AwaitingTvApproval) {
            publish(SessionState.NeedsRepair, RepairReason.ApprovalTimedOut)
            endUnansweredAttempt()
        } else {
            onConnectionLost()
        }
    }

    /**
     * Reads the connection until the television ends it, the holder releases the session, or the
     * approval wait ends an attempt whose prompt was never answered.
     */
    private suspend fun collectFrames(connection: SessionConnection) {
        try {
            connection.frames.collect { frame -> onFrame(frame) }
        } finally {
            onConnectionLost()
        }
    }

    /**
     * Ends an attempt whose approval prompt was never answered, or whose token was rejected.
     *
     * Without this the attempt keeps collecting the still-open socket, so `sessionLoop` never
     * reaches `retrySignals.receive()` and `retryApproval`/`confirmRepair` have nothing to act on:
     * the session would sit in `Connecting` with a socket nobody is using. Ending the collection is
     * what lets the loop start a fresh attempt.
     */
    private fun endUnansweredAttempt() {
        collecting?.cancel()
    }

    private fun onConnectionLost() {
        when (mutableSnapshot.value.state) {
            SessionState.AwaitingTvApproval ->
                publish(SessionState.NeedsRepair, RepairReason.ApprovalDenied)
            SessionState.NeedsRepair,
            SessionState.Closed -> Unit
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
                    endUnansweredAttempt()
                }
            }
    }

    /**
     * Applies only an explicit protocol-boundary television rejection, never a local send result or
     * a missing visible TV action. A successful local socket write is not positive television
     * evidence and therefore cannot erase this negative evidence. Persistence is offered to a
     * conflated IO consumer so no file access blocks dispatch.
     */
    private fun recordRejectedKey(key: RemoteKey) {
        val previous = rejectedKeyEvidence.getAndUpdate { it + key }
        if (key in previous) return
        capabilityUpdates.trySend(Unit)
        publish(mutableSnapshot.value.state, mutableSnapshot.value.repairReason)
    }

    /**
     * The single publication point for session state. `NeedsRepair` with a null [repairReason] is
     * the caller-facing `SecretsUnavailable` surface: saved material exists but could not be used,
     * so pairing again is required. There is no plaintext fallback and no token transmission on
     * that path.
     */
    private fun publish(state: SessionState, repairReason: RepairReason? = null) {
        val keys =
            if (channelConnected) STANDARD_REMOTE_KEYS - rejectedKeyEvidence.get() else emptySet()
        val snapshot = SessionSnapshot(state, TvCapabilities(keys), repairReason)
        mutableSnapshot.value = snapshot
        diagnostics.session(snapshot)
    }

    companion object {
        /** connection.md: the approval wait. */
        val APPROVAL_WAIT: Duration = 45.seconds

        /**
         * commands.md#evidence: `Ready` on the adopted channel is the evidence that the standard
         * remote keys are accepted, except keys removed by explicit per-key television rejection.
         */
        val STANDARD_REMOTE_KEYS: Set<RemoteKey> = RemoteKey.entries.toSet()
    }
}
