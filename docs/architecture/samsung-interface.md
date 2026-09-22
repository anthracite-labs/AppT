# `app` ↔ `samsung` interface

This file is the external **interface** of the `samsung` **module**. Everything a caller must know is here. Wire format, ports, `KEY_*` strings, discovery packets, token layout, TLS mechanics, and retry math are not part of this interface. They live in [protocol.md](protocol.md), [discovery.md](discovery.md), and [connection.md](connection.md) for implementers inside the module.

Package: `dev.anthracite.appt.samsung`. `app` may import this package only. `app` may not import `samsung.internal`.

## Contract

```kotlin
interface SamsungTvs {
    fun discover(): Flow<DiscoveryEvent>
    fun open(id: TvId, scope: CoroutineScope): RemoteSession
    suspend fun wake(id: TvId): WakeResult
    suspend fun forget(id: TvId): ForgetResult
    fun rememberedIds(): Set<TvId>
    fun redactedDiagnostics(): RedactedDiagnosticReport
}

interface RemoteSession {
    val snapshot: StateFlow<SessionSnapshot>
    suspend fun command(command: TvCommand): CommandResult
    suspend fun retryApproval()
    suspend fun confirmRepair()
    fun close()
}
```

`SamsungTvs` is the primary test surface. `app` tests and `samsung` behavior tests cross this seam. Internal transports are a test detail of the `samsung` module.

## Types callers must know

```kotlin
@JvmInline value class TvId(val value: String)

data class DiscoveredTv(
    val id: TvId,
    val name: String,
    val remembered: Boolean,
    val correlatable: Boolean,
    val availability: ControlAvailability,
)

enum class ControlAvailability { NeedsPairing, ReadyToOpen, Unsupported }

sealed interface DiscoveryEvent {
    data class Found(val tv: DiscoveredTv) : DiscoveryEvent
    data object Finished : DiscoveryEvent
    data class Failed(val failure: TvFailure) : DiscoveryEvent
}

data class SessionSnapshot(
    val state: SessionState,
    val capabilities: TvCapabilities,
    val launchableApps: List<LaunchableApp>,
    val repairReason: RepairReason?,
)

sealed interface SessionState {
    data object Connecting : SessionState
    data object AwaitingTvApproval : SessionState
    data object Ready : SessionState
    data object Reconnecting : SessionState
    data object NeedsRepair : SessionState
    data object Unreachable : SessionState
    data object Unsupported : SessionState
    data object Closed : SessionState
}

enum class RepairReason {
    ApprovalDenied,
    ApprovalTimedOut,
    TokenRejected,
    IdentityChanged,
}

data class TvCapabilities(
    val keys: Set<RemoteKey>,
    val pointer: Boolean,
    val textInput: Boolean,
    val apps: Boolean,
    val powerOn: PowerOn,
    val powerOff: Boolean,
)

enum class PowerOn { Attemptable, Unavailable }

enum class RemoteKey {
    Up, Down, Left, Right, Enter, Back, Home,
    VolumeUp, VolumeDown, Mute, Power,
    Play, Pause, Stop, Rewind, FastForward,
    ChannelUp, ChannelDown, Source,
    Red, Green, Yellow, Blue,
    Number0, Number1, Number2, Number3, Number4,
    Number5, Number6, Number7, Number8, Number9,
}

sealed interface TvCommand {
    data class Tap(val key: RemoteKey) : TvCommand
    data class Hold(val key: RemoteKey, val duration: Duration) : TvCommand
    data class PointerMove(val dx: Int, val dy: Int) : TvCommand
    data object PointerClick : TvCommand
    data class InsertText(val text: String) : TvCommand
    data class LaunchApp(val app: AppId) : TvCommand
}

@JvmInline value class AppId(val value: String)

data class LaunchableApp(
    val id: AppId,
    val name: String,
    val wellKnown: WellKnownApp?,
)

enum class WellKnownApp {
    Netflix, YouTube, PrimeVideo, DisneyPlus, Spotify, Browser,
}

sealed interface CommandResult {
    data object Accepted : CommandResult
    data class Rejected(val failure: TvFailure) : CommandResult
}

sealed interface WakeResult {
    data object Sent : WakeResult
    data object Unavailable : WakeResult
    data class Failed(val failure: TvFailure) : WakeResult
}

sealed interface ForgetResult {
    data object Forgotten : ForgetResult
    data object Failed : ForgetResult
}

sealed interface TvFailure {
    data object LocalNetworkDenied : TvFailure
    data object Unreachable : TvFailure
    data object TimedOut : TvFailure
    data object NeedsRepair : TvFailure
    data object Unsupported : TvFailure
    data object Rejected : TvFailure
    data object Unavailable : TvFailure
    data object SecretsUnavailable : TvFailure
}

data class RedactedDiagnosticReport(val events: List<RedactedEvent>)

data class RedactedEvent(
    val elapsedMs: Long,
    val name: String,
    val fields: Map<String, String>,
)
```

`TvId`, `AppId`, and `RemoteKey` are opaque to callers. Callers do not parse `TvId` for a MAC, address, or protocol generation. `correlatable` is the flag sync uses; see [data.md](data.md).

No caller-facing type contains an address, MAC, token, certificate, Wi-Fi name, or raw payload.

## Operations

### `discover`

Starts one bounded scan and emits `Found` cards as televisions are confirmed, then `Finished`. Cards are friendly names, never addresses.

- A card is emitted only after the module has confirmed a television identity. At most one `Found` per `TvId` per scan.
- `remembered` means a secret or saved identity exists for that id.
- `ReadyToOpen` means `open` should resume a saved pairing.
- `NeedsPairing` means the adopted control path is available and no saved approval exists yet.
- `Unsupported` means the television was identified and the adopted control path is not available. The card is for honesty and later support, not for commands.
- Collecting the flow starts the scan. Cancelling collection stops probes and releases the multicast lock.
- Only one scan runs. A new `discover()` cancels the previous scan. The previous flow is cancelled, not failed.
- The scan always runs to its bound unless cancelled. Callers do not pass a duration.
- Reconnect does not call `discover()` and does not emit discovery events. Address repair during reconnect is internal. See [connection.md](connection.md).

Ordering: the permission gate must be granted before `discover()`. If it is not, the flow emits `Failed(LocalNetworkDenied)` and does not launch permission UI.

### `open`

Starts or resumes the persistent session for one television. The initial snapshot is `Connecting`, never null.

- Call with a `TvId` from `discover()` or `rememberedIds()`.
- Pass the caller's scope. Cancelling that scope closes the session and does not delete secrets.
- `open` on `Unsupported` moves to `Unsupported` and sends no functional command.
- `open` on an unknown id moves to `Unreachable` and sends no token.
- A recent successful `wake` for the same id extends the initial connect patience for the rest of the wake window. Callers do not pass timeouts. The window is specified in [connection.md](connection.md).
- While `Ready`, `command` writes on the already open session. It does not pay a new handshake.

### `command`

Sends one typed command.

- Returns `Accepted` when the command has been written to the live session, not when the television has visibly acted. The television often does not acknowledge a key. The account gate uses the first `Accepted` as its observable proxy for the first successful local-control session. That proxy is a socket write. Do not invent an acknowledgement. See [sync.md](sync.md).
- Returns `Rejected` for expected failures. It does not throw those failures.
- Throws `CancellationException` only when the calling coroutine is cancelled.
- Cancelling one `command` does not close the session.
- Calls are safe from the main thread and safe if overlapped. Overlapped calls are ordered, not parallel. A `Hold` occupies the session until release, including release on cancellation.
- If the write buffer already holds 32 unsent frames, further commands return `Rejected(Unavailable)` instead of growing a burst.
- `Hold` longer than 10 seconds is clamped to 10 seconds. Callers must not assume a longer hold is delivered.
- `InsertText` longer than 256 characters, or empty, returns `Rejected(Rejected)` and sends nothing. The module does not send a truncated prefix.
- `PointerMove` deltas are clamped to -127..127.
- Commands while the state is not `Ready` return `Rejected(Unavailable)` immediately. Nothing is queued for later replay.

### `retryApproval` and `confirmRepair`

Both update `snapshot` and return when the resulting transition has been applied. They do not throw. In a state where the call does not apply, snapshot is unchanged.

| Call | Applies when | Effect |
|---|---|---|
| `retryApproval` | `NeedsRepair` and reason is `ApprovalDenied` or `ApprovalTimedOut` | Ask the television for approval again. Saved security identity is kept. |
| `retryApproval` | `NeedsRepair` and reason is `TokenRejected` or `IdentityChanged` | Ignored. Those reasons require `confirmRepair`. |
| `confirmRepair` | `NeedsRepair` and reason is `TokenRejected` or `IdentityChanged` | Discard the saved approval, then pair again. This is the explicit re-pair. |
| `confirmRepair` | any other state | Ignored. A healthy session is not dropped by this call. |

`app` must not call `confirmRepair` without an explicit user confirmation. Required meaning: the saved approval no longer matches the television; continue only if this is the television the user means. The UI has no "ignore security error" action. Copy does not mention certificates unless the user opens a secondary explanation.

### `wake`

Transmits a wake attempt for a remembered television. `Sent` means the packet was transmitted, not that the panel is on. `Unavailable` means wake is not attemptable. Callers that receive `Sent` then `open` the same id.

### `forget`

Deletes pairing material, security identity, and samsung-private device records for that id. Idempotent. Safe to retry. Does not delete Room rows; `app` does that. `Forgotten` means no secret remains. `Failed` means the caller must retry before treating the television as forgotten.

### `rememberedIds`

Ids for which samsung-private records exist. `app` uses this at startup to retry `forget` for ids whose `localUnpairPending` flag is set. It is not a UI list, and it is not a list of remote tombstones. See [data.md](data.md).

### `redactedDiagnostics`

Returns the module's already redacted event buffer. Fields obey [diagnostics.md](diagnostics.md). `app` may share that report after explicit user confirmation. `app` must not enrich it with addresses, account ids, or secrets.

## Observable states

`repairReason` is non-null only in `NeedsRepair`. `launchableApps` is empty unless `capabilities.apps` is true. Capabilities while not `Ready` still reflect the last evidence for that television, so the UI can hide known-dead controls during reconnect instead of flashing a full remote.

| State | Caller-visible meaning |
|---|---|
| `Connecting` | Opening the session. Not an error. |
| `AwaitingTvApproval` | The user must allow AppT on the television. |
| `Ready` | Commands write immediately on the open session. |
| `Reconnecting` | Lightweight non-modal status. Automatic, bounded, quiet. |
| `NeedsRepair` | User action required. Branch on `repairReason`. Do not auto-call `confirmRepair`. |
| `Unreachable` | Bounded recovery ended, or the television cannot be reached. Not a modal loop. |
| `Unsupported` | No adopted control path. No command controls. |
| `Closed` | Holder released the session. Secrets, if any, remain. |

User-visible status for `Reconnecting` uses a lightweight "Reconnecting" treatment, not a repeated dialog. `samsung` does not choose Compose layout. It does guarantee the state is observable without polling.

## Normalized failures

Expected television and network failures cross the seam as `TvFailure` values on flows and results. They do not cross as thrown exceptions, and they do not crash the caller.

| Failure | When |
|---|---|
| `LocalNetworkDenied` | LAN access is blocked or the gate was not granted |
| `Unreachable` | No route within the bound |
| `TimedOut` | A bounded wait ended |
| `NeedsRepair` | Approval, token, or identity requires user action |
| `Unsupported` | No adopted control path, or `open` was asked to command one |
| `Rejected` | The television rejected this command, or the command was invalid to send |
| `Unavailable` | Session not ready, capability absent, or write buffer full |
| `SecretsUnavailable` | Saved material could not be decrypted. No plaintext fallback |

Malformed television traffic is handled inside the module. Callers see the session continue, or see `Reconnecting` / `Unreachable`. They never see a parse exception.

## Cancellation and lifecycle

- Cancel `discover` collection: probes stop, multicast lock released, no further events.
- Cancel `open`'s scope, or call `close`: socket closes, reconnect stops, in-flight `Hold` sends release, secrets remain.
- Cancel `command`: that command stops. A `Hold` that already sent press still sends release. The session stays up.
- Process death: in-memory sessions are gone. Saved secrets remain. The next `open` of a remembered id resumes without treating process death as an identity change.
- `close` is not `forget`.

`app` releases the session 15 seconds after the remote surface stops, as specified in [modules.md](modules.md).

## Concurrency and performance

- Main-safe. Snapshot updates are observable on the main dispatcher.
- While `Ready`, a command write starts on the open socket. Target: no handshake and no cloud call on that path.
- Write timeout is internal. Callers see `Accepted` or `Rejected` without a caller-supplied deadline.
- `command` does not wait on diagnostics, Room sync, or Firebase.
- Diagnostics inside the module are enqueue-and-forget. A full diagnostic buffer drops events. It does not block `command`.

## Invariants

1. Callers can exercise discovery, pairing, control, reconnect, wake, and forget without learning wire mechanics.
2. `command` during `Ready` does not open a socket and does not contact Firebase.
3. A token is never sent to a television whose saved security identity does not match. See [connection.md](connection.md).
4. `Unsupported` and unknown ids receive no functional commands.
5. `InsertText` is all-or-nothing.
6. `Hold` releases on completion and on cancellation.
7. Results and snapshots contain no secrets, addresses, MACs, or Wi-Fi names.
8. `confirmRepair` is the only operation that replaces a saved security identity, and only from `NeedsRepair`.
9. `forget` is the only operation that deletes a saved secret, and it is idempotent.
10. Expected failures are values, not crashes.

## What callers owe the module

- Do not call `discover` or `open` before the permission gate is granted, except to handle `LocalNetworkDenied` if a race loses the grant.
- Do not call `open` when the account gate forbids continued use. The module will still work; the gate is app policy so a cloud outage cannot be enforced inside `samsung`. See [sync.md](sync.md).
- Do not call `forget` because a remote television tombstone won. `forget` is only for a local user unpair, retried while `localUnpairPending` is set. See [sync.md](sync.md) and [data.md](data.md).
- Do not log `TvCommand.InsertText.text`, discovery names in crash reports, or `redactedDiagnostics` fields plus extra identifiers.
- Do not add a second Samsung client beside this interface.

## Test surface

Drive tests through `SamsungTvs` and `RemoteSession`. Assert on `DiscoveryEvent`, `SessionSnapshot`, `CommandResult`, `WakeResult`, and `ForgetResult`.

`app` tests use the fake adapter and do not assert frame bytes. `samsung` contract tests may also read frames from the fake session transport. Those assertions stay in the `samsung` test source set. See [testing.md](testing.md).
