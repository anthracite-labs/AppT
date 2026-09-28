# Project State

This file is a bounded snapshot of accepted, durable project reality.

Record only the current project position. Do not use it as a diary, backlog, CI log, architecture document, changelog, project profile, or chat history.

Update it only when accepted reality materially changes. Activity alone is not state. A pull request opening, a test failing, Arena starting work, a workflow running, or an implementation commit does not by itself justify an update.

Reconcile this file after accepted work has landed on `main`. It describes accepted reality, never anticipated reality.

## Phase

Implementation.

## Current Objective

Recompile S04 — Saved pairing, fail-closed identity, and the safe forget primitive — against the lifecycle/evidence-reconciled architecture, then dispatch it as the next authorized implementation slice.

Success means S04 implements only behavior that becomes real in S04, includes the staged physical Samsung checkpoint, preserves S03's accepted first-control/session boundaries, and satisfies the accepted security, privacy, provenance, supply-chain, and verification floor.

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
- Repository CI/agent-control and verification hardening are accepted through PR #89.
- Mandatory lifecycle skill routing and diagnose-before-fix gates are accepted through PR #84.
- S04 is the next authorized implementation slice. Its contract must be recompiled from the reconciled architecture before dispatch; it does not pre-create `Favourite` or `PendingForget`.
- S07 may be dependency-ready but is not active or authorized.
- Provider facts marked `needs validation` must be confirmed when they first become implementation-relevant. Material external/protocol claims also carry explicit authority/implementation-evidence/AppT-decision provenance in their owning architecture documents.
- Final AppT source-license selection and focused Samsung vendor-terms/legal review remain pre-public-release gates.
- Project commands and execution-environment facts are owned by `docs/BUILD.md`, project configuration, and `.github/workflows/verify.yml`.

## Durable Blockers

None for S04 dispatch.

## Latest Accepted Milestone

S03 — Pair on the television and the first command — accepted through PR #80.

## Next Authorized Action

Revise Issue #91 from the reconciled S04 contract in `docs/architecture/slices.md`, then use `.project-ai/skills/implementation-planning/SKILL.md` and `.project-ai/execution/arena-dispatch.md` to dispatch S04.

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
