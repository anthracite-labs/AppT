---
name: incident-response
description: Use when production has an active or credible outage, severe degradation, data-integrity failure, widespread dependency failure, security compromise, material SLO breach, or uncontrolled rollout impact that requires stabilization before ordinary debugging can proceed.
---

# Incident Response

## Purpose

Limit active harm and restore safe service.

When users, data, security, or availability are actively at risk, stabilize first and pursue perfect explanation after the system is safe enough.

## Workflow

### 1. Confirm impact

Establish:

- what is failing;
- who or what is affected;
- when it started or was detected;
- current user/business/security impact;
- whether harm is ongoing;
- evidence source.

Separate facts from hypotheses.

### 2. Classify severity by impact

Use the project's existing incident taxonomy.

If none exists, reason from blast radius, duration, data loss/corruption, security exposure, user impact, contractual/regulatory impact, and available workaround.

Do not invent organizational response-time commitments.

### 3. Establish proportionate coordination

For material incidents, make decision ownership, technical response, communications, and security/legal escalation clear.

Small teams may combine roles. Do not add ceremony that slows response.

### 4. Establish live system state

Use actual telemetry and provider evidence.

Check as relevant:

- errors, latency, throughput;
- queues/jobs;
- resource pressure;
- dependency health;
- data integrity;
- alerts/SLOs;
- recent deployments/configuration changes.

Compare with known baseline where possible.

### 5. Stabilize or contain harm

Choose the safest bounded mitigation likely to reduce impact: rollback, flag disable, traffic shift, destructive-job stop, isolation, load reduction, failover, or write pause as appropriate.

For each action, state:

- expected effect;
- signal that will confirm it;
- reversal/recovery path where practical.

Random changes are not mitigation.

### 6. Preserve evidence when required

For security, fraud, data corruption, or forensic-sensitive incidents, preserve relevant evidence before destructive cleanup when doing so does not worsen active harm.

Use `../security-engineering/SKILL.md` for suspected compromise.

### 7. Investigate enough to guide recovery

Correlate symptoms with recent changes, dependencies, resources, data shape, tenants, and error patterns.

Use `../debugging-recovery/SKILL.md` when a focused reproducer/hypothesis loop is useful.

The active-response goal is enough understanding to restore safe service, not a complete postmortem.

### 8. Communicate factual state

When stakeholder updates are warranted, report:

- current status;
- user impact;
- mitigation in progress;
- known uncertainty;
- next update point if organizational practice requires one.

Do not present speculative cause as fact.

### 9. Verify recovery

Confirm from user/system signals:

- affected path works;
- error/latency/throughput recover as relevant;
- queues/jobs resume or drain;
- data integrity is acceptable;
- no critical signal shows continuing impact.

### 10. Monitor and close active response

Observe long enough to establish stability based on project risk, not a universal timer.

Close active response when impact is resolved or accepted degraded mode is stable, residual risks are known, temporary mitigations are tracked, and remaining work has an owner.

Hand meaningful learning to postmortem-learning.

## Output contract

Produce:

- Incident State
- Impact / Blast Radius
- Evidence
- Mitigations and observed effects
- Current evidence-backed hypothesis
- Recovery Verification
- Residual Risk
- Handoff / Follow-up

## Boundaries

- Do not chase perfect root cause while active harm continues.
- Do not make random changes without an expected observable effect.
- Do not roll back blindly across stateful/data changes.
- Do not infer blast radius from a single signal.
- Do not communicate speculative cause as fact.
- Do not expose sensitive incident evidence broadly.
- Do not close an incident because deployment succeeded while user signals remain bad.
- Do not escalate ordinary non-production bugs into incident process.

## Completion gate

Before ending active response, confirm:

- impact and blast radius are evidence-based;
- ongoing harm is contained or resolved;
- mitigation effects were observed;
- user/system health is verified;
- sensitive evidence was preserved where required;
- temporary mitigations and residual risk are tracked;
- remaining diagnostic work has an owner;
- meaningful learning is handed off.