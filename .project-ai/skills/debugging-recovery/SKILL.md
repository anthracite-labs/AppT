---
name: debugging-recovery
description: Use when a bug, failing test, build failure, regression, unexpected behavior, integration failure, or performance anomaly needs a reproducible signal and evidence-based root-cause diagnosis before a fix is attempted.
---

# Debugging and Recovery

## Purpose

Create a tight failure signal, test falsifiable hypotheses, correct the root cause, and prove recovery.

The first job is diagnosis, not editing.

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

### 4. Form falsifiable hypotheses

For each plausible cause, state:

- hypothesis;
- evidence that would support it;
- evidence that would reject it;
- cheapest next observation.

Change one independent variable at a time.

### 5. Instrument only where evidence is missing

Add targeted temporary logs, assertions, spans, counters, timing, or state snapshots.

Do not flood logs or expose secrets/sensitive data.

### 6. Trace to root cause

Distinguish:

- symptom location;
- propagation path;
- earliest correctable source condition.

Do not patch a downstream symptom while an upstream invariant remains broken.

### 7. Correct minimally

Make the smallest source change that removes the root cause while preserving unrelated behavior.

If the real fix requires a material contract change, escalate rather than hiding it inside debugging.

### 8. Add regression protection

Where practical, turn the reproducer into a durable test or deterministic check.

For a bug fix, the regression test should fail on the broken behavior and pass on the fix.

### 9. Verify recovery

Follow `../../execution/verification.md`.

At minimum, verify:

- original reproducer is green;
- affected-scope checks are green;
- original user/system scenario works where practical.

For stateful failures, also verify the recovered state independently.

### 10. Clean up and route durable learning

Remove temporary diagnostics unless they have earned a permanent observability role.

Route lasting gaps to their owner: test, architecture, observability, runbook, documentation, or follow-up work.

Do not create a permanent debugging diary.

## Output contract

Produce:

- Symptom
- Reproducer
- Root Cause
- Minimal Fix
- Regression Protection
- Recovery action when state restoration is needed
- Fresh Verification
- Durable Follow-up only where warranted

## Boundaries

- Do not edit first when a reproducer is practical.
- Do not shotgun multiple fixes in one experiment.
- Do not accept "probably" as root cause.
- Do not use remote CI as the only loop for a locally reproducible failure.
- Do not assume application rollback reverses data or external side effects.
- Do not use debugging to silently rewrite requirements.
- For an active production incident, stabilize via incident-response first.

## Completion gate

Before declaring recovery, confirm:

- the failure was reproduced or the inability to reproduce is explicit;
- root cause is evidence-backed;
- the fix targets root cause;
- regression protection exists where practical;
- the original reproducer and affected scope are green;
- state recovery is verified when applicable;
- temporary diagnostics are cleaned up;
- material contract changes were escalated.