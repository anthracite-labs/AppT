---
name: observability-operations-design
description: Use when production behavior needs logs, metrics, traces, health signals, actionable alerts, SLIs/SLOs, or better diagnostic evidence, especially for services, jobs, queues, retries, external dependencies, or features that would otherwise ship blind.
---

# Observability and Operations Design

## Purpose

Make production behavior answerable from outside the process.

Start from operator questions and user-visible reliability, then add only the telemetry needed to answer them.

## Decision rules

| Signal | Best for |
|---|---|
| Structured log | what happened in one specific operation and why |
| Metric | how often, how fast, how saturated, or how much |
| Trace | where time or failure propagated across boundaries |
| Health check | whether a component is usable for a defined platform purpose |

Use the cheapest signal that answers the question clearly.

## Workflow

### 1. State operator questions

Write the questions a responder must answer, such as:

- Is the capability working for users?
- How often does it fail?
- Why did one operation fail?
- Is a dependency slow or unavailable?
- Is a queue backing up?
- Did a deployment change behavior?

Every signal should support at least one real question.

### 2. Identify critical paths and failure modes

Use requirements and architecture to locate:

- user-critical flows;
- state transitions;
- external dependencies;
- async boundaries;
- retries;
- queues/jobs;
- invariants whose violation matters.

Instrument the path, not every line.

### 3. Design logs, metrics, and traces deliberately

Prefer structured log events with stable fields.

Use bounded-cardinality metric dimensions; never use request IDs, raw URLs, emails, error messages, or other unbounded values as labels.

Propagate correlation/trace context across network and async boundaries where reconstruction matters.

### 4. Protect telemetry

Treat telemetry as a data system.

Keep secrets, credentials, full payment data, and unnecessary personal data out of logs/traces.

Use field allowlists and project-appropriate retention/access controls.

### 5. Define reliability signals where needed

For request-driven systems, rate/errors/duration may be useful.

For resources, utilization/saturation/errors may be useful.

For async work, consider queue depth, oldest-item age, throughput, retry/dead-letter rate, and completion latency.

Choose only signals tied to real operational questions.

### 6. Define SLIs before SLOs

When reliability objectives matter, define an SLI that reflects user-perceived success.

Set SLOs from user/business requirements, current capability, dependency limits, and reliability cost.

Do not default every system to a conventional number of nines.

Use error budgets only when they change release or reliability decisions.

### 7. Define actionable alerts and runbooks

For each alert, specify:

- condition/window;
- severity;
- user/system impact;
- first diagnostic action;
- response/runbook;
- owner or escalation when relevant.

If nobody should act, prefer a dashboard or log.

### 8. Verify telemetry

Where practical, prove:

- expected log event appears;
- metric changes under controlled behavior;
- trace crosses intended boundaries;
- health check reflects meaningful readiness;
- alert expression can fire;
- sensitive fields are absent;
- original operator questions are answerable.

Use the canonical verification strategy.

## Output contract

Define:

- Operator Questions
- Critical Paths / Failure Modes
- Logs
- Metrics / SLIs
- Traces
- Health Checks where meaningful
- SLO / Error Budget where justified
- Alerts / Runbooks
- Telemetry Data Constraints
- Verification

Implement these in the project's actual code, provider configuration, dashboards, alert rules, and runbooks rather than a shadow observability artifact.

## Boundaries

- Do not log everything.
- Do not alert on signals with no action.
- Do not put high-cardinality identifiers into metrics.
- Do not leak secrets or unnecessary PII into telemetry.
- Do not create SLO/error-budget ceremony without a reliability decision attached.
- Do not impose a specific observability vendor or stack on a project-neutral system.
- Do not confuse observability design with active incident response.

## Completion gate

Before handoff, confirm:

- every signal answers a stated operator question;
- critical failure paths are diagnosable;
- signal type matches the question;
- correlation works where needed;
- sensitive data is excluded or minimized;
- SLOs reflect user-visible reliability when used;
- alerts are actionable;
- telemetry has fresh verification evidence.