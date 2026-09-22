# Architecture map

Detailed elaboration of the accepted baseline in `docs/ARCHITECTURE.md`. **This map is currently under architecture revision and is not yet implementation-ready.**

`docs/ARCHITECTURE.md` owns decisions and invariants. This directory owns concrete modules, state, data, and slices. If a document here conflicts with the baseline, the baseline wins until a later architecture decision changes it.

Product intent stays in `docs/PRODUCT.md`. Canonical domain language is in `CONTEXT.md`. Harvest dispositions stay in `docs/HARVEST.md`. This map does not reopen them.

## Read by branch

Load the file for the branch in front of you. Do not load the whole directory by default.

| Branch | File |
|---|---|
| Where code lives, dependency direction, Hilt, ownership | [modules.md](modules.md) |
| Caller-facing `app` ↔ `samsung` seam | [samsung-interface.md](samsung-interface.md) |
| Bounded discovery, identity, dedup, permission gate | [discovery.md](discovery.md) |
| Pairing, security identity, persistent session, reconnect | [connection.md](connection.md) |
| Typed commands, capability evidence, phone input | [commands.md](commands.md) |
| Wire and generation knowledge inside `samsung` | [protocol.md](protocol.md) |
| Room, DataStore, Keystore, secret lifecycle | [data.md](data.md) |
| Customer account, seven-day trial, lifetime entitlement, privacy boundaries | [sync.md](sync.md) |
| Crashlytics, redaction, export | [diagnostics.md](diagnostics.md) |
| Test seams, fixtures, physical matrix | [testing.md](testing.md) |
| CI, supply chain, Play release | [release.md](release.md) |
| Cross-module sequences | [flows.md](flows.md) |
| Vertical implementation route | [slices.md](slices.md) |

`protocol.md` is for `samsung` implementers. `app` code uses only the types in `samsung-interface.md`.

## Diagrams

| View | Location |
|---|---|
| Module and system structure | [modules.md](modules.md) |
| First-run permission → discovery → pairing → first control | [flows.md](flows.md) |
| Reconnect and control lifecycle | [connection.md](connection.md) |
| Secret lifecycle | [data.md](data.md) |
| Account, trial, and entitlement lifecycle | [sync.md](sync.md) |

## Invariant demonstration

The binding text is the numbered list in `docs/ARCHITECTURE.md`. This table is only the pointer to where the map keeps each invariant true.

| Invariant | Where the map keeps it |
|---|---|
| 1. Local control independent of cloud | `samsung` has no Firebase dependency. Session open checks no cloud service. Account gate reads the local Auth cache only. See [modules.md](modules.md), [sync.md](sync.md), [flows.md](flows.md). |
| 2. Samsung protocol complexity inside `samsung` | One external seam, `SamsungTvs`. Wire names stay in [protocol.md](protocol.md). |
| 3. Pairing secrets device-local and Keystore-backed | [data.md](data.md). Secrets are not a `SyncRecord`. |
| 4. Capability-driven UI | Evidence rules in [commands.md](commands.md). Unsupported cards expose no command controls. |
| 5. Discovery bounded and visible | [discovery.md](discovery.md). Reconnect rediscovery is internal and not a scan UI. |
| 6. No unnecessary listening server | Client probes and outbound sockets only. See [discovery.md](discovery.md) and [protocol.md](protocol.md). |
| 7. Android optimized for Android | Native Kotlin/Compose stack. No shared iOS runtime. iOS is out of scope. |
| 8. No universal TV abstraction | The type is `SamsungTvs`, not a cross-brand adapter. Ecosystem #2 is the trigger for a new seam. |
| 9. TV and remote personalization are device-local; cloud account data is licensing-only | [sync.md](sync.md). The remaining data/slice docs are being reconciled to remove the superseded TV-sync model. |
| 10. No behavioral analytics | Crashlytics only, no Analytics dependency. See [diagnostics.md](diagnostics.md). |

## Scope interpretations

These apply the baseline. They are not new product decisions. Review can reject an interpretation; the caller-facing seam does not have to change to add a handshake later inside `samsung`.

- V1 control is the Tizen remote-control WebSocket channel on ports 8001 and 8002, including TV-side Allow/Deny and token when that television requires one. Generation choice follows what the television demonstrates, not a year table.
- Legacy encrypted TCP, encrypted PIN/session handshakes, and Consumer IP Control are identified and not sent functional commands. SmartThings is not a control or power path.
- Other brands are not discovered and not probed. A universal adapter is not introduced.
- Diagnostic export is a user-confirmed share of an already redacted report. V1 has no diagnostic upload service.
- Manual address entry is not a V1 path.

## Harvest application

`docs/HARVEST.md` remains the disposition register. This map does not change ADOPT, HARVEST, or REJECT intent.

Clean-room reimplementation is the one HARVEST practice used while building V1 protocol behavior. It is justified because the adopted WebSocket path must live inside `samsung`, and the usual reference library is LGPL-3.0. The screener already says not to copy incompatible reference code. Using that practice does not decide AppT's license.

No REJECT row is reintroduced. Diagnostic share is not file-sync. Crashlytics is the accepted reporter, not ACRA. There is no `TvAdapter`, no per-brand module, no iOS runtime, no credential sync, no ads, no listening mirror, and no F-Droid commitment.

## Settled privacy, account, and licensing semantics

The owning product decisions are in `docs/PRODUCT.md`, with canonical language in `CONTEXT.md`. [sync.md](sync.md) records the current account/trial/entitlement architecture.

- the Customer Account exists only for authentication, username, seven-day trial state, anti-abuse eligibility, and lifetime license entitlement;
- television identity, local pairing, TV names, favourites, remote arrangement, preferences, last-used TV, diagnostics, and usage history are not account/backend data;
- Google sign-in is primary, with email/password fallback;
- the first successful remote session remains available before account creation;
- the seven-day trial starts from a server-authoritative activation time;
- Android V1 uses a Google Play one-time non-consumable lifetime unlock;
- a validated lifetime entitlement keeps paid local control available offline indefinitely;
- trial anti-abuse uses privacy-minimized pseudonymous eligibility signals and Play Integrity, not TV or behavioral data;
- switching/signing out/deleting an account does not erase or replace device-local television state;
- production V1 has no consumer experimental functional-control mode for non-adopted protocols; internal/debug research may probe experimental implementations.

The previous Firestore TV-personalization sync model is superseded.

## Map reconciliation required

Several detailed files still encode the removed sync model and **must not be treated as implementation-ready** until this architecture round reconciles them. In particular:

- `data.md` still contains sync tuples, `pendingSync`, tombstones, and account-scoped TV/favourite assumptions that must be removed or redefined;
- `modules.md`, `flows.md`, `testing.md`, and `release.md` need account/licensing/backend-environment updates;
- `slices.md` still contains the old S10/S11 account + TV-sync implementation route and must be redesigned;
- presentation/navigation, domain behavior, threat model, backend deployment, lifecycle/network transitions, performance budgets, and persisted-format migration architecture remain open.

Pre-existing and not blockers for this map: Samsung vendor-terms review before public release, and confirmation of the final source license before public distribution. The repository contains an MIT `LICENSE` file; `docs/HARVEST.md` still treats the final license as undecided. This map does not choose it.
