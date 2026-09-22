# Project State

<!--
Living snapshot only: replace stale state; do not append history.
Update only when the snapshot materially changes.
Keep decisions and references only while they affect current work.
Recent change: one entry. Next: one action. After that: at most three.
Verification is optional and appears only when materially relevant.
Phase values: setup | discovery | architecture | implementation | hardening | maintenance.
-->

Project: `AppT`

Purpose: `A universal TV remote for ordinary consumers who want private, dependable local television control, beginning with Samsung Smart TVs.`

Phase: `architecture`

## Current objective

Objective: `Complete the second architecture round: reconcile the privacy-first licensing model, product/domain architecture, presentation/navigation, security, backend deployment, lifecycle/network behavior, reliability budgets, and persisted-format migration before any implementation transition.`

Success condition: `Every material product/architecture decision is explicit, the detailed docs are internally consistent with docs/PRODUCT.md, docs/ARCHITECTURE.md, CONTEXT.md, and docs/HARVEST.md, the superseded TV-sync model is fully removed, and the human explicitly approves leaving architecture.`

## Active work

Primary: `Human decision tree for the remaining architecture frontier, followed by technical architecture closure. No implementation slice is active or dispatched.`

Secondary:

- Reconcile all detailed docs that still contain the superseded Firestore TV-personalization sync model.
- Add the missing presentation/navigation, threat-model, backend-environment, Android lifecycle/network, reliability/performance, and persisted-format migration architecture.
- Focused Samsung vendor-terms/legal review remains a pre-release gate, not an architecture blocker.

## Current decisions

- `Product discovery is complete; docs/PRODUCT.md owns product intent.`
- `CONTEXT.md now owns canonical domain language, including Television, Local Pairing, Customer Account, Trial, Lifetime Entitlement, and Active Remote.`
- `Android-native is first; Kotlin + Compose + ViewModel/Coroutines/StateFlow; iOS is deferred and not an Android architecture constraint.`
- `Start with lean app + samsung modules; Samsung is a deep module and a universal TV seam waits for ecosystem #2.`
- `The physical Television is the core domain concept; discovery, local pairing/trust, personalization, account identity, and active control are separate relationships/state around it.`
- `AppT is remote-first after setup: normally reopen the last-used TV; connection failure stays on an honest remote surface with retry/switch-TV recovery rather than forcing a dashboard.`
- `Production V1 has no consumer experimental functional-control mode for non-adopted protocols; internal/debug research may exercise bounded experimental implementations.`
- `Customer Account purpose is minimal: authentication, username, seven-day trial/anti-abuse state, and lifetime license entitlement only.`
- `TV identity, pairing, TV names, favourites, remote arrangement, preferences, last-used TV, diagnostics, and usage history are device-local and are not stored/synchronized in the account backend.`
- `Google sign-in through Credential Manager is primary; email/password is fallback. Google display name may seed an editable username, but unrelated profile data is not copied into AppT.`
- `First successful local-control session remains available before account creation; after that active session ends, account access is required.`
- `Eligible accounts receive a seven-day full-use trial from a server-authoritative activation timestamp.`
- `Trial abuse protection uses privacy-minimized pseudonymous eligibility signals derived from verified email/device context plus Play Integrity, not TV or behavioral data.`
- `Android lifetime unlock is a Google Play one-time non-consumable product; authoritative purchase validation grants an account-level lifetime entitlement.`
- `A validated lifetime entitlement keeps paid local control available offline indefinitely without periodic backend revalidation.`
- `Trial expiry does not interrupt an active remote; after known expiry, the next remote entry requires online entitlement validation or purchase.`
- `Signing out, switching accounts, or deleting an account does not erase/merge/upload/replace local TV pairing or personalization.`
- `Forget this TV is the consumer action that removes this phone's local pairing and TV personalization only.`
- `Favourites/secondary-control order are per-TV and local; haptics, physical volume-button behavior, and preferred navigation mode are device-wide and local.`
- `The previous Firestore TV-personalization sync architecture is superseded and must not reappear in implementation.`
- `Phase transition to implementation requires explicit human approval.`

## Blockers / Unknowns

- Email/password verification semantics for seven-day trial eligibility.
- Exact entitlement backend/service and datastore shape.
- Google Play purchase verification, restore, refund/revocation, and fraud semantics.
- Offline paid-entitlement cache/token representation and tamper model.
- Anti-abuse keyed-identifier retention/rotation and account-deletion retention.
- Dev/test/production Firebase, Play Billing, Play Integrity, and backend environment separation.
- Complete presentation/navigation state architecture for remote-first launch, trial/account/purchase/restore, failures, process death, and recovery.
- Formal threat model/trust boundaries for LAN control plus account/licensing infrastructure.
- Android network/lifecycle architecture across Wi-Fi/Ethernet changes, VPNs, screen/background/process transitions, and Doze.
- Internal reliability/performance budgets and benchmark gates.
- Secret/private-record migration, key rotation, corruption, and app-upgrade strategy.
- Detailed data/modules/flows/testing/release/slices docs still need reconciliation after the privacy pivot.
- Final AppT source-license decision remains required before public distribution.
- Focused Samsung vendor-terms/legal review must be completed before public release.

## Recent change

- `Replaced the cloud TV-personalization model with a privacy-first Customer Account: username + seven-day trial + one-time lifetime entitlement only. Added canonical domain language and marked the detailed architecture map under revision.`

## Relevant canonical references

- `docs/PRODUCT.md — approved product definition, including privacy-first account/trial/license behavior.`
- `CONTEXT.md — canonical AppT domain language.`
- `docs/ARCHITECTURE.md — accepted technical baseline as revised by settled architecture decisions.`
- `docs/architecture/README.md — detailed architecture map and reconciliation status.`
- `docs/architecture/sync.md — current account/trial/entitlement architecture and remaining technical questions.`
- `docs/HARVEST.md — harvested research disposition: ADOPT / HARVEST / REJECT.`
- `.agents/CAPABILITIES.md — architecture/decision/review routing.`
- `.agents/ARENA-DISPATCH.md — Arena work-order compilation contract.`
- `AGENTS.md — repository operating entry point.`

## Next

`Continue the human architecture decision tree for licensing/privacy/presentation semantics; do not dispatch implementation.`

## After that

1. `Record the remaining human decisions in their owning canonical files.`
2. `Dispatch one architecture-only Arena work order to reconcile and complete the technical architecture map, if no human decisions remain unresolved.`
3. `Review the completed architecture map; only explicit human approval can move the project to implementation.`
