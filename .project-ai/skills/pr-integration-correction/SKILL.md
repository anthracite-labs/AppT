---
name: pr-integration-correction
description: Use when an existing pull request has failing actionable CI, substantive review feedback, a contract-review correction, a flaky or infrastructure failure needing classification, or a bounded implementation defect that should stay on the same branch and PR.
---

# PR Integration and Correction

## Purpose

Turn review and CI evidence into bounded corrections without using remote CI as the primary debugger, blindly accepting feedback, or changing the contract in secret.

Ordinary implementation misses stay on the same branch and PR.

## Authority

For Arena implementation PRs, read `../../execution/arena-dispatch.md`. It owns contract revision, correction, acceptance, and merge boundaries.

This skill never authorizes merge.

## Workflow

### 1. Identify the live candidate

Establish:

- repository and PR;
- current head commit;
- active Issue contract/revision when applicable;
- current verification state;
- unresolved review findings.

Check whether older feedback still applies to the current head.

### 2. Collect evidence

Gather:

- failing checks and logs;
- review comments/findings;
- contract-review outcome;
- current diff;
- relevant project commands.

Separate technical failures from human or merge gates.

Before editing, when one failing domain can contain multiple independent failures, run or inspect the bounded diagnostic in the most evidence-rich mode the tool safely supports. Collect the practical failure set in one pass where possible: native continuation, non-bail test execution, compiler/static-analysis multi-error output, structured reports, and existing CI artifacts all qualify. A continuation mechanism must preserve the failing result.

Group related observations by likely root cause before classifying individual symptoms.

### 3. Classify each item

| Class | Action |
|---|---|
| Implementation defect | correct on the same branch |
| Test defect | correct only when the accepted behavior proves the test wrong |
| Review misunderstanding | reject with technical evidence |
| Infrastructure/transient | rerun or report; do not patch project code |
| Contract exception | return to the control plane |

### 4. Evaluate review feedback before editing

For each substantive comment:

1. understand the technical claim;
2. verify it against codebase reality;
3. check accepted requirements/architecture;
4. determine whether it is correct for this project;
5. implement only when valid.

Reviewer authority does not override accepted project authority.

### 5. Diagnose CI failures as a bounded set

Read the actual failing logs and reports.

Create or run the smallest useful reproducer to confirm the reported symptom, then use `../debugging-recovery/SKILL.md` to expose the practical independent failures in the affected surface before correction. Classify and group the resulting failure set by root cause, correct coherent causes minimally, and regain focused green locally where practical.

Use `../test-driven-development/SKILL.md` when regression protection is appropriate.

CI should confirm the grouped correction rather than serve as a one-error-at-a-time edit/push loop. If an upstream failure blocks dependent diagnostics, correct only that blocker, rerun the bounded failure-surface pass, and continue from the newly observed evidence.

### 6. Keep corrections bounded

Do not mix unrelated cleanup, broad refactoring, or new requirements into the correction.

If the fix needs a material change to scope, architecture, security, dependencies, data, interface, or user-visible behavior, raise a contract exception.

### 7. Verify and re-evaluate the new head

Use `../../execution/verification.md`.

Push the corrected candidate, inspect new provider results and high-signal feedback, and confirm previous corrections still hold.

If the same failure persists after reasonable root-cause attempts, report the blocker instead of looping blindly.

### 8. Stop at technical readiness

Stop when actionable technical checks and required corrections are resolved and only human/provider gates remain.

Do not mark accepted or merge from this skill.

## Output contract

Produce:

- PR / current head
- Resolved Items grouped by root cause: evidence → correction → focused verification
- Unresolved Items
- Contract Exceptions
- Current Verification State
- Remaining Human / Provider Gates

## Boundaries

- Do not change code before reading the failing evidence.
- Do not use push-and-pray CI loops.
- Do not discover and patch one CI failure at a time when the same bounded diagnostic can safely expose independent failures together.
- Do not implement review comments merely because they were requested.
- Do not treat style preference as a contract requirement.
- Do not create a new PR for an ordinary implementation miss.
- Do not hide material contract changes inside a correction.
- Do not merge because checks are green.

## Completion gate

Before handoff, confirm:

- current head and active contract are known;
- every actionable item is classified;
- the practical bounded failure set was collected before correction, or the evidence limit is explicit;
- code failures were reproduced narrowly where practical;
- root causes, not symptoms, were corrected;
- valid feedback was implemented and invalid feedback was technically rejected;
- corrections stayed within scope;
- contract exceptions were escalated;
- fresh verification applies to the current head;
- acceptance and merge remain separate gates.