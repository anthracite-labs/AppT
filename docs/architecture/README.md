# Architecture map

Detailed elaboration of the accepted baseline in `docs/ARCHITECTURE.md`. **This map is the V1 architecture closure after the lifecycle/evidence reconciliation accepted by the human on 2026-09-28.** The superseded television-personalization sync model and standalone harvest register are removed from live architecture; their history remains recoverable from Git. The current implementation route is [slices.md](slices.md), with accepted S01–S03 history preserved and the future route reconciled from S04 onward.

`docs/ARCHITECTURE.md` owns decisions and invariants. This directory owns concrete modules, state, data, presentation, environments, and slices. If a document here conflicts with the baseline, the baseline wins until a later architecture decision changes it.

Product intent stays in `docs/PRODUCT.md`. Canonical domain language is in `CONTEXT.md`. External/provider evidence and open-source implementation harvests live in the architecture document that owns the behavior they support; this map defines the evidence format and does not act as a separate harvest register.

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
| Customer account, seven-day trial, lifetime entitlement, backend topology | [account-entitlement.md](account-entitlement.md) |
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

`account-entitlement.md` owns account, trial, Play purchase, entitlement, and the remote-entry licensing gate. Television state is never synchronized through that architecture.

## Diagrams

| View | Location |
|---|---|
| Module and system structure | [modules.md](modules.md) |
| First-run permission → discovery → pairing → first control | [flows.md](flows.md) |
| Reconnect and control lifecycle | [connection.md](connection.md) |
| Local storage classes and secret lifecycle | [data.md](data.md) |
| Account, trial, and entitlement lifecycle | [account-entitlement.md](account-entitlement.md) |
| Backend topology | [account-entitlement.md](account-entitlement.md) |
| Route graph and launch routing | [presentation.md](presentation.md) |
| Active Remote lifecycle | [lifecycle.md](lifecycle.md) |
| Trust boundaries | [security.md](security.md) |
| Implementation route | [slices.md](slices.md) |

## Invariant demonstration

The binding text is the numbered list in `docs/ARCHITECTURE.md`. This table is only the pointer to where the map keeps each invariant true.

| Invariant | Where the map keeps it |
|---|---|
| 1. Local control independent of cloud | `samsung` has no Firebase, Play, or licensing dependency. The command path never touches the backend. The gate reads cached proofs only. See [modules.md](modules.md), [account-entitlement.md](account-entitlement.md), [lifecycle.md](lifecycle.md). |
| 2. Samsung protocol complexity inside `samsung` | One external seam, `SamsungTvs`. Wire names stay in [protocol.md](protocol.md). |
| 3. Pairing secrets device-local and Keystore-backed | [data.md](data.md). Secrets are a distinct type in a distinct directory, never in Room, DataStore, or the entitlement cache. |
| 4. Capability-driven UI | Evidence rules in [commands.md](commands.md). Unsupported cards and rejected keys expose no controls. |
| 5. Discovery bounded and visible | [discovery.md](discovery.md). Reconnect rediscovery is internal and never a scan UI. |
| 6. No unnecessary listening server | Client probes and outbound sockets only. See [discovery.md](discovery.md) and [protocol.md](protocol.md). |
| 7. Android optimized for Android | Native Kotlin/Compose/ViewModel stack. iOS is out of scope. |
| 8. No universal TV abstraction | The type is `SamsungTvs`, not a cross-brand adapter. Ecosystem #2 is the trigger for a new seam. |
| 9. TV and remote personalization are device-local; cloud account data is licensing-only | [data.md](data.md) stores no account ownership; [account-entitlement.md](account-entitlement.md) stores no television, personalization, or behavioral field and lists the forbidden names. |
| 10. No behavioral analytics and no cloud reporting | [diagnostics.md](diagnostics.md): no telemetry dependency at all, bounded redacted local record, explicit user-confirmed export, no upload path. |

## Settled decision ownership

Every settled decision projected into `.project-ai/PROJECT_STATE.md` has an owning canonical path. This table is the check that keeps that true.

| Settled decision group | Owning path |
|---|---|
| Product intent, privacy model, licensing promises, discovery/setup expectations | `docs/PRODUCT.md` |
| Canonical domain terms (Television, Local Pairing, Television Personalization, Interaction Preferences, Active Remote, Customer Account, Username, Trial, Lifetime Entitlement, Forget this TV, and the licensing terms added this round) | `CONTEXT.md` |
| Accepted technical baseline, invariants, platform baseline | `docs/ARCHITECTURE.md` |
| External evidence and implementation-harvest provenance | The owning architecture document, using the evidence standard below |
| Android-native stack, module shape, seams, dependency rules, background limits | [modules.md](modules.md), [lifecycle.md](lifecycle.md) |
| Samsung control depth: discovery, pairing, session, commands, protocol | [discovery.md](discovery.md), [connection.md](connection.md), [commands.md](commands.md), [protocol.md](protocol.md), [samsung-interface.md](samsung-interface.md) |
| Device-local personalization, favourites, preferences, last-used television | [data.md](data.md), [presentation.md](presentation.md) |
| Remote-first behavior, failure UX, thumb-first reach, customization rules | [ui-ux.md](ui-ux.md), [presentation.md](presentation.md) |
| Screen/state contracts, routes, restoration, responsive behavior, accessibility contracts | [presentation.md](presentation.md) |
| Account purpose, sign-in paths, Username, trial, purchase, restore, refund, provisional entitlement, paid offline, sign-out, deletion | [account-entitlement.md](account-entitlement.md) |
| Remote-entry licensing gate and first-session exemption | [account-entitlement.md](account-entitlement.md), [lifecycle.md](lifecycle.md), [presentation.md](presentation.md) |
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

## Evidence and implementation harvest

Evidence is stored with the architecture decision it supports. There is no cross-cutting harvest register.

Use these labels consistently:

- **AUTHORITATIVE** — first-party platform/provider/specification evidence.
- **IMPLEMENTATION EVIDENCE** — OSS or physical behavior that demonstrates an implementation or edge case but does not become a provider guarantee.
- **APPT DECISION** — the accepted AppT contract after reconciling the evidence with product/security/privacy constraints.

For every material external/protocol claim whose implementation depends on research, the owning document records:

| Field | Required content |
|---|---|
| Decision / behavior | The exact AppT contract |
| Authority | First-party owner, when one exists |
| Authoritative link | Direct URL to the relevant page/spec |
| Authority limitation | What that source does not establish |
| OSS implementation evidence | Repository links for implementations actually inspected |
| Pinned provenance | Commit/tag plus relevant file/class/function paths |
| License | License of the material inspected |
| Harvest method | `ADAPT`, `PORT`, `CLEAN-ROOM REIMPLEMENT`, `BEHAVIORAL REFERENCE`, or `TEST-VECTOR/DATA` |
| Harvested material | The exact algorithm, state behavior, message shape, test vector, race rule, or operational pattern used |
| Rejected material | Upstream behavior AppT deliberately does not carry across |
| AppT owner | Module/document/interface that owns the resulting behavior |
| Verification | Fixture/test/physical/provider evidence that proves AppT's result |
| Validated | Date/revision of the research pass |

The current complete-codebase comparison set is intentionally distributed to its relevant owners rather than copied here: Smart-TV-Remote-Control informs Samsung discovery/connection/protocol; KDE Connect Android informs local-network and pairing-race hardening; Home Assistant Android informs Android lifecycle/discovery/E2E test practice; IR Blaster Remote informs local-only post-crash diagnostics UX. Protocol-only libraries remain in [protocol.md](protocol.md) where they are needed.

A vendor document and an OSS repository are not interchangeable. When Samsung does not document a de-facto remote-control wire detail, [protocol.md](protocol.md) says so explicitly and relies on pinned implementation evidence plus AppT fixtures/physical evidence rather than presenting the behavior as a Samsung guarantee.


## Settled privacy, account, and licensing semantics

The owning product decisions are in `docs/PRODUCT.md`, with canonical language in `CONTEXT.md`. [account-entitlement.md](account-entitlement.md) records the technical architecture.

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
| Exact `ProductPurchaseV2` representation of promo or rewarded one-time-product acquisitions, **if** either acquisition mode is deliberately enabled for AppT | [account-entitlement.md](account-entitlement.md), [security.md](security.md), [release.md](release.md) |
| First production Play app-signing key upgrade continuity drill: the new Play signing certificate is registered with App Check before rollout and the Play-delivered build continues to satisfy App Check and Play Integrity after the upgrade | [release.md](release.md), [security.md](security.md) |

## Open items that are not architecture

Pre-existing and not blockers for this map:

- human confirmation of the final source license before public distribution;
- the focused Samsung vendor-terms and legal review before public release;
- physical-device evidence that tunes the reliability targets in [reliability.md](reliability.md).

This map was accepted with the architecture closure on 2026-09-23 and remains the durable implementation map. It deliberately does not project which slice is next; the current accepted milestone and next authorized action live only in `.project-ai/PROJECT_STATE.md`.
