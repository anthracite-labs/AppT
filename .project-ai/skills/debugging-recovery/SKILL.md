---
name: debugging-recovery
description: Use when a bug, failing test, build failure, regression, unexpected behavior, integration failure, or performance anomaly needs a reproducible signal and evidence-based root-cause diagnosis before a fix is attempted.
---

# Debugging and Recovery

## Purpose

Create a tight failure signal, expose the bounded failure surface, test falsifiable hypotheses, correct root causes coherently, and prove recovery.

The first job is diagnosis, not editing. A confirmed first failure is the start of diagnosis, not automatically the whole failure set.

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

### 3. Expose the bounded failure surface

After the reproducer confirms the symptom, determine whether the same affected surface can contain independent failures. Before correcting code, harvest as much actionable evidence as practical from that bounded surface.

Use the native capabilities of the actual toolchain rather than assuming one universal flag. Depending on the tool, that may include:

- continue-on-failure execution for independent tasks;
- non-fail-fast test execution;
- compiler, linter, type-checker, or static-analysis multi-error reporting;
- structured test/analysis reports and uploaded artifacts;
- stack traces, verbose diagnostics, or machine-readable output;
- targeted temporary listeners, assertions, traces, or logs when normal reports are insufficient.

Preserve strict failure semantics: diagnostic continuation exists to reveal more evidence, never to convert a failing check into a passing one.

Do not widen to unrelated repository domains merely to collect more errors. Maximize useful evidence inside the smallest relevant affected surface.

If an upstream failure prevents dependent work from running, record that dependency. Correct only the blocker needed to expose the remaining bounded surface, then rerun the evidence-harvesting pass before beginning broader correction.

Capture the observed failures as a set and distinguish independent failures from downstream symptoms before choosing edits.

### 4. Minimize the failing surface

Reduce variables without removing the real trigger.

Compare known-good and failing inputs, versions, configurations, dependencies, or environments where useful.

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

Make the smallest source change that removes the root cause while preserving unrelated behavior.

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
- Bounded Failure Set and root-cause grouping
- Root Cause
- Minimal Fix
- Regression Protection
- Recovery action when state restoration is needed
- Fresh Verification
- Durable Follow-up only where warranted

## Boundaries

- Do not edit first when a reproducer is practical.
- Do not shotgun multiple fixes in one experiment.
- Do not enter a one-failure-at-a-time edit/push/CI loop when the bounded toolchain can expose independent failures in one diagnostic pass.
- Do not accept "probably" as root cause.
- Do not use remote CI as the only loop for a locally reproducible failure.
- Do not assume application rollback reverses data or external side effects.
- Do not use debugging to silently rewrite requirements.
- For an active production incident, stabilize via incident-response first.

## Completion gate

Before declaring recovery, confirm:

- the failure was reproduced or the inability to reproduce is explicit;
- the bounded affected surface was harvested for practical independent failures, or the reason that was not possible is explicit;
- root cause is evidence-backed;
- the fix targets root cause;
- regression protection exists where practical;
- the original reproducer and affected scope are green;
- state recovery is verified when applicable;
- temporary diagnostics are cleaned up;
- material contract changes were escalated.