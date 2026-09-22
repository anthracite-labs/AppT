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

Objective: `Review and accept the V1 architecture map so implementation can be dispatched as vertical slices.`

Success condition: `The map in docs/architecture/ is accepted against docs/PRODUCT.md, the invariants in docs/ARCHITECTURE.md, and docs/HARVEST.md, with different-account sign-in and cross-device unpair either explicitly deferred under the written holding behavior or resolved before any slice implements those behaviors.`

## Active work

Primary: `Review of the architecture map and two open product decisions: different-account sign-in, and whether deleting a television on one signed-in phone also unpairs other phones.`

Secondary:

- Focused Samsung vendor-terms/legal review remains a pre-release gate, not an architecture blocker.
- Final source-license confirmation remains a pre-distribution item. The tree contains an MIT LICENSE file; the harvest register still treats the final license as undecided. The map does not choose it.

## Current decisions

- `Product discovery is complete; docs/PRODUCT.md is the approved product definition.`
- `The accepted V1 technical baseline is recorded in docs/ARCHITECTURE.md. Elaboration is in docs/architecture/ and does not override the baseline.`
- `Android-native is first; Kotlin + Compose + ViewModel/Coroutines/StateFlow; iOS is deferred and not an Android architecture constraint.`
- `Start with lean app + samsung modules; the Samsung implementation is deep and a universal TV seam waits for ecosystem #2.`
- `The caller-facing seam is SamsungTvs. V1 control is the Tizen WebSocket channel on ports 8001 and 8002. Other handshakes are identified and not commanded.`
- `Room is local truth; Firebase Authentication + Cloud Firestore provide account/non-secret sync; pairing secrets never sync.`
- `The harvest-matrix research remains reconciled in docs/HARVEST.md using ADOPT / HARVEST / REJECT.`
- `Implementation is dispatched from docs/architecture/slices.md only after this map is accepted, one slice at a time, starting at S01.`

## Blockers / Unknowns

- Different-account sign-in on a phone that already has local data. Decision needed: what happens to local names, favourites, preferences, and pairing material when the Firebase uid differs from `lastSyncedUid`. Holding behavior is specified in `docs/architecture/sync.md`. Do not assume merge, replace, or wipe.
- Cross-device unpair. Decision needed: when a signed-in user deletes a television on one phone, do other phones also unpair, or does pairing stay strictly local while only non-secret synced state is removed? Holding behavior in `docs/architecture/sync.md` syncs the non-secret deletion and does not delete device-local pairing secrets. Do not choose either answer.
- Final AppT source-license decision remains required before public distribution, not before architecture acceptance.
- Focused Samsung vendor-terms/legal review must be completed before public release.

## Recent change

- `Revised the architecture map so each sync record is compared before write, preference metadata is stored with the mutation, a remote television tombstone does not unpair this phone, and the LAN permission and account-gate wording match the accepted decisions. Different-account sign-in and cross-device unpair remain unresolved.`

## Relevant canonical references

- `docs/PRODUCT.md — approved AppT product definition and product constraints.`
- `docs/ARCHITECTURE.md — accepted V1 architecture baseline and invariants.`
- `docs/architecture/README.md — implementation-ready map: seams, state, data, sync, tests, release, slices.`
- `docs/HARVEST.md — harvested research disposition: ADOPT / HARVEST / REJECT.`
- `.agents/CAPABILITIES.md — routing for planning, architecture, implementation, and review work.`
- `.agents/ARENA-DISPATCH.md — Arena work-order compilation contract.`
- `AGENTS.md — repository operating entry point.`

## Next

`Review the revised architecture map against docs/PRODUCT.md, docs/ARCHITECTURE.md, docs/HARVEST.md, and the six review findings, and accept it or send back a concrete defect.`

## After that

1. `Resolve different-account sign-in and cross-device unpair if acceptance should include chosen behavior; otherwise accept the map with both holding behaviors explicit.`
2. `Move the project to implementation only when the map is accepted.`
3. `Dispatch S01 from docs/architecture/slices.md, then one later slice at a time.`
