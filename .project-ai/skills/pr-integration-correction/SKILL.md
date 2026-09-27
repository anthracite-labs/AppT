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

### 5. Diagnose CI failures narrowly

Read the actual failing log.

Create or run the smallest useful reproducer, establish root cause, correct it, and regain focused green locally where practical.

Use `../debugging-recovery/SKILL.md` for non-trivial root-cause work and `../test-driven-development/SKILL.md` when regression protection is appropriate.

CI should confirm the fix rather than serve as the only edit-run loop.

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
- Resolved Items: evidence → root cause → correction → focused verification
- Unresolved Items
- Contract Exceptions
- Current Verification State
- Remaining Human / Provider Gates

## Boundaries

- Do not change code before reading the failing evidence.
- Do not use push-and-pray CI loops.
- Do not implement review comments merely because they were requested.
- Do not treat style preference as a contract requirement.
- Do not create a new PR for an ordinary implementation miss.
- Do not hide material contract changes inside a correction.
- Do not merge because checks are green.

## Completion gate

Before handoff, confirm:

- current head and active contract are known;
- every actionable item is classified;
- code failures were reproduced narrowly where practical;
- root causes, not symptoms, were corrected;
- valid feedback was implemented and invalid feedback was technically rejected;
- corrections stayed within scope;
- contract exceptions were escalated;
- fresh verification applies to the current head;
- acceptance and merge remain separate gates.