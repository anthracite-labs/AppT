---
name: debugging-recovery
description: Use when a bug, failing test, build failure, regression, unexpected behavior, integration failure, or performance anomaly needs a reproducible signal and evidence-based root-cause diagnosis before a fix is attempted.
---

# Debugging and Recovery

## Purpose

Create a tight failure signal, map the bounded failure surface, test falsifiable hypotheses, correct root causes coherently, and prove recovery.

The first job is diagnosis, not editing. Narrow scope and broad evidence are complementary: diagnose the smallest relevant surface, then expose as much trustworthy failure evidence as practical inside that surface before changing code.

## Workflow

### 1. State the symptom

Separate observation from theory:

- expected behavior;
- observed behavior;
- environment/context;
- frequency;
- earliest known occurrence;
- relevant recent changes when known.

### 2. Build the tightest reliable reproducer

Use the cheapest loop that can reliably show the failure:

- focused test;
- CLI/API invocation;
- small harness;
- browser reproduction;
- trace query;
- minimal fixture;
- benchmark;
- differential comparison;
- bisect.

Confirm the reproducer fails for the reported symptom rather than for its own setup error.

### 3. Minimize the failing surface

Reduce variables without removing the real trigger.

Compare known-good and failing inputs, versions, configurations, dependencies, or environments where useful.

### 4. Map the bounded failure surface

Before correction, collect the practical independent failures inside the smallest relevant surface.

Prefer the toolchain's native diagnostic capabilities rather than stopping at the first visible symptom. Depending on the owner this may include:

- continue-on-independent-failure or non-fail-fast execution;
- compiler or linter multi-error reporting;
- running independent owned sub-checks when an umbrella command stops early;
- complete test-suite reporting within the selected component;
- structured test, lint, coverage, trace, or build artifacts;
- temporary listeners, assertions, logging, harnesses, or report extraction when normal output hides the useful detail.

These are diagnostic techniques, not universal flags. Discover the behavior of the actual project-owned tool before choosing one. A mechanism such as Gradle `--continue` can expose independent task failures but cannot make work that depends on a failed prerequisite valid or runnable.

Do not widen to the whole repository merely to collect more errors. Stop broad collection when later evidence would be invalid, unsafe, destructive, dominated by one prerequisite failure, or outside the affected surface.

Record the observed failure set, then group items by likely shared root cause or by distinct failure class before editing. Do not enter an edit-run loop after each newly visible symptom when the same bounded diagnostic pass can safely reveal sibling failures.

### 5. Form falsifiable hypotheses

For each plausible cause, state:

- hypothesis;
- evidence that would support it;
- evidence that would reject it;
- cheapest next observation.

Change one independent variable at a time.

### 6. Instrument only where evidence is missing

Add targeted temporary logs, assertions, spans, counters, timing, or state snapshots.

Do not flood logs or expose secrets/sensitive data.

### 7. Trace to root cause

Distinguish:

- symptom location;
- propagation path;
- earliest correctable source condition.

Do not patch a downstream symptom while an upstream invariant remains broken.

### 8. Correct minimally

Make the smallest coherent source change that removes the evidenced root cause while preserving unrelated behavior.

A grouped correction may resolve multiple observed failures when the evidence shows they share a root cause, or when several independently classified defects can be corrected without obscuring which change addresses which failure. Do not shotgun speculative fixes.

If the real fix requires a material contract change, escalate rather than hiding it inside debugging.

### 9. Add regression protection

Where practical, turn the reproducer into a durable test or deterministic check.

For a bug fix, the regression test should fail on the broken behavior and pass on the fix.

### 10. Verify recovery

Follow `../../execution/verification.md`.

At minimum, verify:

- original reproducer is green;
- affected-scope checks are green;
- original user/system scenario works where practical.

For stateful failures, also verify the recovered state independently.

### 11. Clean up and route durable learning

Remove temporary diagnostics unless they have earned a permanent observability role.

Route lasting gaps to their owner: test, architecture, observability, runbook, documentation, or follow-up work.

Do not create a permanent debugging diary.

## Output contract

Produce:

- Symptom
- Reproducer
- Bounded Failure Surface
- Failure Classification / Grouping
- Root Cause
- Minimal Fix
- Regression Protection
- Recovery action when state restoration is needed
- Fresh Verification
- Durable Follow-up only where warranted

## Boundaries

- Do not edit first when a reproducer is practical.
- Do not stop at the first visible error when the same bounded diagnostic can safely expose independent failures.
- Do not confuse comprehensive evidence inside a narrow scope with running the whole repository.
- Do not shotgun multiple speculative fixes in one experiment.
- Do not accept "probably" as root cause.
- Do not use remote CI as the only loop for a locally reproducible failure.
- Do not assume application rollback reverses data or external side effects.
- Do not use debugging to silently rewrite requirements.
- For an active production incident, stabilize via incident-response first.

## Completion gate

Before declaring recovery, confirm:

- the failure was reproduced or the inability to reproduce is explicit;
- the practical failure set inside the chosen scope was collected, or the reason it could not be collected is explicit;
- observed failures were classified/grouped before correction;
- root cause is evidence-backed;
- the fix targets root cause;
- regression protection exists where practical;
- the original reproducer and affected scope are green;
- state recovery is verified when applicable;
- temporary diagnostics are cleaned up;
- material contract changes were escalated.