# Architecture map

Detailed elaboration of the accepted baseline in `docs/ARCHITECTURE.md`. **This map is the V1 architecture closure for the current architecture round, accepted by the human on 2026-09-23.** The superseded Firestore TV-personalization sync model has been removed from every live document. The pre-S01 gate is human review of the reconciled implementation route in [slices.md](slices.md) (Issue #23), not further architecture reconciliation.

`docs/ARCHITECTURE.md` owns decisions and invariants. This directory owns concrete modules, state, data, presentation, environments, and slices. If a document here conflicts with the baseline, the baseline wins until a later architecture decision changes it.

Product intent stays in `docs/PRODUCT.md`. Canonical domain language is in `CONTEXT.md`. Harvest dispositions stay in `docs/HARVEST.md`. This map does not reopen them.

## Read by branch

Load the file for the branch in front of you. Do not load the whole directory by default.

| Branch | File |
|---|---|
| Where code lives, dependency direction, Hilt, ownership | [modules.md](modules.md) |
| Caller-facing `app` ↔ `samsung` seam | [samsung-interface.md](samsung-interface.md) |
| Bounded discovery, identity, permission gate | [discovery.md](discovery.md) |
| Pairing, security identity, persistent session, reconnect | [connection.md](connection.md) |
| Typed commands, capability evidence, phone input | [commands.md](commands.md) |
| Wire and generation knowledge inside `samsung` | [protocol.md](protocol.md) |
| Room, DataStore, Keystore, migration, corruption recovery | [data.md](data.md) |
| Customer account, seven-day trial, lifetime entitlement, backend topology | [sync.md](sync.md) |
| Routes, screen state, restoration, responsive rules, tokens, accessibility | [presentation.md](presentation.md) |
| Android lifecycle, network transitions, background limits | [lifecycle.md](lifecycle.md) |
| Reliability and performance targets with verification | [reliability.md](reliability.md) |
| Threat model and trust boundaries | [security.md](security.md) |
| Local redacted record, preview, export | [diagnostics.md](diagnostics.md) |
| Test seams, fixtures, backend tests, physical matrix | [testing.md](testing.md) |
| CI, environments, deployment, rollback, release | [release.md](release.md) |
| Cross-module sequences | [flows.md](flows.md) |
| Product surface, navigation, remote interaction, visual and recovery rules | [ui-ux.md](ui-ux.md) |
| Vertical implementation route | [slices.md](slices.md) |

`protocol.md` is for `samsung` implementers. `app` code uses only the types in `samsung-interface.md`. `ui-ux.md` owns product-surface decisions; `presentation.md` owns how they are built.

`sync.md` keeps its historical file name for link stability. It owns account and licensing only; it does not describe any television-data synchronization, because none exists.

## Diagrams

| View | Location |
|---|---|
| Module and system structure | [modules.md](modules.md) |
| First-run permission → discovery → pairing → first control | [flows.md](flows.md) |
| Reconnect and control lifecycle | [connection.md](connection.md) |
| Local storage classes and secret lifecycle | [data.md](data.md) |
| Account, trial, and entitlement lifecycle | [sync.md](sync.md) |
| Backend topology | [sync.md](sync.md) |
| Route graph and launch routing | [presentation.md](presentation.md) |
| Active Remote lifecycle | [lifecycle.md](lifecycle.md) |
| Trust boundaries | [security.md](security.md) |
| Implementation route | [slices.md](slices.md) |

## Invariant demonstration

The binding text is the numbered list in `docs/ARCHITECTURE.md`. This table is only the pointer to where the map keeps each invariant true.

| Invariant | Where the map keeps it |
|---|---|
| 1. Local control independent of cloud | `samsung` has no Firebase, Play, or licensing dependency. The command path never touches the backend. The gate reads cached proofs only. See [modules.md](modules.md), [sync.md](sync.md), [lifecycle.md](lifecycle.md). |
| 2. Samsung protocol complexity inside `samsung` | One external seam, `SamsungTvs`. Wire names stay in [protocol.md](protocol.md). |
| 3. Pairing secrets device-local and Keystore-backed | [data.md](data.md). Secrets are a distinct type in a distinct directory, never in Room, DataStore, or the entitlement cache. |
| 4. Capability-driven UI | Evidence rules in [commands.md](commands.md). Unsupported cards and rejected keys expose no controls. |
| 5. Discovery bounded and visible | [discovery.md](discovery.md). Reconnect rediscovery is internal and never a scan UI. |
| 6. No unnecessary listening server | Client probes and outbound sockets only. See [discovery.md](discovery.md) and [protocol.md](protocol.md). |
| 7. Android optimized for Android | Native Kotlin/Compose/ViewModel stack. iOS is out of scope. |
| 8. No universal TV abstraction | The type is `SamsungTvs`, not a cross-brand adapter. Ecosystem #2 is the trigger for a new seam. |
| 9. TV and remote personalization are device-local; cloud account data is licensing-only | [data.md](data.md) stores no account ownership; [sync.md](sync.md) stores no television, personalization, or behavioral field and lists the forbidden names. |
| 10. No behavioral analytics and no cloud reporting | [diagnostics.md](diagnostics.md): no telemetry dependency at all, bounded redacted local record, explicit user-confirmed export, no upload path. |

## Settled decision ownership

Every settled decision projected into `.project-ai/PROJECT_STATE.md` has an owning canonical path. This table is the check that keeps that true.

| Settled decision group | Owning path |
|---|---|
| Product intent, privacy model, licensing promises, discovery/setup expectations | `docs/PRODUCT.md` |
| Canonical domain terms (Television, Local Pairing, Television Personalization, Interaction Preferences, Active Remote, Customer Account, Username, Trial, Lifetime Entitlement, Forget this TV, and the licensing terms added this round) | `CONTEXT.md` |
| Accepted technical baseline, invariants, platform baseline | `docs/ARCHITECTURE.md` |
| Harvest ADOPT / HARVEST / REJECT dispositions | `docs/HARVEST.md` |
| Android-native stack, module shape, seams, dependency rules, background limits | [modules.md](modules.md), [lifecycle.md](lifecycle.md) |
| Samsung control depth: discovery, pairing, session, commands, protocol | [discovery.md](discovery.md), [connection.md](connection.md), [commands.md](commands.md), [protocol.md](protocol.md), [samsung-interface.md](samsung-interface.md) |
| Device-local personalization, favourites, preferences, last-used television | [data.md](data.md), [presentation.md](presentation.md) |
| Remote-first behavior, failure UX, thumb-first reach, customization rules | [ui-ux.md](ui-ux.md), [presentation.md](presentation.md) |
| Screen/state contracts, routes, restoration, responsive behavior, accessibility contracts | [presentation.md](presentation.md) |
| Account purpose, sign-in paths, Username, trial, purchase, restore, refund, provisional entitlement, paid offline, sign-out, deletion | [sync.md](sync.md) |
| Remote-entry licensing gate and first-session exemption | [sync.md](sync.md), [lifecycle.md](lifecycle.md), [presentation.md](presentation.md) |
| Environment separation, deployment, rollback, release checks | [release.md](release.md) |
| Threat model, guarantees versus best-effort, needs-validation register | [security.md](security.md) |
| Local-only redacted diagnostics and user-confirmed export | [diagnostics.md](diagnostics.md) |
| Test contracts for every contract above | [testing.md](testing.md) |
| Implementation route and slice dependencies | [slices.md](slices.md) |

## Scope interpretations

These apply the baseline. They are not new product decisions. Review can reject an interpretation; the caller-facing seam does not have to change to add a handshake later inside `samsung`.

- V1 control is the Tizen remote-control WebSocket channel on ports 8001 and 8002, including TV-side Allow/Deny and token when that television requires one. Generation choice follows what the television demonstrates, not a year table.
- Legacy encrypted TCP, encrypted PIN/session handshakes, and Consumer IP Control are identified and not sent functional commands. SmartThings is not a control or power path.
- Other brands are not discovered and not probed. A universal adapter is not introduced.
- Diagnostic export is a user-confirmed share of an already redacted report. V1 has no diagnostic upload service.
- Manual address entry is not a V1 path.
- The Android client has no Firestore SDK. Firestore is a server-only datastore reached exclusively through validated endpoints.
- Play carries exactly one artifact: the production-flavoured release candidate is uploaded to the internal testing track and promoted from there, while internal-environment builds are tester builds distributed outside Play. See [release.md](release.md#release-path-and-artifact-identity).
- AppT backup is disabled for application data, so a new phone starts remote state clean; signing in restores identity and entitlement only.

## Harvest application

`docs/HARVEST.md` remains the disposition register. This map does not change ADOPT, HARVEST, or REJECT intent.

Clean-room reimplementation is the one HARVEST practice used while building V1 protocol behavior. It is justified because the adopted WebSocket path must live inside `samsung`, and the usual reference library is LGPL-3.0. The screener already says not to copy incompatible reference code. Using that practice does not decide AppT's license.

No REJECT row is reintroduced. Diagnostic share is not file-sync, and V1 adopts neither ACRA nor any cloud crash reporter: cloud crash reporting was removed from V1 this round, so diagnostics are local-only. There is no `TvAdapter`, no per-brand module, no iOS runtime, no credential sync, no ads, no listening mirror, and no F-Droid commitment.

## Settled privacy, account, and licensing semantics

The owning product decisions are in `docs/PRODUCT.md`, with canonical language in `CONTEXT.md`. [sync.md](sync.md) records the technical architecture.

- the Customer Account exists only for authentication, Username, seven-day trial state, anti-abuse eligibility, and lifetime license entitlement;
- television identity, local pairing, TV names, favourites, remote arrangement, preferences, last-used television, diagnostics, and usage history are not account or backend data;
- Google sign-in is primary, with email/password fallback and required email verification before a trial;
- the first successful remote session remains available before account creation, exactly once;
- the seven-day trial starts from a server-authoritative activation time and follows the account across phones;
- Android V1 uses a Google Play one-time non-consumable lifetime unlock, verified and acknowledged server-side, bound to one live account;
- a validated lifetime entitlement keeps paid local control available offline indefinitely;
- trial abuse protection uses privacy-minimized pseudonymous eligibility signals plus Play Integrity, never television or behavioral data;
- signing out, switching accounts, or deleting an account does not erase or replace device-local television state;
- production V1 has no consumer experimental functional-control mode for non-adopted protocols;
- V1 ships no crash-reporting, behavioral-analytics, advertising, or attribution SDK, and no diagnostic upload path exists in the app or the backend.

## Needs validation

Only provider facts that still require live/provider evidence remain here. Documented provider contracts belong in their owning architecture files rather than staying indefinitely marked unknown.

| Fact | Where it matters |
|---|---|
| Exact `ProductPurchaseV2` representation of promo or rewarded one-time-product acquisitions, **if** either acquisition mode is deliberately enabled for AppT | [sync.md](sync.md), [security.md](security.md), [release.md](release.md) |
| First production Play app-signing key upgrade continuity drill: the new Play signing certificate is registered with App Check before rollout and the Play-delivered build continues to satisfy App Check and Play Integrity after the upgrade | [release.md](release.md), [security.md](security.md) |

## Open items that are not architecture

Pre-existing and not blockers for this map:

- human confirmation of the final source license before public distribution;
- the focused Samsung vendor-terms and legal review before public release;
- physical-device evidence that tunes the reliability targets in [reliability.md](reliability.md).

This map was accepted with the architecture closure on 2026-09-23 and remains the durable implementation map. It deliberately does not project which slice is next; the current accepted milestone and next authorized action live only in `.project-ai/PROJECT_STATE.md`.
