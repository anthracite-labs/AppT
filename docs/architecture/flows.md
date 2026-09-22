# Cross-module flows

State machines, field lists, and rules stay in their owning files. This file is the sequence that crosses modules.

Owning files: reconnect in [connection.md](connection.md), secrets and local data in [data.md](data.md), account/trial/entitlement in [sync.md](sync.md), modules and seams in [modules.md](modules.md), lifecycle transitions in [lifecycle.md](lifecycle.md), screens in [presentation.md](presentation.md).

## First run to first control

No Firebase component is a participant in this path. First success is observed as `Accepted`, which means the command was written to the open session, not that the television visibly acted.

The permission explanation precedes the system prompt and the first scan, in that order.

```mermaid
sequenceDiagram
  actor User
  participant App as AppT
  participant Gate as PermissionGate
  participant Samsung as SamsungTvs
  participant TV as Samsung television
  User->>App: Open AppT
  App->>User: Welcome
  App->>User: Explain local network access
  User->>Gate: Continue
  Gate-->>App: Granted (a system prompt only if a chosen API needs one)
  App->>Samsung: discover
  Samsung->>TV: Bounded client probes
  TV-->>Samsung: Device info confirms a television
  Samsung-->>App: Friendly card
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
  Note over App,TV: No cloud call on this path
```

Failure branches on the same path:

- Permission denied: stay on the explanation. No probe loop.
- No card by the bound: `Finished`, offer rescan. No manual address form.
- `Unsupported`: explain that control is not available and never send a key.
- Approval timeout: `NeedsRepair` with `ApprovalTimedOut`. Retry is user-initiated.
- Identity mismatch on a later open: `NeedsRepair` with `IdentityChanged`. The token is not sent.

## First free control to the account requirement

```mermaid
sequenceDiagram
  participant App as AppT
  participant Host as ActiveRemoteHost
  participant Prefs as PreferenceStore
  participant Storage as Room, secrets
  actor User
  User->>App: Press a control
  App->>Host: command on the retained session
  Host-->>App: Accepted
  App->>Prefs: firstControlAchieved = true
  Note over App: The exempt session continues uninterrupted
  User->>App: Leave Remote
  App->>Host: release (grace)
  Host->>Storage: close socket, keep secrets
  Note over Host: Next entry consults AccountGate
  User->>App: Re-enter a television
  App->>Host: enter(tvId)
  Host-->>App: RequireSignIn
  App->>User: Account continuation surface
```

The exemption belongs to one `ActiveRemote` session. Rotation, a sheet, or a short absence inside the grace window does not create a new entry. Process death ends the exempt session even though the user did not leave.

## Account sign-in and trial activation

```mermaid
sequenceDiagram
  actor User
  participant App as AppT
  participant Auth as AccountAuth
  participant Lic as Licensing
  participant Fn as EntitlementService
  User->>App: Continue with Google or email
  App->>Auth: sign in
  Auth-->>App: uid, verified-email state
  App->>Lic: onSignedIn()
  Lic->>Fn: GET /v1/entitlement
  alt entitlement active
    Fn-->>Lic: lifetime or trial access, signed proof
    Lic->>Lic: cache proof and key set
  else no entitlement yet
    Fn-->>Lic: access none
    Lic->>Fn: POST /v1/trial/activate with integrity token and device signal
    alt eligible
      Fn-->>Lic: trialExpiresAt, signed trial proof
    else already used on this email or device
      Fn-->>Lic: not eligible
      Lic-->>App: trial unavailable, purchase or restore offered
    end
  end
  App-->>User: Account surface shows exact remaining trial time
```

An unverified email/password account cannot activate a trial. Google identities are provider-verified. Nothing about the television is sent in either call.

## Trial expiry and the next remote entry

```mermaid
sequenceDiagram
  actor User
  participant App as AppT
  participant Host as ActiveRemoteHost
  participant Lic as Licensing
  participant Fn as EntitlementService
  User->>App: Press a control during the last minute of the trial
  App->>Host: command
  Host-->>App: Accepted
  Note over App: Expiry never interrupts an active session
  User->>App: Leave, then re-enter
  App->>Host: enter(tvId)
  Host->>Lic: evaluate access
  alt backend reachable
    Lic->>Fn: GET /v1/entitlement
    Fn-->>Lic: trial expired, no lifetime
    Host-->>App: RequireEntitlement
    App-->>User: Entitlement surface with Buy once and Restore purchase
  else backend unreachable
    Host-->>App: RequireEntitlement(retryable)
    App-->>User: Honest licensing error, explicitly not a television problem
  end
```

While a cached trial proof is unexpired, the app may run entirely offline: the local known expiry is the rule.

## Purchase

```mermaid
sequenceDiagram
  actor User
  participant App as AppT
  participant Play as PlayBilling
  participant Lic as Licensing
  participant Fn as EntitlementService
  participant Api as Play Developer API
  User->>App: Buy once
  App->>Play: buyLifetime()
  Play-->>App: purchase (PURCHASED or PENDING)
  alt PENDING
    App-->>User: Waiting for Google Play, no entitlement yet
  else PURCHASED
    App->>Lic: verifyPurchase(purchase)
    Lic->>Fn: POST /v1/purchase/verify
    Fn->>Api: purchases.products.get
    Api-->>Fn: purchased, standard purchase type
    Fn->>Api: acknowledge
    Fn-->>Lic: granted, signed lifetime proof
    Lic->>Lic: cache proof
    App-->>User: Lifetime unlocked, remote entries allowed offline from here
  end
```

If the validator is unavailable while Play reports `PURCHASED`, the client writes a 24-hour non-renewable provisional record and shows it as temporary. A later authoritative answer replaces it.

## Restore purchase, including after account deletion

```mermaid
sequenceDiagram
  actor User
  participant App as AppT
  participant Play as PlayBilling
  participant Lic as Licensing
  participant Fn as EntitlementService
  User->>App: Restore purchase
  App->>Play: query purchases
  Play-->>App: lifetime purchase token (if this Play account owns one)
  App->>Lic: restorePurchase()
  Lic->>Fn: POST /v1/purchase/verify (source restore)
  alt binding is live on this account, or was released by account deletion
    Fn-->>Lic: granted, signed lifetime proof
  else binding is live on a different live account
    Fn-->>Lic: bound elsewhere
    Lic-->>App: Explain honestly, offer support
  else revoked
    Fn-->>Lic: already revoked
    Lic-->>App: Explain the refund or withdrawal
  end
```

Deleting an AppT account does not destroy the Play purchase. After deletion the binding is released, so recreating an account and restoring by the legitimate Play owner works.

## Restore on a second phone

A second phone signed into the same account receives identity, Username, trial state, and Lifetime Entitlement. It receives no televisions, names, favourites, arrangement, preferences, last-used television, or pairing material, so it discovers and pairs independently. A trial that is already running keeps its original expiry.

## Refund or chargeback

```mermaid
sequenceDiagram
  participant Play as Google Play
  participant PubSub as Cloud Pub/Sub
  participant Fn as EntitlementService
  participant Db as Firestore
  participant App as AppT
  Play->>PubSub: voided purchase or one-time product canceled
  PubSub->>Fn: authenticated push
  Fn->>Db: mark binding revoked, account entitlement revoked
  Note over App: No active session is touched
  App->>Fn: next remote entry, online refresh
  Fn-->>App: revoked
  App-->>App: Entitlement surface with Restore purchase
```

Revocation, like expiry, applies between sessions.

## Backend outage during control

```mermaid
sequenceDiagram
  participant App as AppT
  participant Samsung as SamsungTvs
  participant TV as Samsung television
  participant Fn as EntitlementService
  App->>Samsung: command
  Samsung->>TV: write
  Samsung-->>App: Accepted
  App--xFn: entitlement refresh fails
  Note over App,Samsung: Session state unchanged, command path untouched
```

The account gate treats a cached identity and cached proof as valid offline. It never calls the backend to discover whether a signed-in user is signed in.

## Quiet reconnect and an address change

```mermaid
sequenceDiagram
  actor User
  participant App as AppT
  participant Sam as SamsungTvs
  participant TV as Samsung television
  Note over Sam,TV: Session already Ready
  User->>App: Direction key
  App->>Sam: command
  Sam->>TV: write immediately
  TV--xSam: socket closes
  Sam-->>App: Reconnecting
  Note over App: Lightweight status, not a dialog
  Sam->>TV: backoff, then connect
  alt address refused
    Sam->>TV: internal rediscovery for the saved identity
  end
  TV-->>Sam: trusted connect
  Sam-->>App: Ready
  User->>App: Leave remote
  Note over App: 15 second grace
  App->>Sam: close
  Note over Sam: secrets remain
```

If the budget ends, the state is `Unreachable` and automatic attempts stop. The user may try again, or wake when `powerOn` is `Attemptable`.

## Process death and reopen

```mermaid
sequenceDiagram
  actor User
  participant App as AppT
  participant Host as ActiveRemoteHost
  participant Resolver as StartDestinationResolver
  participant Gate as AccountGate
  User->>App: Reopen after the process was killed
  App->>Resolver: resolveLaunch(permission, remembered, lastOpenedTvId, gate)
  Resolver->>Gate: decide(tvId)
  alt first control not yet achieved
    Gate-->>Resolver: Allow
    Resolver-->>App: Remote(tvId), new session
  else first control achieved and no account
    Gate-->>Resolver: RequireSignIn
    Resolver-->>App: Account(EntryOrigin.Launch)
  else signed in with cached access
    Gate-->>Resolver: Allow
    Resolver-->>App: Remote(tvId), quiet reopen
  end
```

Process death is never treated as an identity change: no re-pair prompt appears, and saved secrets remain valid.

## Switching televisions

```mermaid
sequenceDiagram
  actor User
  participant App as AppT
  participant Sheet as TvSwitchSheet
  participant Host as ActiveRemoteHost
  participant Gate as AccountGate
  User->>App: Tap the current-TV affordance
  App->>Sheet: open with remembered televisions
  User->>Sheet: Select another television
  Sheet->>Host: release current, enter(target)
  Host->>Gate: decide(target)
  alt allowed
    Gate-->>Host: Allow
    Host-->>App: Remote(target), session opens
  else blocked
    Gate-->>Host: RequireSignIn or RequireEntitlement
    Host-->>App: Account or Entitlement with EntryOrigin.Remote
  end
```

There is no horizontal swipe between televisions; the switch is always an explicit selection.

## Sign-out

```mermaid
sequenceDiagram
  actor User
  participant App as AppT
  participant Auth as AccountAuth
  participant Lic as Licensing
  participant Storage as Room, Samsung files
  User->>App: Sign out
  App->>App: confirmation dialog stating local televisions stay
  App->>Auth: signOut()
  App->>Lic: clear account identity, keep cached proof and key set
  App->>Storage: nothing changes
  App->>App: cancel EntitlementRefresh work
  Note over App: Next new remote entry requires sign-in
```

An active remote session is allowed to finish. Sign-out does not delete the cached entitlement proof, television rows, favourites, preferences, or pairing secrets.

## Account deletion

```mermaid
sequenceDiagram
  actor User
  participant App as AppT
  participant Lic as Licensing
  participant Fn as EntitlementService
  participant Auth as FirebaseAuth
  participant Storage as Room, Samsung files
  User->>App: Delete account
  App->>App: confirmation stating what is deleted and what remains on the phone
  App->>Lic: deleteAccount()
  Lic->>Fn: POST /v1/account/delete
  Fn->>Fn: delete account record, release purchase binding, retain markers and binding
  Fn->>Auth: delete user (last, so a retry stays possible)
  Fn-->>Lic: deleted
  Lic->>Lic: clear cached proof, drop identity
  App->>Storage: nothing changes
  App-->>User: Deleted. Local televisions remain on this phone
```

The account backend retains only pseudonymous trial markers and the released purchase binding. Television data was never there to delete.

## Diagnostics opt-in and export

```mermaid
sequenceDiagram
  actor User
  participant App as AppT
  participant Consent as DiagnosticsConsent
  participant Crash as Crashlytics
  User->>App: Open Privacy and Diagnostics
  App-->>User: Crash reporting is off, with an explanation of what would be sent
  User->>Consent: Enable crash reporting
  Consent->>Crash: enable collection from the next launch
  App-->>User: Offer to send or delete reports stored on the device
  User->>App: Request a diagnostic export
  App->>App: build allowlisted report from redacted diagnostics
  App-->>User: Preview of every field leaving the phone
  User->>App: Confirm
  App->>App: system share sheet
```

No AppT service receives a diagnostic report in V1.
