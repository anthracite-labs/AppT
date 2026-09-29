# Project State

This file is a bounded snapshot of accepted, durable project reality.

Record only the current project position. Do not use it as a diary, backlog, CI log, architecture document, changelog, project profile, or chat history.

Update it only when accepted reality materially changes. Activity alone is not state. A pull request opening, a test failing, Arena starting work, a workflow running, or an implementation commit does not by itself justify an update.

Reconcile this file after accepted work has landed on `main`. It describes accepted reality, never anticipated reality.

## Phase

Implementation.

## Current Objective

Compile and dispatch S05 — Capability-driven Remote surface, Settings shell, and diagnostic recorder — as the next authorized implementation slice.

Success means S05 makes the accepted phone-native Remote, Settings, accessibility, performance, and bounded local-diagnostics foundation real while preserving S04's accepted saved-pairing, fail-closed identity, and safe-forget boundaries.

## Accepted Decisions

- `docs/PRODUCT.md` owns approved product intent; product discovery is complete.
- `CONTEXT.md` owns canonical AppT domain language.
- `docs/ARCHITECTURE.md` and `docs/architecture/` own accepted technical direction and detailed architecture.
- The accepted implementation route is S01–S17 in `docs/architecture/slices.md`, executed one authorized slice at a time. S01–S03 history is preserved; Issue #96 lifecycle/evidence reconciliation corrected the future S04–S17 route before S04 implementation.
- S01 is accepted through PR #30.
- The pre-S02 security baseline is accepted through PR #38.
- Verification and independent AI-code assurance are accepted through PR #60.
- S02 is accepted through implementation PR #75 and corrective closeout PR #77.
- S03 is accepted through implementation PR #80.
- S04 is accepted through PR #105.
- Repository CI/agent-control and verification hardening are accepted through PR #89.
- Mandatory lifecycle skill routing and diagnose-before-fix gates are accepted through PR #84.
- Bounded diagnostic breadth before correction is accepted through PR #106: inside the smallest affected domain, collect the practical independent failure set, group root causes, then correct coherently without weakening failure semantics.
- Reuse-before-reimplement is accepted through PR #108 for new Arena-ready Issues and future material revisions: the control plane compiles an Implementation Reuse Plan from accepted AppT code plus distributed harvest evidence; Arena prefers approved `EXISTING APPT`, `ADAPT`, or `PORT` leverage before `NEW`, and `NEW` requires a concrete recorded reason.
- S05 is the next authorized implementation slice.
- S07 may be dependency-ready but is not active or authorized.
- Provider facts marked `needs validation` must be confirmed when they first become implementation-relevant. Material external/protocol claims also carry explicit authority/implementation-evidence/AppT-decision provenance in their owning architecture documents.
- Final AppT source-license selection and focused Samsung vendor-terms/legal review remain pre-public-release gates.
- Project commands and execution-environment facts are owned by `docs/BUILD.md`, project configuration, and `.github/workflows/verify.yml`.

## Durable Blockers

None for S05 dispatch.

## Latest Accepted Milestone

S04 — Saved pairing, fail-closed identity, and the safe forget primitive — accepted through PR #105.

## Next Authorized Action

Compile S05 from `docs/architecture/slices.md` and its owning architecture sources into an Arena-ready Issue with the required Implementation Reuse Plan, then dispatch it through `.project-ai/execution/arena-dispatch.md`.

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
