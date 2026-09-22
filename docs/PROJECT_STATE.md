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

Primary: `Architecture research synthesis and evidence-gap closure`

Secondary:

- Samsung control integration constraints relevant to stack selection.
- Empirical validation needs for discovery, TLS/device identity, lifecycle/reconnection, and session-placement models.
- Account/sync boundary design, preserving device-local pairing credentials and offline local control.
- Focused Samsung vendor-terms/legal review remains a pre-release gate, not an architecture blocker.

## Current decisions

- `AppT is a Universal TV Remote for ordinary consumers — docs/PRODUCT.md`
- `Samsung Smart TVs are the first intentionally targeted ecosystem; other TVs may be experimentally probed with explicit user consent before functional commands — docs/PRODUCT.md`
- `Android and iPhone are V1 targets when architecture can support both without compromising reliability; Android-first is an acceptable fallback — docs/PRODUCT.md`
- `TV control is local-first and continues when AppT services or internet access are unavailable — docs/PRODUCT.md`
- `An account is part of the product, but first successful local control is not gated by sign-in; account sync excludes pairing secrets — docs/PRODUCT.md`
- `V1 is free, with no advertising, behavioral usage analytics, or required paid tier/subscription — docs/PRODUCT.md`
- `The merged V1 architecture/stack research is non-binding evidence; no framework, runtime, or session-ownership model has been selected — docs/research/V1_APPLICATION_ARCHITECTURE_STACK_RESEARCH.md`

## Blockers / Unknowns

- Which application architecture and stack best satisfy Samsung discovery/control, TLS/security, secure credential storage, app lifecycle, reliability, and cross-platform requirements.
- Which remaining architecture questions require physical-device prototypes rather than further documentation research.
- Exact Samsung protocol/device-generation behavior that AppT will support in V1.
- Whether Samsung secure transport exposes a persistent identity that AppT can verify without a global certificate-verification bypass.
- Final account/backend architecture and synchronization model.
- Focused Samsung vendor-terms/legal review must be completed before public release.

## Recent change

- `Merged PR #4, establishing the stack-neutral V1 architecture/stack research baseline with Samsung-first evidence, cross-platform comparison, multi-ecosystem stress testing, and explicit unresolved prototype questions.`

## Relevant canonical references

- `docs/PRODUCT.md — approved AppT product definition and product constraints.`
- `docs/research/V1_APPLICATION_ARCHITECTURE_STACK_RESEARCH.md — merged non-binding architecture/stack research baseline.`
- `.agents/CAPABILITIES.md — routing for planning, architecture, implementation, and review work.`
- `AGENTS.md — repository operating entry point.`

## Next

`Synthesize and independently verify the merged research, then identify the minimum unresolved questions that require targeted prototypes before selecting the V1 architecture and stack.`

## After that

1. `Run only the targeted physical-device/protocol experiments that materially differentiate viable architecture choices.`
2. `Record the architecture/stack decision and move the project into architecture when the evidence is sufficient.`
3. `Design the Samsung integration boundary and account/local-data seams from the accepted product and architecture decisions.`
