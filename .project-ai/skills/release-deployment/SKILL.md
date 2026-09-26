---
name: release-deployment
description: Use when an accepted change, artifact, package, configuration, infrastructure change, migration, or feature exposure is ready to move into a target environment, registry, or user population and needs deployment, rollout, health verification, or recovery planning.
---

# Release and Deployment

## Purpose

Move an identifiable accepted candidate to its intended target and prove it is healthy enough to remain there.

Keep merge, deployment, health, and release completion as distinct states.

## Workflow

### 1. Identify candidate and target

Record the exact commit, tag, version, or artifact and the target environment, registry, or user population.

Prefer promoting an identifiable built artifact rather than relying on ambiguous mutable state.

### 2. Run proportionate preflight checks

Check only prerequisites that affect the release decision, such as:

- current target health;
- dependencies and capacity;
- required secrets/configuration;
- migration prerequisites;
- backups where recovery depends on them;
- change-window/provider constraints;
- observability availability;
- active incident status.

### 3. Define advance, hold, and recovery criteria

Before rollout, state the signals for:

- **ADVANCE** — continue;
- **HOLD** — stop expansion and investigate;
- **RECOVER** — roll back, disable, fail over, restore, or forward-fix.

Use project baselines and requirements, not generic percentage thresholds.

### 4. Deploy, publish, or enable

Use the project's real release mechanism.

Track enough provenance to know exactly what action applied to which candidate and target.

### 5. Run smoke verification

Immediately verify the critical basics appropriate to the release:

- expected version/artifact is active;
- core user/API path works;
- essential dependencies connect;
- no obvious critical error signal appears.

Prefer existing smoke tests.

### 6. Observe health

Check the signals that represent real user/system health: errors, latency, throughput, queues/jobs, data integrity, critical business behavior, alerts, and external dependencies as applicable.

A successful deployment job does not prove application health.

### 7. Expand exposure only when risk justifies it

Use internal users, staged environments, canaries, traffic shifting, blue/green, or feature flags only when they reduce a real risk.

Do not impose fixed percentages or wait windows.

### 8. Recover when criteria fail

Use the safest supported recovery path:

- previous artifact;
- traffic shift;
- feature disable;
- configuration revert;
- restore;
- data recovery;
- forward-fix.

Do not assume application rollback reverses data or external side effects.

Verify the recovered state independently.

### 9. Conclude the release

Use one status:

- **RELEASED**
- **HELD**
- **RECOVERED**
- **FAILED**

Track real cleanup or monitoring work that remains.

## Output contract

Produce:

- Candidate
- Target
- Preflight evidence
- Rollout action
- Smoke evidence
- Health evidence
- Recovery path and whether used
- Status
- Required follow-up

## Boundaries

- Do not release an unidentified mutable candidate.
- Do not equate workflow success with user health.
- Do not impose feature flags, canaries, staging, or manual gates on every project.
- Do not invent rollback for irreversible data changes.
- Do not merge implementation work from this skill.
- Do not declare RELEASED while required health signals are unknown.

## Completion gate

Before stating RELEASED, confirm:

- exact candidate and target are known;
- required approvals were preserved;
- material preflight checks passed;
- advance/hold/recovery criteria existed before observation;
- deployment/publication completed;
- smoke and health evidence are good;
- recovery is understood;
- no unresolved material release failure remains.