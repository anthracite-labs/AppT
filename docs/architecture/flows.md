# Cross-module flows

State machines and field lists stay in their owning files. This file is the sequence that crosses modules.

Owning files: reconnect in [connection.md](connection.md), secrets in [data.md](data.md), account and sync in [sync.md](sync.md), modules in [modules.md](modules.md).

## First run to first control

Firebase is not a participant. Sign-in happens after the first accepted command, in [sync.md](sync.md).

The product list names discovery before the permission sentence. The baseline orders them as explanation, then local-network access, then automatic discovery. This sequence follows the baseline.

```mermaid
sequenceDiagram
  actor User
  participant App
  participant Gate as PermissionGate
  participant Samsung as SamsungTvs
  participant TV as SamsungTV
  User->>App: Open AppT
  App->>User: Welcome
  App->>User: Explain local network access
  User->>Gate: Continue
  Gate->>User: System prompt when the API level requires one
  Gate->>App: Granted
  App->>Samsung: discover
  Samsung->>TV: Bounded client probes
  TV-->>Samsung: Device info confirms a television
  Samsung-->>App: Found friendly card
  User->>App: Choose television
  App->>Samsung: open
  Samsung->>TV: WebSocket handshake without a saved token
  TV-->>User: Allow AppT
  User->>TV: Allow
  TV-->>Samsung: Channel connect and token
  Samsung-->>App: Ready
  User->>App: Volume
  App->>Samsung: command
  Samsung->>TV: Write on the open session
  Samsung-->>App: Accepted
  Note over App,TV: No Firebase call on this path
```

Failure branches on the same path:

- Permission denied: stay on the explanation. No probe loop.
- No card by the bound: `Finished`, offer rescan. No manual address form.
- `Unsupported`: explain that control is not available. No key is sent.
- Approval timeout: `NeedsRepair` with `ApprovalTimedOut`. User may retry. No automatic re-prompt loop.
- Identity mismatch on a later open: `NeedsRepair` with `IdentityChanged`. Token is not sent. User must confirm re-pair.

## Active control and quiet reconnect

```mermaid
sequenceDiagram
  actor User
  participant App
  participant Samsung as SamsungTvs
  participant TV as SamsungTV
  Note over Samsung,TV: Session already Ready
  User->>App: Direction key
  App->>Samsung: command
  Samsung->>TV: Write immediately
  TV --x Samsung: Socket closes
  Samsung-->>App: Reconnecting
  Note over App: Lightweight status, not a dialog
  Samsung->>TV: Backoff then connect
  alt Address refused
    Samsung->>TV: Internal rediscovery for the saved identity
  end
  TV-->>Samsung: Trusted connect
  Samsung-->>App: Ready
  User->>App: Leave remote
  Note over App: 15 second grace
  App->>Samsung: close
  Note over Samsung: Secret remains
```

If the budget ends, state is `Unreachable` and automatic attempts stop. The user may try again, or wake when `powerOn` is `Attemptable`.

## Cloud outage during control

```mermaid
sequenceDiagram
  participant App
  participant Samsung as SamsungTvs
  participant TV as SamsungTV
  participant Cloud as Firestore
  App->>Samsung: command
  Samsung->>TV: Write
  Samsung-->>App: Accepted
  App --x Cloud: Sync worker fails
  Note over App,Samsung: Session state unchanged
```

The account gate treats a cached Firebase user as signed in when the cloud is down. It does not call the cloud to find that out.
