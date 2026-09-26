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
- the work can be expressed as one sensible reviewable implementation unit.

Plan only to the depth required to make the Issue executable. Do not pre-implement the solution in prose.

If the work cannot fit one sensible branch/PR, reconsider decomposition before dispatch rather than allowing Arena to invent project-level work decomposition.

## 2. Canonical Arena Issue contract

Every Arena Issue uses this lean structure:

### Objective

State what must become true.

### Context & Authority

Give only the accepted decisions and canonical repository references Arena needs to execute correctly.

Do not paste chat history or duplicate all of `PROJECT_STATE.md`.

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

## 3. Arena autonomy

Within the approved contract Arena may:

- inspect relevant project code and documentation;
- choose implementation details;
- edit files inside scope;
- refactor locally when necessary to satisfy the contract;
- run targeted checks;
- diagnose implementation failures;
- retry within the same accepted boundaries.

Arena must not silently change material:

- scope;
- architecture;
- accepted requirements;
- user-visible behavior;
- security or trust boundaries;
- major dependency/tooling decisions;
- data models or external contracts;
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

One Arena Issue maps to one Arena implementation branch and one pull request.

Arena does not implement directly on `main`.

Bounded corrections remain on the same branch and PR.

A contract revision normally continues on that branch/PR unless the revision invalidates so much of the candidate that a clean restart is explicitly chosen by the control plane.

## 7. Arena PR completion report

The PR should contain:

### Contract

Issue #<n> — Revision <n>

### Changes

Concise description of what was actually changed.

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

The control plane reviews the PR against the active Issue revision and available technical evidence.

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

## 9. Correction cycle

Implementation miss:

`same contract → bounded correction → targeted verification → new finished candidate → terminal repository verification → contract review`

Contract flaw:

`contract exception → control-plane decision → explicit contract revision → Arena resumes`

Do not close and recreate Issues for ordinary implementation corrections.

## 10. Acceptance and merge authority

Keep these states distinct:

`VERIFIED ≠ CONTRACT-COMPLIANT ≠ ACCEPTED ≠ MERGED`

- Arena establishes technical verification evidence.
- The control plane determines contract compliance.
- The human gives final acceptance.
- Arena never merges its own work.
- After explicit human acceptance, the merge may be performed mechanically by an authorized GitHub actor.

## 11. Issue closure and state reconciliation

After an accepted PR merges:

1. close the Issue;
2. keep Issue/PR/Git history as the permanent execution record;
3. do not create a duplicate `.project-ai/history/`;
4. reconcile `PROJECT_STATE.md` only when the accepted merge materially changed durable project position.

No durable state change means no state update.