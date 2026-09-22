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

Phase: `implementation`

## Current objective

Objective: `Implement V1 from the accepted Android/Samsung-first architecture in small vertical, verifiable slices, beginning with S01.`

Success condition: `Each implementation slice produces its stated observable outcome, respects docs/PRODUCT.md, docs/ARCHITECTURE.md, docs/architecture/, and docs/HARVEST.md, passes its verification, and leaves main releasable before the next slice is dispatched.`

## Active work

Primary: `S01 — Walking skeleton, dispatched from docs/architecture/slices.md.`

Secondary:

- Focused Samsung vendor-terms/legal review remains a pre-release gate, not an implementation blocker.
- Final source-license confirmation remains a pre-distribution gate.
- A focused security audit of the TLS identity path, Keystore format, and Firestore rules is recommended before public production.

## Current decisions

- `Product discovery is complete; docs/PRODUCT.md is the approved product definition.`
- `The V1 architecture map is accepted: docs/ARCHITECTURE.md owns the baseline and docs/architecture/ owns implementation-ready elaboration.`
- `Android-native is first; Kotlin + Compose + ViewModel/Coroutines/StateFlow; iOS is deferred and not an Android architecture constraint.`
- `Start with lean app + samsung modules; Samsung is a deep module and a universal TV seam waits for ecosystem #2.`
- `Room is local truth; Firebase Authentication + Cloud Firestore provide account/non-secret sync; pairing secrets never sync.`
- `The first successful command does not interrupt its active remote session; once that session ends, the next remote entry requires sign-in.`
- `A confirmed account switch replaces account-scoped non-secret local state rather than merging it and preserves device-local TV pairing/security material.`
- `Synchronized TV deletion removes shared non-secret metadata but never remotely unpairs another phone; only explicit local forget removes that phone's pairing.`
- `The harvest-matrix research remains reconciled in docs/HARVEST.md using ADOPT / HARVEST / REJECT.`
- `Implementation is dispatched one vertical slice at a time from docs/architecture/slices.md.`

## Blockers / Unknowns

- No architecture blocker prevents S01.
- Exact Samsung behavior remains evidence-driven during later Samsung slices; unexpected material product/architecture decisions must be surfaced rather than guessed.
- Final AppT source-license decision must be confirmed before public distribution.
- Focused Samsung vendor-terms/legal review must be completed before public release.

## Recent change

- `Accepted the end-to-end V1 architecture map and resolved the remaining account-gate, account-switch, and cross-device pairing semantics, making the vertical slice plan implementation-ready.`

## Relevant canonical references

- `docs/PRODUCT.md — approved AppT product definition and product constraints.`
- `docs/ARCHITECTURE.md — accepted V1 architecture baseline and invariants.`
- `docs/architecture/README.md — implementation-ready architecture map.`
- `docs/architecture/slices.md — ordered vertical implementation route S01–S15.`
- `docs/HARVEST.md — harvested research disposition: ADOPT / HARVEST / REJECT.`
- `.agents/CAPABILITIES.md — routing for planning, implementation, and review work.`
- `.agents/ARENA-DISPATCH.md — Arena work-order compilation contract.`
- `AGENTS.md — repository operating entry point.`

## Next

`Compile and dispatch S01 — Walking skeleton as one self-contained Arena Issue.`

## After that

1. `Review and accept the S01 pull request before dispatching S02.`
2. `Dispatch S02 — Local-network explanation and bounded discovery.`
3. `Continue one accepted vertical slice at a time; do not batch implementation slices.`
