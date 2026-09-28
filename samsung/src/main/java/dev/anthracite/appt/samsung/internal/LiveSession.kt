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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
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
 * * Saved pairing (S04): a saved token resumes only after the saved security identity matched
 *   (TLS SPKI pin on the TLS channel, protocol UUID on the plaintext channel); the token is attached
 *   to the attempted URL only after the TLS pin check has passed. Approval success persists the
 *   token and pin atomically together with the samsung-private device record. `unauthorized` after a
 *   saved token was sent is `NeedsRepair(TokenRejected)` and stops — no reconnect, no loop.
 *
 * What this class deliberately does not do:
 * * It never sends a saved token to a television whose saved identity did not match, never sends a
 *   saved token on the plaintext channel, and never persists anything after its
 *   [SessionGeneration] was superseded (connection.md#evidence-and-race-handling-harvest).
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
    private var channelKeys: Set<RemoteKey> = emptySet()

    /** The saved pairing this attempt resumed with, or null on first contact. */
    private var resumedFrom: PairingSecret? = null

    /**
     * True when this attempt's connection carried the saved token on the wire: only the TLS path
     * with a saved pin that matched can truthfully say so.
     */
    private var resumedWithTokenSent = false

    init {
        attempt = scope.launch { sessionLoop() }
    }

    override suspend fun command(command: TvCommand): CommandResult {
        val connection =
            (if (mutableSnapshot.value.state == SessionState.Ready) openConnection else null)
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
        // Queue the retry, but do not publish Connecting yet. The previous attempt may still be
        // unwinding a canceled frame collector; sessionLoop publishes Connecting only after that
        // attempt has fully closed, so stale connection-loss callbacks cannot overwrite the retry.
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
        // connection.md: discard the saved secret first, then pair as new. Deletion failure keeps
        // the state unchanged: AppT never pairs anew while old approval material remains.
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
        publish(SessionState.Closed)
    }

    private suspend fun sessionLoop() {
        while (true) {
            runAttempt()
            if (mutableSnapshot.value.state == SessionState.Closed) return
            retrySignals.receive()
            if (mutableSnapshot.value.state == SessionState.Closed) return
            // The old attempt is now fully cleaned up. Only the session loop starts the next
            // attempt, so an old collector cannot overwrite this state with Unreachable.
            publish(SessionState.Connecting)
        }
    }

    private suspend fun runAttempt() {
        if (!television.adoptedChannel) {
            publish(SessionState.Unsupported)
            return
        }
        // connection.md#ordering-relative-to-secrets: read the secret first. Undecryptable saved
        // material is SecretsUnavailable and stops: no connect, no token, no fallback.
        val saved =
            when (val stored = secrets.loadSecret(television.id)) {
                is StoredSecret.Available -> stored.secret
                StoredSecret.Unavailable -> {
                    publishSecretsUnavailable()
                    return
                }
                StoredSecret.Absent -> null
            }
        // The plaintext identity is the protocol UUID (connection.md#security-identity). A
        // television that now presents a different UUID than the saved one is not the television
        // this phone paired, so it fails closed before any socket is opened.
        if (saved != null && !television.tls && savedUuidIdentityChanged()) {
            publish(SessionState.NeedsRepair, RepairReason.IdentityChanged)
            return
        }
        when (val attempt = transport.connect(television, saved)) {
            is ConnectionAttempt.Opened -> runConnection(attempt.connection, saved)
            // The saved pin did not match this handshake. The token never reached the attempt, so
            // there is nothing to retract: fail closed and ask the user to re-pair explicitly.
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
            // The collection is a child of this attempt, so ending it ends the attempt and the
            // loop is free to start a fresh one; the holder's close cancels it with the attempt.
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
            // The holder released the session; `close` already published `Closed`, and repeating
            // it here keeps the state `Closed` when the cancellation lands after that publish.
            if (released.get()) publish(SessionState.Closed)
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
     * The successful channel-connect event.
     *
     * On a resumed connection the saved identity already matched, so this is a trusted session: a
     * token the television reissued replaces the stored one atomically. On first contact this is
     * the approval, and the token and pin are persisted in one atomic secret write together with
     * the samsung-private device record.
     */
    private fun onApproved(token: String?) {
        approvalTimer?.cancel()
        channelKeys = STANDARD_REMOTE_KEYS
        // A superseded generation may clean up after itself, but it may not publish Ready and may
        // not persist stale pairing evidence (connection.md#evidence-and-race-handling-harvest).
        if (!generation.isActive()) return
        val saved = resumedFrom
        if (saved != null) {
            if (token != null && token != saved.token) {
                try {
                    secrets.saveSecret(television.id, PairingSecret(token = token, pin = saved.pin))
                } catch (ignored: IOException) {
                    // A replacement that cannot be stored leaves the old pairing in place: the
                    // stored token may be refused next time, which is TokenRejected, not a silent
                    // divergence between the file and the television.
                    return
                }
            }
            publish(SessionState.Ready)
            return
        }
        val pin = connectionPin()
        try {
            persistPairing(token = token, pin = pin)
        } catch (ignored: IOException) {
            // The session itself is alive, but the pairing could not be saved. Fail closed: the
            // user sees that the saved connection needs pairing again, and nothing pretends a
            // pairing exists (data.md#samsung-secret-record).
            publishSecretsUnavailable()
            endUnansweredAttempt()
            return
        }
        publish(SessionState.Ready)
    }

    /**
     * The pin to persist with a first approval: the SPKI this connection presented on the TLS
     * channel, or null on the plaintext channel, whose identity is the protocol UUID saved in the
     * device record instead.
     */
    private fun connectionPin(): String? =
        openConnection?.certificateIdentity?.takeIf { television.tls }

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
            // connection.md: unauthorized after a saved token was sent is TokenRejected. Stop.
            // There is no automatic reconnect and no loop: a loop would re-prompt the television.
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
            // The television ended the socket, or the approval wait ended the attempt.
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
            // The prompt ended without approval: the same recovery as a denial.
            SessionState.AwaitingTvApproval ->
                publish(SessionState.NeedsRepair, RepairReason.ApprovalDenied)
            // Already reported, or already released by the holder: neither is a connection loss.
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
     * `NeedsRepair` without a repair reason is the caller-facing `SecretsUnavailable` surface:
     * saved material exists but could not be used, so pairing again is required. There is no
     * plaintext fallback and no token transmission on this path.
     */
    private fun publishSecretsUnavailable() {
        publish(SessionState.NeedsRepair, repairReason = null)
    }

    private fun publish(state: SessionState, repairReason: RepairReason? = null) {
        mutableSnapshot.value = SessionSnapshot(state, TvCapabilities(channelKeys), repairReason)
    }

    companion object {
        /** connection.md: the approval wait. */
        val APPROVAL_WAIT: Duration = 45.seconds

        /**
         * commands.md#evidence: `Ready` on the adopted channel is the evidence that the standard
         * remote keys are accepted. Per-key rejection tracking arrives with the slice that
         * implements rejection; S03 has none to apply.
         */
        val STANDARD_REMOTE_KEYS: Set<RemoteKey> = RemoteKey.entries.toSet()
    }
}
