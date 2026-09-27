# Capability Routing

Capability answers **what kind of work is required**.

It is separate from:

- **Skill** — the method used to perform the work.
- **Route** — where or through what execution mechanism a required operation runs.
- **Authority** — who may decide or act.
- **Verification** — the evidence that proves the result.

Use only these initial capabilities:

## understand

Use when the primary need is to recover facts, inspect the project, investigate relevant context, or establish what currently exists.

## decide

Use when alternatives, trade-offs, constraints, or accepted direction must be resolved.

## plan

Use when an accepted direction must be converted into an executable work boundary, including an Arena Issue contract.

Plan only deeply enough to make execution safe and unambiguous. Do not pre-implement the solution in prose.

## implement

Use when approved changes must be made.

For project implementation, the control plane does not normally implement directly. Approved implementation is dispatched to Arena through the GitHub Issue contract defined in `../execution/arena-dispatch.md`.

Control-plane maintenance remains owned by the control plane.

## review

Use when examining an implementation, change, evidence set, security concern, or contract compliance.

## diagnose

Use when a bug, failing test, build/CI failure, regression, unexpected result, integration failure, performance anomaly, or blocked operation requires root-cause investigation.

Diagnosis is a mandatory phase before every correction. Load `../skills/debugging-recovery/SKILL.md`. Establish the tightest reliable reproducer when practical, an evidence-backed root cause, the minimal correction, regression protection where practical, and targeted green verification before returning to broader or terminal verification.

For failures on an existing pull request, also use `../skills/pr-integration-correction/SKILL.md` so the correction stays on the same branch/PR and actionable CI/review items are classified before editing.

If the failure cannot be reproduced locally, record the exact capability/tooling blocker and escalate only the blocked operation through the narrowest safe alternate route. If no safe route can provide equivalent targeted evidence, stop the correction and return control with the blocker; do not edit, produce a finished candidate, or run terminal verification. Do not substitute repeated terminal CI runs for diagnosis.

## Mandatory lifecycle skill routing

When a skill's trigger matches the work, load and follow that skill. This is mandatory routing, not an optional reference. More than one skill may apply to the same task; use only the skills whose triggers materially apply.

| Trigger | Required skill |
|---|---|
| project/control-plane bootstrap, adoption, or foundation reconciliation | `../skills/project-bootstrap/SKILL.md` |
| materially unclear problem, user, outcome, success signal, constraint, or need | `../skills/project-discovery/SKILL.md` |
| decision depends on uncertain external facts, feasibility, alternatives, cost, or risky assumptions | `../skills/research-feasibility/SKILL.md` |
| confirmed intent needs an approved behavioral contract | `../skills/requirements-specification/SKILL.md` |
| durable architecture, ownership, dependency direction, state/data rule, or interface contract is required | `../skills/architecture-interface-design/SKILL.md` |
| approved requirements/architecture need executable implementation units | `../skills/implementation-planning/SKILL.md` |
| approved multi-step/multi-file implementation is being executed | `../skills/incremental-implementation/SKILL.md` |
| behavior or a bug can be specified with an executable test before implementation | `../skills/test-driven-development/SKILL.md` |
| bug, failing test/build/CI, regression, unexpected behavior, integration failure, performance anomaly, or blocked operation | `../skills/debugging-recovery/SKILL.md` |
| an existing PR has actionable CI/review/correction work | `../skills/pr-integration-correction/SKILL.md` |
| work is about to be called complete, fixed, passing, ready, or technically verified | `../skills/verification-before-completion/SKILL.md` |
| a proposed implementation/PR needs contract-compliance and engineering-quality review | `../skills/code-review/SKILL.md` |
| authentication, authorization, secrets, cryptography, untrusted input, network trust, privileged operations/CI, sensitive data, supply chain, or explicit security review is involved | `../skills/security-engineering/SKILL.md` |
| CI/CD, automated build/test/quality/security/package/deploy behavior is created or changed | `../skills/ci-cd-automation/SKILL.md` |
| accepted behavior/setup/interfaces/operations or durable technical rationale needs documentation | `../skills/documentation-adrs/SKILL.md` |
| software/dependency/API/schema/service/feature/infrastructure/data is upgraded, migrated, deprecated, removed, or retired | `../skills/maintenance-migration-retirement/SKILL.md` |
| production behavior needs logs, metrics, traces, health signals, alerts, SLIs/SLOs, or diagnostic evidence | `../skills/observability-operations-design/SKILL.md` |
| active or credible production harm requires stabilization before ordinary debugging | `../skills/incident-response/SKILL.md` |
| resolved incident, near-miss, repeated rework, difficult release/migration, or surprising outcome contains durable learning | `../skills/postmortem-learning/SKILL.md` |
| an accepted candidate is moving to an environment, registry, or user population | `../skills/release-deployment/SKILL.md` |

The skill trigger selects the method; it does not change capability, route, authority, or acceptance boundaries.

## Skills and cross-cutting concerns

Lifecycle skills are methods, not additional top-level capabilities.

Testing, security, documentation, research, UI work, deployment, migrations, performance, and similar concerns operate within one or more of the six capabilities above.

Create a new capability only when separating it materially changes routing, procedure, authority, permissions, or acceptance.

A missing tool, runtime, package manager, network route, or hosted runner does not change the capability. Those are execution-routing concerns.