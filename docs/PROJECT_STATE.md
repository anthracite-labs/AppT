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

Phase: `discovery`

## Current objective

Objective: `Determine the V1 application architecture and stack from AppT's approved product constraints, without preselecting a framework or runtime.`

Success condition: `An evidence-backed architecture decision compares viable approaches against AppT's Samsung-control, local-first, security, account-sync, reliability, and cross-platform requirements, then records the selected direction and its trade-offs.`

## Active work

Primary: `Targeted physical-device architecture gates identified by the independent verification pass`

Secondary:

- Gate A: Samsung transport, pairing, token behavior, TLS/device identity, and fail-closed reconnect on representative physical TVs.
- Gate B: physical iPhone/Android discovery, local-network permission, network-change, and lifecycle/reconnect behavior.
- Gate C: minimum session-placement comparison after Gates A and B establish a reproducible Samsung/platform baseline.
- Focused Samsung vendor-terms/legal review remains a pre-release gate, not an architecture blocker.

## Current decisions

- `AppT is a Universal TV Remote for ordinary consumers — docs/PRODUCT.md`
- `Samsung Smart TVs are the first intentionally targeted ecosystem; other TVs may be experimentally probed with explicit user consent before functional commands — docs/PRODUCT.md`
- `Android and iPhone are V1 targets when architecture can support both without compromising reliability; Android-first is an acceptable fallback — docs/PRODUCT.md`
- `TV control is local-first and continues when AppT services or internet access are unavailable — docs/PRODUCT.md`
- `An account is part of the product, but first successful local control is not gated by sign-in; account sync excludes pairing secrets — docs/PRODUCT.md`
- `V1 is free, with no advertising, behavioral usage analytics, or required paid tier/subscription — docs/PRODUCT.md`
- `The merged V1 architecture/stack research is non-binding evidence; no framework, runtime, or session-ownership model has been selected — docs/research/V1_APPLICATION_ARCHITECTURE_STACK_RESEARCH.md`
- `Independent verification confirmed the architecture-critical platform constraints and reduced the first decision-blocking experiment set to Gates A-C; wake, text/pointer breadth, account restore, broad cohort coverage, and iPhone hardware-volume resolution are later validation/product gates — docs/research/V1_ARCHITECTURE_DECISION_GAP_ANALYSIS.md`

## Blockers / Unknowns

- Which Samsung local-control path works repeatably on representative physical TVs, including pairing/token flow and secure transport identity without a global certificate-verification bypass.
- Which discovery/reconnect strategy works on physical iPhone and Android devices under current local-network permission, entitlement, network-change, suspension, and resume behavior.
- Which session-placement boundary gives the best measured reliability and maintainability once the Samsung/platform baseline is reproducible.
- Exact Samsung protocol/device-generation behavior that AppT will ultimately support in V1 beyond the initial architecture-validation cohort.
- Final account/backend architecture and synchronization model.
- The iPhone hardware-volume requirement remains a separate product/App Review gate and should not determine stack selection.
- Focused Samsung vendor-terms/legal review must be completed before public release.

## Recent change

- `Completed an independent verification pass against current Apple, Android, Samsung, and framework primary documentation; added docs/research/V1_ARCHITECTURE_DECISION_GAP_ANALYSIS.md and reduced the immediate architecture evidence plan to Samsung/security, physical discovery/lifecycle, and session-placement gates.`

## Relevant canonical references

- `docs/PRODUCT.md — approved AppT product definition and product constraints.`
- `docs/research/V1_APPLICATION_ARCHITECTURE_STACK_RESEARCH.md — merged non-binding architecture/stack research baseline.`
- `docs/research/V1_ARCHITECTURE_DECISION_GAP_ANALYSIS.md — independently verified synthesis and minimum architecture evidence gates.`
- `.agents/CAPABILITIES.md — routing for planning, architecture, implementation, and review work.`
- `AGENTS.md — repository operating entry point.`

## Next

`Prepare and run the Gate A + Gate B physical-device baseline: characterize Samsung transport/pairing/TLS identity and real iPhone/Android discovery, permission, network-change, suspension, and reconnect behavior without introducing a production stack decision.`

## After that

1. `Use the measured baseline to choose and run the minimum Gate C session-placement comparison rather than a broad framework shootout.`
2. `Record the architecture/stack decision and move the project into architecture when the evidence is sufficient.`
3. `Design the Samsung integration boundary and account/local-data seams from the accepted product and architecture decisions.`
