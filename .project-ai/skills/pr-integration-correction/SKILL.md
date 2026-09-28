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

For each failing technical domain, identify the smallest affected surface and collect all practical independent failures inside it before editing. Use project-owned tool capabilities such as non-fail-fast/continue modes, complete test reporting, independent sub-checks, report artifacts, or temporary diagnostics when needed. If a composite command stops after its first child failure, run the remaining owned child checks separately when they are independent and safe rather than assuming the first error is the whole failure set.

Separate technical failures from human or merge gates, then classify/group the technical failures before correction.

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

### 5. Diagnose CI failures narrowly and comprehensively

Read the actual failing evidence.

Create or run the smallest useful reproducer, then map the bounded failure surface before correction. "Narrow" describes scope, not a requirement to stop after the first error: use the actual toolchain's safe diagnostic features to expose sibling failures in that scope, classify/group them, establish root cause, correct coherently, and regain focused green locally where practical.

Use `../debugging-recovery/SKILL.md` for non-trivial root-cause work and `../test-driven-development/SKILL.md` when regression protection is appropriate.

CI should confirm the fix rather than serve as the only edit-run loop. Do not push one speculative correction per newly discovered error when a bounded diagnostic pass can surface the set first.

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

- Do not change code before reading the failing evidence and mapping the practical bounded failure set.
- Do not use push-and-pray or one-error-at-a-time CI loops when the same bounded diagnostic can expose independent failures.
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
- each failing technical domain was diagnosed broadly enough to expose its practical independent failure set, or the limitation is explicit;
- root causes, not symptoms, were corrected;
- valid feedback was implemented and invalid feedback was technically rejected;
- corrections stayed within scope;
- contract exceptions were escalated;
- fresh verification applies to the current head;
- acceptance and merge remain separate gates.