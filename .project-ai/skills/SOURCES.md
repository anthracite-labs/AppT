# Lifecycle Skill Sources

This file records the upstream material used to design the Anthracite lifecycle skills.

It is maintenance/provenance material and is intentionally outside runtime `SKILL.md` files.

Paths were re-verified against the current upstream repositories on 2026-09-26.

## Adaptation policy

- Harvest mechanisms and decision logic; do not vendor upstream workflow architectures blindly.
- Preserve Anthracite canonical owners for bootstrap, verification, Arena dispatch, repository truth, and human acceptance.
- Remove upstream-specific workspaces, memlogs, shadow state, mandatory task/spec directories, provider assumptions, and orchestration unless the mechanism is independently justified here.
- Runtime skills should contain only what helps an agent decide, do, or verify the task.
- Upstream source structure is not copied merely because it exists.
- Where licenses differ, this repository uses independent Anthracite rewrites rather than substantial copied passages.

## Skill-authoring guidance studied

| Source | Path | Role |
|---|---|---|
| obra/superpowers | `skills/writing-skills/SKILL.md` | trigger-only descriptions, concise runtime skills, skill TDD, duplication control |
| getsentry/skills | `skills/skill-writer/SKILL.md` and focused references | precision pass, source adaptation, runtime/maintenance separation |
| anthropics/skills | `skills/skill-creator/SKILL.md` | progressive disclosure, runtime size discipline, iterative evaluation |

Superpowers is MIT-licensed. Sentry Skills is Apache-2.0. The Anthropic source was used as authoring guidance only; no repository-level license claim is made here.

## Lifecycle source map

### 01 — project-discovery

- Base: addyosmani/agent-skills — `skills/interview-me/SKILL.md` — MIT
- Harvest: bmad-code-org/BMAD-METHOD — `skills/bmad-product-brief/SKILL.md` — MIT
- Harvest: tomzx/agents — `skills/create-needs-assessment/SKILL.md` — MIT

### 02 — research-feasibility

- Base: bmad-code-org/BMAD-METHOD — `skills/bmad-deep-recon/SKILL.md` — MIT
- Harvest: mattpocock/skills — `skills/engineering/research/SKILL.md` — MIT
- Harvest: tomzx/agents — `skills/create-feasibility/SKILL.md` — MIT

### 03 — requirements-specification

- Base: addyosmani/agent-skills — `skills/spec-driven-development/SKILL.md` — MIT
- Harvest: bmad-code-org/BMAD-METHOD — `skills/bmad-prd/SKILL.md` — MIT
- Harvest: bmad-code-org/BMAD-METHOD — `skills/bmad-spec/SKILL.md` — MIT

### 04 — architecture-interface-design

- Base: bmad-code-org/BMAD-METHOD — `skills/bmad-architecture/SKILL.md` — MIT
- Harvest: addyosmani/agent-skills — `skills/api-and-interface-design/SKILL.md` — MIT
- Harvest: mattpocock/skills — `skills/engineering/codebase-design/SKILL.md` — MIT

### 05 — security-engineering

- Base: addyosmani/agent-skills — `skills/security-and-hardening/SKILL.md` — MIT
- Harvest: getsentry/skills — `skills/security-review/SKILL.md` — Apache-2.0
- Harvest: cloudflare/security-audit-skill — `skills/security-audit/SKILL.md` — MIT

### 06 — project-bootstrap

- Base: tomzx/agents — `skills/create-project/SKILL.md` — MIT
- Harvest: github/awesome-copilot — `skills/repo-standardizer/SKILL.md` — MIT
- Harvest: addyosmani/agent-skills — `skills/constraint-driven-development/SKILL.md` — MIT

### 07 — ci-cd-automation

- Base: addyosmani/agent-skills — `skills/ci-cd-and-automation/SKILL.md` — MIT
- Harvest: wshobson/agents — `plugins/cicd-automation/skills/deployment-pipeline-design/SKILL.md` — MIT
- Harvest: github/awesome-copilot — `skills/github-actions-efficiency/SKILL.md` — MIT

### 08 — implementation-planning

- Base: addyosmani/agent-skills — `skills/planning-and-task-breakdown/SKILL.md` — MIT
- Harvest: obra/superpowers — `skills/writing-plans/SKILL.md` — MIT
- Harvest: mattpocock/skills — `skills/engineering/to-tickets/SKILL.md` — MIT

### 09 — incremental-implementation

- Base: addyosmani/agent-skills — `skills/incremental-implementation/SKILL.md` — MIT
- Harvest: obra/superpowers — `skills/executing-plans/SKILL.md` — MIT
- Harvest: bmad-code-org/BMAD-METHOD — `skills/bmad-build/SKILL.md` — MIT

### 10 — test-driven-development

- Base: addyosmani/agent-skills — `skills/test-driven-development/SKILL.md` — MIT
- Harvest: mattpocock/skills — `skills/engineering/tdd/SKILL.md` — MIT
- Harvest: obra/superpowers — `skills/test-driven-development/SKILL.md` — MIT

### 11 — debugging-recovery

- Base: mattpocock/skills — `skills/engineering/diagnosing-bugs/SKILL.md` — MIT
- Harvest: obra/superpowers — `skills/systematic-debugging/SKILL.md` — MIT
- Harvest: addyosmani/agent-skills — `skills/debugging-and-error-recovery/SKILL.md` — MIT

### 12 — observability-operations-design

- Base: addyosmani/agent-skills — `skills/observability-and-instrumentation/SKILL.md` — MIT
- Harvest: tomzx/agents — `skills/create-observability/SKILL.md` — MIT
- Harvest: wshobson/agents — `plugins/observability-monitoring/skills/slo-implementation/SKILL.md` — MIT

### 13 — documentation-adrs

- Base: addyosmani/agent-skills — `skills/documentation-and-adrs/SKILL.md` — MIT
- Harvest: tomzx/agents — `skills/create-documentation/SKILL.md` — MIT
- Harvest: github/awesome-copilot — `skills/create-architectural-decision-record/SKILL.md` — MIT

### 14 — verification-before-completion

- Base: obra/superpowers — `skills/verification-before-completion/SKILL.md` — MIT
- Harvest: tomzx/agents — `skills/verify-pr/SKILL.md` — MIT
- Harvest: github/awesome-copilot — `skills/quality-playbook/SKILL.md` — MIT

### 15 — code-review

- Base: mattpocock/skills — `skills/engineering/code-review/SKILL.md` — MIT
- Harvest: getsentry/skills — `skills/code-review/SKILL.md` — Apache-2.0
- Harvest: addyosmani/agent-skills — `skills/code-review-and-quality/SKILL.md` — MIT

### 16 — pr-integration-correction

- Base: getsentry/skills — `skills/iterate-pr/SKILL.md` — Apache-2.0
- Harvest: tomzx/agents — `skills/handle-pr-ci/SKILL.md` — MIT
- Harvest: obra/superpowers — `skills/receiving-code-review/SKILL.md` — MIT

### 17 — release-deployment

- Base: addyosmani/agent-skills — `skills/shipping-and-launch/SKILL.md` — MIT
- Harvest: tomzx/agents — `skills/deploy-pr/SKILL.md` — MIT
- Harvest: github/awesome-copilot — `skills/devops-rollout-plan/SKILL.md` — MIT

### 18 — incident-response

- Base: wshobson/agents — `plugins/incident-response/skills/incident-runbook-templates/SKILL.md` — MIT
- Harvest: tomzx/agents — `skills/observe-production/SKILL.md` — MIT
- Harvest: sickn33/agentic-awesome-skills — `skills/incident-response/SKILL.md` — MIT; community-sourced security-IR material

### 19 — postmortem-learning

- Base: wshobson/agents — `plugins/incident-response/skills/postmortem-writing/SKILL.md` — MIT
- Harvest: github/awesome-copilot — `skills/incident-postmortem/SKILL.md` — MIT
- Harvest: tomzx/agents — `skills/create-learnings/SKILL.md` — MIT

### 20 — maintenance-migration-retirement

- Base: addyosmani/agent-skills — `skills/deprecation-and-migration/SKILL.md` — MIT
- Harvest: wshobson/agents — `plugins/framework-migration/skills/dependency-upgrade/SKILL.md` — MIT
- Harvest: wshobson/agents — `plugins/framework-migration/skills/database-migration/SKILL.md` — MIT