# Project State

This file is a bounded snapshot of accepted, durable project reality.

Record only the current project position. Do not use it as a diary, backlog, CI log, architecture document, changelog, project profile, or chat history.

Update it only when accepted reality materially changes. Activity alone is not state. A pull request opening, a test failing, Arena starting work, a workflow running, or an implementation commit does not by itself justify an update.

Reconcile this file after accepted work has landed on `main`. It describes accepted reality, never anticipated reality.

## Phase

Implementation.

## Current Objective

Prepare S06 — Reconnect, lifecycle, and Activity recreation — as the next authorized implementation slice while keeping the accepted S05 baseline stable. No S06 Arena implementation is active yet.

Success means the control plane compiles a bounded S06 contract from accepted architecture and reuse evidence without regressing S05 product, verification, dependency-state, or diagnostic-selection behavior.

## Accepted Decisions

- `docs/PRODUCT.md` owns approved product intent; product discovery is complete.
- `CONTEXT.md` owns canonical AppT domain language.
- `docs/ARCHITECTURE.md` and `docs/architecture/` own accepted technical direction and detailed architecture.
- The accepted implementation route is S01–S17 in `docs/architecture/slices.md`, executed one authorized slice at a time. S01–S03 history is preserved; Issue #96 lifecycle/evidence reconciliation corrected the future S04–S17 route before S04 implementation.
- S01 is accepted through PR #30.
- The pre-S02 security baseline is accepted through PR #38.
- Verification and independent AI-code assurance are accepted through PR #60.
- S02 is accepted through implementation PR #75 and corrective closeout PR #77.
- S03 is accepted through PR #80.
- S04 is accepted through PR #105.
- Repository CI/agent-control and verification hardening are accepted through PR #89.
- Mandatory lifecycle skill routing and diagnose-before-fix gates are accepted through PR #84.
- Bounded diagnostic breadth before correction is accepted through PR #106: inside the smallest affected domain, collect the practical independent failure set, group root causes, then correct coherently without weakening failure semantics.
- Reuse-before-reimplement is accepted through PR #108 for new Arena-ready Issues and future material revisions: the control plane compiles an Implementation Reuse Plan from accepted AppT code plus distributed harvest evidence; Arena prefers approved `EXISTING APPT`, `ADAPT`, or `PORT` leverage before `NEW`, and `NEW` requires a concrete recorded reason.
- S05 is accepted through PR #118 under Issue #117 Contract Revision 5. The accepted slice includes the capability-driven Remote surface, Settings shell, bounded diagnostic recorder, and the retirement of Baseline Profile, `:macrobenchmark`, hosted Gradle Managed Device, profile-handoff/regeneration, and `ci:device` paths.
- The S05 closeout owner decision accepted the already-recorded bounded physical Samsung evidence for final acceptance without rerunning the five-sample verifier on the final SHA. The final candidate `f2b007ab0f5c370375a657c96e36cfbbaeb32cb2` passed terminal repository verification and trusted `android-build` before PR #118 merged.
- Selectable multi-mode diagnostics are accepted through PR #118: `diagnose.yml` accepts one existing mode or a validated comma-separated set of existing modes, runs selected named jobs concurrently, preserves single-mode focus semantics, and remains non-terminal.
- Repository simplification is accepted through PR #136. Standard Gradle dependency locking and strict dependency verification remain active; the custom `dependencyLockCheck` wrapper, hosted `ci:dependency-state` regeneration route, warm-cache publisher/sanitizer, manual Actions purge workflow, and redundant backend diagnostic modes are retired. `docs/BUILD.md` owns the current dependency-refresh and verification routes.
- S06 is the next authorized implementation slice.
- S06 is not yet active; implementation begins only from a bounded Arena-ready contract and explicit dispatch.
- S07 may be dependency-ready in parallel with the control spine but is not active or authorized.
- Provider facts marked `needs validation` must be confirmed when they first become implementation-relevant. Material external/protocol claims also carry explicit authority/implementation-evidence/AppT-decision provenance in their owning architecture documents.
- Final AppT source-license selection and focused Samsung vendor-terms/legal review remain pre-public-release gates.
- Project commands and execution-environment facts are owned by `docs/BUILD.md`, project configuration, and `.github/workflows/verify.yml`.

## Durable Blockers

None for S06 dispatch.

## Latest Accepted Milestone

S05 — Capability-driven Remote surface, Settings shell, and diagnostic recorder — accepted through PR #118, merged to `main` as `bd0474acccb3e30ff2592dee73b01205fc847e4d`.

## Next Authorized Action

Prepare the bounded Arena-ready contract for S06 — Reconnect, lifecycle, and Activity recreation — from the accepted route and architecture sources, preserving the accepted S05 Remote, Settings, diagnostics, verification, and dependency-state baseline. Apply the repository's reuse-before-reimplement and diagnose-before-fix rules. Do not begin Arena implementation until that S06 contract is explicitly dispatched.

## Authoritative References

- `docs/PRODUCT.md`
- `CONTEXT.md`
- `docs/ARCHITECTURE.md`
- `docs/architecture/README.md`
- `docs/architecture/slices.md`
- `docs/architecture/security.md`
- `docs/architecture/testing.md`
- `docs/architecture/release.md`
- `docs/BUILD.md`
- `docs/architecture/account-entitlement.md`
- `.github/workflows/verify.yml`
- `.project-ai/bootstrap/project.md`
- `.project-ai/routing/capabilities.md`
- `.project-ai/routing/route.md`
- `.project-ai/execution/arena-dispatch.md`
- `.project-ai/execution/verification.md`
