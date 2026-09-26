---
name: test-driven-development
description: Use when implementing new behavior, fixing a bug, changing existing behavior, or adding an edge case where an executable test can define the expected result before production code is written.
---

# Test-Driven Development

## Purpose

Use tests as executable behavioral specifications.

A real TDD cycle observes the required behavior fail before implementation, makes the minimum change to pass, then refactors while keeping behavior green.

## Workflow

### 1. Discover the repository's test system

Identify:

- test framework;
- focused-test command;
- broader test command;
- test locations and naming;
- neighboring conventions;
- CI commands that gate the project.

Do not assume a default command or framework.

### 2. Choose the seam

Test through the narrowest stable observable interface that proves the required behavior.

Prefer public/module/API/CLI/state-transition/integration behavior over private call order.

The seam should survive ordinary internal refactoring.

### 3. Write one failing behavior

Express:

- precondition/input;
- action;
- observable expected result.

Keep one behavioral reason for failure.

### 4. Observe RED

Run the focused test before production implementation.

Confirm it fails because the behavior is absent or wrong.

A test that passes immediately, fails because setup is broken, or fails for a typo has not established useful RED evidence.

### 5. Make the minimum change to GREEN

Implement only enough correct behavior to satisfy the failing test.

Run the focused test and observe it pass.

Do not solve hypothetical future requirements during GREEN.

### 6. Refactor while green

Improve naming, duplication, control flow, module depth, or structure without changing behavior.

Re-run relevant tests after refactoring.

### 7. Repeat vertically

Add the next meaningful behavior through another RED → GREEN → REFACTOR cycle.

Prefer thin capability progress over building all low-level helpers before one complete path works.

### 8. For bugs, prove the regression

Reproduce the reported bug in a test at the best available seam before fixing it.

After the root-cause fix, retain the test and verify the original real-world scenario where practical.

## Decision rules

A strong test:

- fails when the required behavior breaks;
- asserts behavior rather than private implementation;
- derives expected values independently of production logic;
- uses mocks/fakes only where they preserve meaningful boundary behavior;
- provides a diagnostic failure;
- runs at the cheapest level that proves the contract.

Coverage percentage is not a substitute for these properties.

## Output contract

For each behavior, preserve:

- the test that specified it;
- observed RED evidence;
- minimal GREEN implementation;
- any refactor performed while green;
- regression protection for bugs.

Final candidate verification still follows the canonical verification strategy.

## Boundaries

- Do not write production behavior first and backfill a test while calling it TDD.
- Do not accept a test that never demonstrated the bug or missing behavior when RED is feasible.
- Do not couple tests unnecessarily to private internals.
- Do not mock away the boundary whose behavior needs proof.
- Do not run terminal repository verification on every TDD iteration.
- Do not force TDD onto documentation-only, generated, or purely mechanical changes where another check is more direct.

## Completion gate

Before handoff, confirm:

- the seam was intentionally chosen;
- RED was observed for the expected reason;
- GREEN was observed after the implementation;
- refactoring preserved behavior;
- bug fixes retain regression protection;
- tests prove observable behavior rather than incidental wiring;
- broader candidate verification remains pending until the implementation unit is finished.