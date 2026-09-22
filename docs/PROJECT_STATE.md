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

Purpose: `A universal TV remote for ordinary consumers, beginning with Samsung Smart TVs and expanding across television ecosystems behind one consistent phone-native experience.`

Phase: `architecture`

## Current objective

Objective: `Complete the V1 architecture map from the accepted Android/Samsung-first baseline so implementation can later be dispatched in vertical, verifiable slices.`

Success condition: `Canonical architecture documents define the application/Samsung seams, state machines, data and sync ownership, security rules, testing/release structure, and an end-to-end implementation slice map without reopening settled product decisions or inventing material new architecture intent.`

## Active work

Primary: `End-to-end V1 architecture mapping and implementation-slice design from the accepted baseline.`

Secondary:

- Samsung discovery, pairing, identity, capability, protocol, and reconnect details.
- Account/sync schema and security rules while preserving local-first control and device-local pairing secrets.
- Focused Samsung vendor-terms/legal review remains a pre-release gate, not an architecture blocker.

## Current decisions

- `Product discovery is complete; docs/PRODUCT.md is the approved product definition.`
- `The accepted V1 technical baseline is recorded in docs/ARCHITECTURE.md.`
- `Android-native is first; Kotlin + Compose + ViewModel/Coroutines/StateFlow; iOS is deferred and not an Android architecture constraint.`
- `Start with lean app + samsung modules; the Samsung implementation is deep and a universal TV seam waits for ecosystem #2.`
- `Room is local truth; Firebase Authentication + Cloud Firestore provide account/non-secret sync; pairing secrets never sync.`
- `The harvest-matrix research has been reconciled explicitly in docs/HARVEST.md using ADOPT / HARVEST / REJECT.`
- `Implementation must later be dispatched in vertical slices after the architecture map is accepted.`

## Blockers / Unknowns

- Concrete `app` ↔ `samsung` interface and Samsung discovery/pairing/connection/capability state models.
- Exact Samsung protocol/device-generation behavior and persistent identity behavior that V1 will support.
- Concrete Room/DataStore/Keystore ownership model and Firestore schema, sync/version/deletion strategy, and Security Rules.
- Final AppT source-license decision is not required to complete the V1 runtime architecture but must be resolved before public distribution if it affects dependency/provenance choices.
- Focused Samsung vendor-terms/legal review must be completed before public release.

## Recent change

- `The 24 architecture choices and the harvest/adopt/reject register were promoted into canonical architecture inputs; the next architecture task is detailed end-to-end mapping rather than further stack selection.`

## Relevant canonical references

- `docs/PRODUCT.md — approved AppT product definition and product constraints.`
- `docs/ARCHITECTURE.md — accepted V1 architecture baseline and invariants.`
- `docs/HARVEST.md — harvested research disposition: ADOPT / HARVEST / REJECT.`
- `.agents/CAPABILITIES.md — routing for planning, architecture, implementation, and review work.`
- `.agents/ARENA-DISPATCH.md — Arena work-order compilation contract.`
- `AGENTS.md — repository operating entry point.`

## Next

`Dispatch one Arena architecture-mapping Issue to turn the accepted baseline into an implementation-ready end-to-end architecture and vertical slice map, resolving routine technical gaps within the decided boundaries and surfacing any material missing decision as a blocker.`

## After that

1. `Review the Arena architecture PR against docs/PRODUCT.md, docs/ARCHITECTURE.md, docs/HARVEST.md, and codebase-design principles.`
2. `Accept the completed architecture map and move the project to implementation only when the remaining material architecture gaps are closed.`
3. `Dispatch implementation as small vertical slices with explicit seams, acceptance criteria, verification, and dependencies.`
