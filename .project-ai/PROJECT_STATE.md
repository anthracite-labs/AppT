# Project State

This file is a bounded snapshot of accepted, durable project reality.

Record only the current project position. Do not use it as a diary, backlog, CI log, architecture document, changelog, project profile, or chat history.

Update it only when accepted reality materially changes. Activity alone is not state. A pull request opening, a test failing, Arena starting work, a workflow running, or an implementation commit does not by itself justify an update.

Reconcile this file after accepted work has landed on `main`. It describes accepted reality, never anticipated reality.

## Phase

Implementation.

## Current Objective

Dispatch and execute the human-authorized contiguous S06–S17 V1 lifecycle program from Issue #145 while keeping the accepted S01–S05 baseline stable. No Arena implementation is active until Issue #145 is explicitly dispatched.

Success means one Arena branch and one pull request implement S06 through S17 in strict chronological checkpoint order, preserving every slice's dependency, reuse/provenance, acceptance, verification, provider, physical-device, security, privacy, legal, licensing, and human gates.

## Accepted Decisions

- `docs/PRODUCT.md` owns approved product intent; product discovery is complete.
- `CONTEXT.md` owns canonical AppT domain language.
- `docs/ARCHITECTURE.md` and `docs/architecture/` own accepted technical direction and detailed architecture.
- The accepted implementation route is S01–S17 in `docs/architecture/slices.md`. The default remains one authorized slice at a time; PR #146 accepted an explicit human-authorized contiguous program mode that may package a named ordered slice range into one Arena Issue, branch, and pull request without weakening any included slice's dependency or acceptance gates. S01–S03 history is preserved; Issue #96 lifecycle/evidence reconciliation corrected the future S04–S17 route before S04 implementation.
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
- S06 is the first execution checkpoint of the human-authorized contiguous S06–S17 program; downstream checkpoints are authorized inside Issue #145 but do not become accepted merely because Arena advances.
- Contiguous Arena program execution is accepted through PR #146, merged to `main` as `2b67bdea1b0ed326dba26181e8a539ec56bfdd48`. Program mode changes execution packaging only; each included slice remains a distinct dependency, acceptance, verification, reuse/provenance, provider, physical-device, and human checkpoint.
- The project owner explicitly authorized S06 through S17 as one contiguous chronological program in Issue #145. Execute checkpoints strictly S06 → S07 → S08 → S09 → S10 → S11 → S12 → S13 → S14 → S15 → S16 → S17 on one Arena branch and one pull request.
- Issue #145 Contract Revision 1 is the Arena-ready implementation contract for the complete remaining V1 lifecycle. Implementation begins only after explicit Arena dispatch; no Arena implementation is active merely because the Issue exists.
- Pending provider, physical-device, legal, licensing, or owner-admin evidence never counts as accepted evidence; each gate resolves at the milestone required by its canonical contract.
- Provider facts marked `needs validation` must be confirmed when they first become implementation-relevant. Material external/protocol claims also carry explicit authority/implementation-evidence/AppT-decision provenance in their owning architecture documents.
- Final AppT source-license selection and focused Samsung vendor-terms/legal review remain pre-public-release gates.
- Project commands and execution-environment facts are owned by `docs/BUILD.md`, project configuration, and `.github/workflows/verify.yml`.

## Durable Blockers

None for Issue #145 Arena dispatch. Later provider, physical-device, source-license, and Samsung vendor-terms gates remain milestone-bound requirements, not present dispatch blockers unless their owning checkpoint requires them to proceed safely.

## Latest Accepted Milestone

S05 — Capability-driven Remote surface, Settings shell, and diagnostic recorder — accepted through PR #118, merged to `main` as `bd0474acccb3e30ff2592dee73b01205fc847e4d`.

## Next Authorized Action

Explicitly dispatch Issue #145 to Arena as the contiguous S06–S17 program. Arena must treat the Issue as the complete implementation contract, execute the ordered checkpoints on one branch/PR, apply reuse-before-reimplement and mandatory lifecycle skills, diagnose before every correction, and return the resulting program PR without merging it.

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
