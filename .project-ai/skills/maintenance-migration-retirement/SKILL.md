---
name: maintenance-migration-retirement
description: Use when software, dependencies, APIs, schemas, services, features, infrastructure, or data need to be maintained, upgraded, replaced, migrated, deprecated, removed, or retired without breaking real consumers or losing control of compatibility and data safety.
---

# Maintenance, Migration, and Retirement

## Purpose

Manage software beyond initial delivery.

Measure real consumers, choose the right lifecycle action, migrate compatibly, protect data, and remove obsolete code and supporting assets only after evidence shows they are no longer needed.

## Workflow

### 1. Measure current value and consumers

Identify:

- unique value still provided;
- active code/service/user consumers;
- maintenance/security/operational cost;
- dependency health;
- migration burden;
- unknown consumers.

Use runtime telemetry, dependency analysis, configuration, API usage, deployment inventory, and code search as appropriate.

"Probably unused" is not evidence.

### 2. Choose the lifecycle action

Choose deliberately:

- **MAINTAIN**
- **UPGRADE**
- **REPLACE**
- **MIGRATE**
- **DEPRECATE**
- **REMOVE**
- **RETIRE**

Newer is not automatically better.

### 3. Define target state and compatibility

State:

- target version/replacement/schema/contract;
- compatibility expectations;
- migration path;
- success evidence;
- recovery strategy;
- deadline only when justified.

If consumers still need the capability, provide a viable replacement before compulsory removal unless emergency security/safety conditions make that impossible.

### 4. Choose advisory or compulsory deprecation

Use advisory deprecation when safe coexistence is acceptable.

Use compulsory deprecation only when continued operation creates unacceptable security, reliability, cost, compatibility, or delivery risk.

Provide a clear migration path and justified timeline.

### 5. Prefer expand → migrate → contract

For compatibility-sensitive changes:

1. **Expand** — add the new contract/schema/path beside the old.
2. **Migrate** — move consumers/data in bounded batches and verify each.
3. **Contract** — remove the old form only after evidence shows it is unused.

Apply this beyond databases where it fits.

### 6. Upgrade dependencies deliberately

For major upgrades:

- read release/migration guidance;
- identify breaking changes and supported version combinations;
- inspect peer/transitive constraints;
- stage risky jumps;
- keep lockfile state coherent;
- verify each stage;
- avoid unrelated dependency churn.

Security urgency can change pace, not the need for compatibility evidence.

### 7. Protect data migrations

Separate schema expansion, application compatibility, backfill, cutover, and contraction where needed.

Make backfills restartable/idempotent where practical, bound load, measure progress, verify invariants, and account for mixed-version deployments.

Use backup/restore or forward-repair strategies according to real recovery needs.

Do not require a destructive "down migration" when rollback would lose valid newer data.

### 8. Migrate consumers incrementally

For each bounded consumer set:

1. identify touchpoints;
2. move to the target;
3. verify behavior;
4. observe health where relevant;
5. remove old consumer-specific references;
6. update migration inventory.

### 9. Prove zero old-path use

Before destructive removal, verify as applicable:

- no active calls;
- no configured consumers;
- no code references;
- no delayed retries/queues that can return;
- no external contractual consumers;
- required retention windows passed.

### 10. Remove supporting assets completely

Retirement may include:

- code and tests;
- adapters and flags;
- dependencies;
- configuration/secrets;
- CI jobs;
- infrastructure;
- dashboards/alerts/runbooks;
- documentation;
- data subject to retention obligations;
- integrations and access grants.

Avoid zombie infrastructure and compatibility layers.

### 11. Verify the target state

Follow `../../execution/verification.md`.

Confirm migrated consumers work, old-path use is cleared, data invariants hold, hidden dependencies did not break, and the new state is supportable.

## Output contract

Produce:

- Lifecycle Decision
- Consumer / Dependency Inventory
- Target State
- Compatibility Strategy
- Migration Stages
- Data Safety / Recovery
- Deprecation Policy
- Removal Evidence
- Cleanup
- Residual Risk

## Boundaries

- Do not remove based on code search alone when runtime consumers may exist.
- Do not force deprecation before a viable replacement without a justified emergency reason.
- Do not combine unrelated dependency upgrades casually.
- Do not make destructive schema changes before compatibility exists.
- Do not assume application rollback reverses data.
- Do not require reversible down migrations universally.
- Do not leave obsolete flags, secrets, infrastructure, or monitoring behind.
- Do not use maintenance as a pretext for unrelated modernization.

## Completion gate

Before declaring migration or retirement complete, confirm:

- lifecycle action is explicit;
- real consumers were measured;
- target state works;
- compatibility protected active consumers;
- data migration has evidence and recovery strategy;
- destructive removal waited for zero-use evidence;
- obsolete supporting assets were cleaned up;
- project docs/operations reflect the new state;
- final verification applies to the post-cleanup candidate.