# Project State

This file is a bounded snapshot of accepted, durable project reality.

Record only the current project position. Do not use it as a diary, backlog, CI log, architecture document, changelog, project profile, or chat history.

Update it only when accepted reality materially changes. Activity alone is not state. A pull request opening, a test failing, Arena starting work, a workflow running, or an implementation commit does not by itself justify an update.

Reconcile this file after accepted work has landed on `main`. It describes accepted reality, never anticipated reality.

## Phase

Implementation.

## Current Objective

Complete S05 — Capability-driven Remote surface, Settings shell, and diagnostic recorder — under the accepted Issue #117 contract on the existing implementation PR #118.

Success means S05 makes the accepted phone-native Remote, Settings, accessibility, performance, and bounded local-diagnostics foundation real while preserving S04's accepted saved-pairing, fail-closed identity, and safe-forget boundaries, then closes on normal repository verification plus the slice-owned physical verifier evidence.

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
- S05 is currently active under Issue #117. Contract Revision 5 is the current accepted contract revision. It retires Baseline Profile, `:macrobenchmark`, hosted Gradle Managed Device, profile-handoff/regeneration, and `ci:device` requirements in favor of ordinary AppT verification plus bounded debug slice diagnostics and physical evidence where the behavior genuinely requires hardware.
- Trusted exact-head Gradle dependency-state regeneration is accepted through PR #129 as the permanent `ci:dependency-state` diagnostic route. It may regenerate only the known Gradle lockfiles under strict verification and never writes the target branch.
- S07 may be dependency-ready but is not active or authorized.
- Provider facts marked `needs validation` must be confirmed when they first become implementation-relevant. Material external/protocol claims also carry explicit authority/implementation-evidence/AppT-decision provenance in their owning architecture documents.
- Final AppT source-license selection and focused Samsung vendor-terms/legal review remain pre-public-release gates.
- Project commands and execution-environment facts are owned by `docs/BUILD.md`, project configuration, and `.github/workflows/verify.yml`.

## Durable Blockers

None at the project/contract level. S05 still requires the benchmark/device plumbing purge, normal exact-head repository verification, an exact-head debug APK, and final physical verifier evidence.

## Latest Accepted Milestone

S04 — Saved pairing, fail-closed identity, and the safe forget primitive — accepted through PR #105.

## Next Authorized Action

Continue Issue #117 / PR #118 on the existing Arena branch under Contract Revision 5. Remove the retired `:macrobenchmark` module, Baseline Profile tooling/configuration, benchmark-only synthetic app variants/fixtures, managed-device build definitions, and resulting dependency state from the implementation candidate; do not repair the failed benchmark harness. Preserve the stock-debug S05 physical verifier and release-exclusion guard. Regenerate only genuinely changed dependency state through the trusted route, regain focused AppT/Samsung checks, then run exact-head terminal verification and `ci:android-build`. Finish with the bounded five-sample physical verifier on the final exact candidate and human acceptance. Keep PR #118 Draft / NOT VERIFIED until those revised gates are satisfied.

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
