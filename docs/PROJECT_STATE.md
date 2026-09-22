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

Objective: `Complete human review and acceptance of the end-to-end V1 architecture map before authorizing any transition to implementation.`

Success condition: `The architecture map in docs/architecture/ is reviewed against docs/PRODUCT.md, docs/ARCHITECTURE.md, docs/HARVEST.md, and the repository architecture rules, any remaining architecture concerns are resolved, and the human explicitly approves leaving the architecture phase.`

## Active work

Primary: `Final architecture review of the merged V1 architecture map and its implementation-slice plan. No implementation slice is active or dispatched.`

Secondary:

- Confirm that the detailed map closes the intended architecture gaps without reopening settled product decisions.
- Decide whether any additional architecture/security review is needed before phase exit.
- Focused Samsung vendor-terms/legal review remains a pre-release gate, not an architecture blocker.

## Current decisions

- `Product discovery is complete; docs/PRODUCT.md is the approved product definition.`
- `The accepted V1 technical baseline is recorded in docs/ARCHITECTURE.md; detailed elaboration is recorded in docs/architecture/ and remains under final architecture review.`
- `Android-native is first; Kotlin + Compose + ViewModel/Coroutines/StateFlow; iOS is deferred and not an Android architecture constraint.`
- `Start with lean app + samsung modules; Samsung is a deep module and a universal TV seam waits for ecosystem #2.`
- `Room is local truth; Firebase Authentication + Cloud Firestore provide account/non-secret sync; pairing secrets never sync.`
- `The first successful command does not interrupt its active remote session; once that session ends, the next remote entry requires sign-in.`
- `A confirmed account switch replaces account-scoped non-secret local state rather than merging it and preserves device-local TV pairing/security material.`
- `Synchronized TV deletion removes shared non-secret metadata but never remotely unpairs another phone; only explicit local forget removes that phone's pairing.`
- `The harvest-matrix research remains reconciled in docs/HARVEST.md using ADOPT / HARVEST / REJECT.`
- `Implementation, when authorized, will be dispatched one vertical slice at a time from docs/architecture/slices.md.`
- `Phase transition from architecture to implementation requires explicit human approval; completing or merging architecture documents does not itself authorize that transition.`

## Blockers / Unknowns

- Human acceptance of the completed architecture map is still required before implementation begins.
- Exact Samsung behavior remains evidence-driven; any newly discovered material product/architecture decision must be surfaced rather than guessed.
- Final AppT source-license decision remains required before public distribution.
- Focused Samsung vendor-terms/legal review must be completed before public release.

## Recent change

- `Restored the project to the architecture phase after an implementation transition and S01 dispatch were started without explicit human approval; the architecture documents remain available for final review.`

## Relevant canonical references

- `docs/PRODUCT.md — approved AppT product definition and product constraints.`
- `docs/ARCHITECTURE.md — accepted V1 architecture baseline and invariants.`
- `docs/architecture/README.md — detailed V1 architecture map under final architecture review.`
- `docs/architecture/slices.md — proposed vertical implementation route S01–S15; not yet authorized for dispatch.`
- `docs/HARVEST.md — harvested research disposition: ADOPT / HARVEST / REJECT.`
- `.agents/CAPABILITIES.md — routing for architecture, implementation, and review work.`
- `.agents/ARENA-DISPATCH.md — Arena work-order compilation contract.`
- `AGENTS.md — repository operating entry point.`

## Next

`Review the completed architecture map and decide explicitly whether the architecture phase is complete or whether additional architecture work is required.`

## After that

1. `If architecture concerns remain, resolve them while staying in the architecture phase.`
2. `Only after explicit human approval, update PROJECT_STATE.md to implementation.`
3. `Then compile and dispatch S01 as the first implementation slice.`
