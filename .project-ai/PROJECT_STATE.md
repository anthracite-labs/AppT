# Project State

This file is a bounded snapshot of accepted, durable project reality.

Record only the current project position. Do not use it as a diary, backlog, CI log, architecture document, changelog, project profile, or chat history.

Update it only when accepted reality materially changes. Activity alone is not state. A pull request opening, a test failing, Arena starting work, a workflow running, or an implementation commit does not by itself justify an update.

Reconcile this file after accepted work has landed on `main`. It describes accepted reality, never anticipated reality.

## Phase

Implementation.

## Current Objective

Dispatch S04 — Saved pairing, fail-closed identity, and the safe forget primitive — from Arena Issue #91 contract revision 3 as the next authorized implementation slice.

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
- Reuse-before-reimplement control-plane policy is accepted through PR #108. New Arena-ready Issues and future material contract revisions must carry an executable Implementation Reuse Plan that prefers existing AppT code and compatible `ADAPT`/`PORT` reuse before `NEW` implementation, while preserving recorded clean-room/behavioral-reference licence boundaries. Active S04 Issue #91 Revision 3 is grandfathered until any future material revision.
- S04 is the next authorized implementation slice.
- Issue #91 contract revision 3 is the Arena-ready S04 contract. Revision 3 preserves the reconciled S04 behavior/boundaries while requiring evidence-first implementation: repository truth first, material first-party authoritative sources re-opened for external contracts, relevant pinned implementation-harvest evidence inspected before coding, and temporary workflows used only as last-resort test infrastructure. It does not pre-create `Favourite` or `PendingForget`.
- S07 may be dependency-ready but is not active or authorized.
- Provider facts marked `needs validation` must be confirmed when they first become implementation-relevant. Material external/protocol claims also carry explicit authority/implementation-evidence/AppT-decision provenance in their owning architecture documents.
- Final AppT source-license selection and focused Samsung vendor-terms/legal review remain pre-public-release gates.
- Project commands and execution-environment facts are owned by `docs/BUILD.md`, project configuration, and `.github/workflows/verify.yml`.

## Durable Blockers

None for S04 dispatch.

## Latest Accepted Milestone

S03 — Pair on the television and the first command — accepted through PR #80.

## Next Authorized Action

Dispatch Issue #91 contract revision 3 to Arena using `.project-ai/execution/arena-dispatch.md`; review the resulting PR against that revision before human acceptance.

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
