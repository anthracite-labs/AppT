# Arena Dispatch and Review Lifecycle

This file owns the complete lifecycle for project implementation dispatched to Arena.

The control plane owns understanding, decisions, planning, dispatch, control-plane maintenance, and contract review.

Arena owns project implementation inside an approved contract.

Human acceptance remains the final authority before merge.

## 1. Dispatch eligibility

Dispatch only when:

- the desired outcome is understood;
- material project/architecture decisions required for the work are accepted;
- scope and non-goals are bounded;
- acceptance criteria are observable;
- non-trivial production behavior has an explicit implementation reuse disposition compiled from current AppT code and owning harvest evidence;
- the work can be expressed as either one sensible reviewable implementation unit or an explicitly human-approved contiguous program contract under §2A.

Use `../skills/implementation-planning/SKILL.md` when approved work needs decomposition, sequencing, risk-first proof, or a bounded Arena contract. Plan only to the depth required to make the Issue executable. Do not pre-implement the solution in prose.

By default, if the work cannot fit one sensible branch/PR, reconsider decomposition before dispatch rather than allowing Arena to invent project-level work decomposition. The only exception is the explicit contiguous program mode in §2A, where the human intentionally authorizes one ordered multi-slice lifecycle program as one Issue, one branch, and one PR.

## 2. Canonical Arena Issue contract

Every Arena Issue uses this lean structure:

### Objective

State what must become true.

### Context & Authority

Give only the accepted decisions and canonical repository references Arena needs to execute correctly.

Do not paste chat history or duplicate all of `PROJECT_STATE.md`.

### Implementation Reuse Plan

For each non-trivial behavior, state the executable disposition compiled by the control plane:

`EXISTING APPT`, `ADAPT`, `PORT`, `CLEAN-ROOM REIMPLEMENT`, `BEHAVIORAL REFERENCE`, `TEST-VECTOR/DATA`, or `NEW`.

For upstream reuse, identify the pinned source/path, licence, exact material to reuse, rejected material, and AppT destination. For `NEW`, state why no suitable existing/reusable implementation applies.

This is not a second harvest register. The owning architecture documents remain canonical provenance; the Issue carries only the subset needed to execute this slice.

Do not retroactively invalidate an already-dispatched Issue solely because its active contract predates this section. Apply the reuse-plan requirement to new Arena-ready Issues and to the next material contract revision of older in-flight work.

Do not retroactively invalidate an already-dispatched Issue solely because its active contract predates this section. Apply the reuse-plan requirement to new Arena-ready Issues and to the next material contract revision of older in-flight work.

### Scope

State what Arena may change.

### Out of Scope

State what Arena must not change.

### Constraints

State architecture, behavior, compatibility, dependency, security, data, or interface boundaries that must be preserved.

### Acceptance Criteria

State observable conditions that must be true when the work is finished.

### Verification

State required targeted checks and the expected terminal repository verification.

Use project-owned commands and artifacts rather than inventing generic stack commands.

### Contract Exceptions

State the categories of discovery that require Arena to stop and return control rather than silently changing the contract.

### 2A. Contiguous program execution mode

Contiguous program execution is an explicit exception to the default one-slice-at-a-time planning rule. Use it only when the human owner directly authorizes a named contiguous slice range as one lifecycle program.

A program Issue is Arena-ready only when all of the following are true:

- the authorized slice range is explicit and ordered by the accepted dependency graph;
- the program has one coherent end-state objective;
- the Issue names the canonical slice contracts instead of duplicating or weakening them;
- every included slice retains its own scope, non-goals, constraints, acceptance criteria, verification obligations, reuse plan, provider evidence, physical evidence, and human gates;
- dependencies between included slices are explicit;
- implementation proceeds checkpoint-by-checkpoint in dependency order on one branch;
- no downstream checkpoint may rely on behavior from a predecessor that has not reached the evidence level required to make that dependency safe;
- a provider, physical-device, legal, licensing, or owner-admin gate may remain pending only when it is not required to safely implement the next checkpoint; pending evidence never counts as accepted evidence, and each gate must be resolved at the milestone required by its canonical contract;
- a material contract exception at any checkpoint stops the program and returns control rather than skipping, weakening, or silently deferring the blocked requirement;
- the final PR completion report contains a per-slice checkpoint matrix covering reuse/provenance, implementation status, targeted verification, terminal verification, provider/physical evidence, deviations, and remaining human gates.

Under this mode, the single program Issue maps to one implementation branch and one pull request. Intermediate slice checkpoints are execution boundaries inside that branch/PR, not separate Issues or PRs, unless a later explicit contract revision exits program mode.

Program mode changes execution packaging only. It does not collapse slice semantics, erase dependency edges, transfer human authority to Arena, waive external gates, or make pending evidence equivalent to acceptance.

### Arena-ready handoff

An Issue is **Arena-ready** when its contract satisfies the dispatch-eligibility and Issue-contract requirements above.

Creating or updating an Arena-ready Issue does not by itself mean Arena has started.

When the control plane makes an Issue Arena-ready, complete the handoff in the same user-facing reply:

- if a direct Arena launch mechanism is available, use it;
- otherwise return one short copy/paste prompt for the human to hand to Arena.

The fallback prompt must reference the repository Issue as the complete contract and must not duplicate its scope, constraints, acceptance criteria, or verification instructions. The user should not need a separate turn to ask for the prompt.

For new work, use this shape:

`Execute <owner/repository> Issue #<issue> in Arena. Treat the Issue as the complete implementation contract and return the resulting PR.`

For continuation work with an existing PR, use this shape:

`Continue <owner/repository> Issue #<issue> / PR #<pr> in Arena from the current branch and follow the latest Issue instructions.`

Do not say Arena was launched or dispatched unless a real launch mechanism was invoked. When using the fallback route, say the work is Arena-ready and present the prompt.

## 3. Arena autonomy

For non-trivial multi-step or multi-file implementation, Arena uses `../skills/incremental-implementation/SKILL.md`. Its evidence-first and reuse-before-reimplement gates are mandatory: Arena must recover repository truth, re-open material first-party authoritative sources for external contracts, inspect the relevant pinned implementation-harvest evidence, and execute the Issue's Implementation Reuse Plan before coding non-trivial behavior. Model memory or assumed API/framework knowledge is not implementation authority, and the ability to write equivalent code is not a reason to ignore an `EXISTING APPT`, `ADAPT`, or `PORT` disposition. When another lifecycle trigger from `../routing/capabilities.md` applies—including test-driven development—Arena also loads that skill rather than improvising an alternate method.

Within the approved contract Arena may:

- inspect relevant project code and documentation;
- choose implementation details;
- edit files inside scope;
- refactor locally when necessary to satisfy the contract;
- run targeted checks through the route hierarchy in `../routing/route.md`;
- diagnose implementation failures;
- retry within the same accepted boundaries.

Arena must not create ad-hoc workflow infrastructure merely because a local test is inconvenient. Follow `../routing/route.md`: narrow local execution first, the smallest existing diagnostic mode for a proven blocker, and temporary branch-scoped workflow YAML only as last-resort infrastructure when neither route can perform the exact operation.

Arena must not silently change material:

- scope;
- architecture;
- accepted requirements;
- user-visible behavior;
- security or trust boundaries;
- major dependency/tooling decisions;
- data models or external contracts;
- source-licence or implementation-reuse boundaries;
- out-of-scope systems.

## 4. Contract exceptions

When implementation evidence shows the contract itself cannot safely or correctly be completed, Arena stops and reports:

- the blocked requirement or constraint;
- repository/evidence supporting the exception;
- why the current contract cannot be completed safely or correctly;
- the smallest decision needed from the control plane;
- work already completed;
- current verification state.

Execution discovery may challenge a contract. It may not silently rewrite it.

## 5. Contract revisions

Material contract changes remain on the same Issue as an explicit new revision.

Record:

- revision number;
- what changed;
- why it changed;
- which previous implementation remains valid, if applicable.

Arena always executes against an identifiable contract revision.

An implementation miss does not create a contract revision. It remains a correction under the current contract.

## 6. Branch and pull request boundary

One Arena Issue maps to one Arena implementation branch and one pull request. In ordinary mode that Issue contains one implementation slice/unit. In §2A contiguous program mode the Issue may contain the explicitly authorized ordered slice range, still on one branch and one pull request.

Arena does not implement directly on `main`.

Bounded corrections remain on the same branch and PR.

A contract revision normally continues on that branch/PR unless the revision invalidates so much of the candidate that a clean restart is explicitly chosen by the control plane.

## 7. Arena PR completion report

Before Arena calls a candidate complete, fixed, passing, ready, or technically verified, it must use `../skills/verification-before-completion/SKILL.md` and satisfy its fresh-evidence gate for the exact candidate.

The PR should contain:

### Contract

Issue #<n> — Revision <n>

### Changes

Concise description of what was actually changed.

### Reuse & Provenance

For each material implementation surface, report the executed disposition and source:

- `EXISTING APPT`, `ADAPT`, `PORT`, `CLEAN-ROOM REIMPLEMENT`, `BEHAVIORAL REFERENCE`, `TEST-VECTOR/DATA`, or `NEW`;
- pinned upstream reference when applicable;
- tests/vectors reused or ported;
- for `NEW`, the reason the Issue authorized new implementation.

State any deviation from the Issue's reuse plan. A material change of licence/reuse boundary returns to the control plane instead of being silently improvised.

### Verification

List targeted checks and their results, plus terminal repository verification and its result.

### Scope

Identify the areas materially touched and whether implementation remained within scope.

### Deviations

State any deliberate difference, limitation, or incomplete requirement. Write `None` when there are none.

### Review Notes

Identify anything the control plane or the human should inspect particularly closely.

Do not turn the PR into an implementation diary. Final evidence and unresolved limitations matter; every exploratory command does not.

## 8. Control-plane contract review

The control plane uses `../skills/code-review/SKILL.md` for this review so accepted-contract compliance and engineering quality are examined separately before human acceptance.

The control plane reviews the PR against the active Issue revision, its Implementation Reuse Plan, and available technical evidence.

The only contract-review outcomes are:

### CONTRACT-COMPLIANT

The candidate satisfies the active contract with sufficient evidence.

This is not human acceptance and does not authorize merge by itself.

### CORRECTION REQUIRED

The contract remains valid, but the implementation misses one or more requirements or contains a bounded implementation defect.

State the exact correction required. Arena returns to the same branch/PR and corrects only that bounded gap.

### CONTRACT EXCEPTION

Review evidence shows the contract itself must change or requires a material project-level decision.

Return to the control plane/human instead of expanding the correction loop.

## 9. Mandatory diagnostic gate for failures and corrections

Any bug, failing test, build/CI failure, regression, unexpected behavior, or bounded implementation defect encountered during Arena work must pass through diagnosis before another fix attempt or terminal verification run.

Arena must:

1. read the actual failure evidence;
2. use `../skills/debugging-recovery/SKILL.md` to build the tightest reliable reproducer when practical;
3. for an existing PR correction, also use `../skills/pr-integration-correction/SKILL.md`;
4. confirm the reproducer fails for the reported symptom rather than setup noise;
5. establish an evidence-backed root cause before editing;
6. make the smallest correction that targets that root cause;
7. add or retain regression protection where practical;
8. regain targeted green evidence for the reproducer and affected scope;
9. only then produce a new finished candidate for terminal repository verification.

If the current environment cannot run the practical reproducer, Arena must report the exact failed capability/tooling probe and use `../routing/route.md` to try the narrowest safe alternate route. If that route provides equivalent targeted evidence for the same failure and affected scope, that evidence satisfies step 8. If no safe route can provide equivalent targeted evidence, Arena must stop the correction cycle and return control with the blocker report; it must not edit further, produce a new finished candidate, or run terminal repository verification. It must not use repeated full CI/workflow runs as the primary edit-run loop.

A correction report must identify:

- Symptom
- Reproducer, or explicit inability to reproduce with blocker evidence
- Root Cause
- Minimal Fix
- Regression Protection, when practical
- Targeted Verification
- Remaining Terminal / Provider Gates

This gate applies to every Arena Issue and PR correction. It does not create a contract revision unless diagnosis proves the contract itself must materially change.

## 10. Correction cycle

Implementation miss:

`same contract → reproduce → root cause → bounded correction → targeted green → new finished candidate → terminal repository verification → contract review`

Contract flaw:

`contract exception → control-plane decision → explicit contract revision → Arena resumes`

Do not close and recreate Issues for ordinary implementation corrections.

## 11. Acceptance and merge authority

Keep these states distinct:

`VERIFIED ≠ CONTRACT-COMPLIANT ≠ ACCEPTED ≠ MERGED`

- Arena establishes technical verification evidence.
- The control plane determines contract compliance.
- The human gives final acceptance.
- Arena never merges its own work.
- After explicit human acceptance, the merge may be performed mechanically by an authorized GitHub actor.

## 12. Issue closure and state reconciliation

After an accepted PR merges:

1. close the Issue;
2. keep Issue/PR/Git history as the permanent execution record;
3. do not create a duplicate `.project-ai/history/`;
4. reconcile `PROJECT_STATE.md` only when the accepted merge materially changed durable project position.

No durable state change means no state update.