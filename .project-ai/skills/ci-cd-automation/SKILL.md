---
name: ci-cd-automation
description: Use when establishing or changing automated build, test, quality, security, packaging, publication, deployment, promotion, or workflow behavior, especially when CI is slow, duplicated, privileged, flaky, or inconsistent with local project commands.
---

# CI/CD and Automation

## Purpose

Automate project truth rather than inventing a second execution model.

CI/CD should provide fast trustworthy feedback, protect privileged execution, and scale delivery rigor to the project's real release risk.

## Workflow

### 1. Discover project-owned commands

Identify the actual package manager, build, tests, static checks, generated-state checks, packaging, publication, and deployment mechanisms.

CI should invoke those owners rather than define different behavior.

### 2. Define the workflow objective

State what the automation must prove or perform, such as PR verification, artifact build, publication, deployment, scheduled maintenance, or generated-state enforcement.

Separate read-only verification from mutation where practical.

### 3. Order feedback from fast to expensive

Prefer the project's equivalent of:

1. cheap static/configuration checks;
2. focused/unit tests;
3. broader integration/system tests;
4. build/package;
5. security/policy checks where justified;
6. environment/deployment checks.

Use terminal repository verification only for a finished candidate.

### 4. Keep execution reproducible

Use project-owned versions, lockfiles, wrappers, and immutable/frozen installation where the ecosystem supports them.

Verification jobs must not silently mutate dependency or generated state.

### 5. Minimize privilege and protect untrusted execution

Default verification to read-only.

Grant write permissions only to workflows that need them.

For privileged workflows:

- minimize token permissions;
- do not execute untrusted PR-controlled code with elevated credentials;
- protect secrets and environments;
- validate workflow inputs;
- prefer short-lived identity where supported and appropriate;
- follow project/organization pinning policy for third-party actions/tooling.

### 6. Optimize without weakening coverage

Use caching, concurrency cancellation, path filtering, matrix control, artifact reuse, and deduplication only when they preserve the required signal.

Every matrix dimension should represent a supported compatibility or real risk.

Measure recurring duration, queue time, flaky failures, expensive jobs, and duplicated work before optimizing aggressively.

### 7. Design artifact promotion and deployment proportionately

Where appropriate, build an identifiable artifact once and promote that candidate.

Choose staging, approvals, canary, blue/green, feature flags, zero-downtime techniques, or automatic rollback only when project risk justifies them.

### 8. Define failure and recovery

State what happens when verification or deployment fails.

Recovery may be a previous artifact, traffic shift, flag disable, configuration revert, restore, or forward-fix.

Do not assume every stateful change is reversibly roll-backable.

### 9. Verify workflow behavior

Follow `../../execution/verification.md`.

Use local/static validation first where it can prove the change; use provider execution when provider behavior itself is part of the contract.

## Output contract

For each workflow, establish:

- Objective
- Triggers
- Minimum Permissions
- Ordered Gates / Actions
- Artifacts
- Environment / Promotion behavior when applicable
- Failure / Recovery behavior
- Efficiency controls
- Security controls

## Boundaries

- CI must not become the canonical owner of project commands.
- Do not give every job write permission.
- Do not run untrusted code in privileged contexts casually.
- Do not add unsupported matrix combinations or generic environments.
- Do not use remote CI as the primary debugger for a narrowly reproducible failure.
- Do not define deployment success only as a workflow exit code.

## Completion gate

Before handoff, confirm:

- automation uses real project commands;
- fast feedback precedes expensive work;
- verification and mutation privileges are separated where practical;
- untrusted execution cannot casually reach privileged credentials;
- efficiency controls preserve coverage;
- release rigor matches risk;
- deployed artifacts are identifiable when relevant;
- material failure has a recovery path.