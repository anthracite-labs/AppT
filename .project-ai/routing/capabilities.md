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

Diagnosis is a mandatory phase before editing when a practical reproducer exists. Load `../skills/debugging-recovery/SKILL.md` and establish the tightest reliable reproducer, evidence-backed root cause, minimal correction, regression protection where practical, and targeted green verification before returning to broader or terminal verification.

For failures on an existing pull request, also use `../skills/pr-integration-correction/SKILL.md` so the correction stays on the same branch/PR and actionable CI/review items are classified before editing.

If the failure cannot be reproduced locally or through another narrow route, record the exact capability/tooling blocker and escalate only the blocked operation. Do not substitute repeated terminal CI runs for diagnosis.

## Skills and cross-cutting concerns

Lifecycle skills are methods, not additional top-level capabilities.

Testing, security, documentation, research, UI work, deployment, migrations, performance, and similar concerns operate within one or more of the six capabilities above.

Create a new capability only when separating it materially changes routing, procedure, authority, permissions, or acceptance.

A missing tool, runtime, package manager, network route, or hosted runner does not change the capability. Those are execution-routing concerns.