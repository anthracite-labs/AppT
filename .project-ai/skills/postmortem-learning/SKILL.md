---
name: postmortem-learning
description: Use when a resolved incident, significant near-miss, difficult release or migration, repeated rework, surprising project outcome, or unusually successful practice contains durable learning worth preserving and turning into concrete follow-up.
---

# Postmortem and Learning

## Purpose

Turn evidence from completed work or resolved incidents into changes that improve future behavior.

A useful postmortem is blameless but specific. A useful retrospective preserves what worked as well as what failed.

## Workflow

### 1. State the event and learning purpose

Identify what is being reviewed, why durable learning is worthwhile, and which evidence sources exist.

Do not begin with who made a mistake.

### 2. Reconstruct the timeline from evidence

Prefer logs, alerts, deployments, commits, Issues/PRs, provider events, incident records, and monitoring data.

Use human recollection for context, but distinguish it from timestamped evidence.

Do not invent precision.

### 3. Quantify impact or outcome

For incidents, capture duration, affected users/traffic, degraded capabilities, data/security impact, SLA/contract impact, and business impact where known.

For project/release retrospectives, capture outcome, rework, scope/schedule variance, defects, operational burden, and disproven assumptions where relevant.

Unknown remains unknown.

### 4. Identify systemic causes

Ask why the system allowed the event or rework.

Look for:

- missing guardrails;
- ambiguous ownership;
- unsafe defaults;
- architecture flaws;
- test gaps;
- observability gaps;
- deployment gaps;
- dependency behavior;
- documentation/runbook gaps;
- process/incentive mismatches.

"Human error" is not a useful terminal cause when the system could reasonably have prevented, detected, or limited the mistake.

### 5. Separate contributing factors

Record conditions that amplified impact without confusing them with root cause.

### 6. Capture detection and response learning

Ask what detected the problem, what should have, what made diagnosis faster/slower, which mitigation helped, and where communication or ownership failed.

Route fixes to their real owners.

### 7. Preserve what worked

Capture tests, architecture, rollout controls, tools, ownership, runbooks, or validation practices worth repeating.

### 8. Create concrete follow-up selectively

Each action should address a demonstrated cause or useful amplification opportunity.

Make it specific and tracked in the project's real work system.

Do not create action items merely to fill a template.

### 9. Store durable learning in the owning artifact

Use the project's actual postmortem convention, ADR, runbook, test, alert, architecture/doc update, or Issue.

Do not create a `.project-ai` learning archive.

## Output contract

Produce:

- Summary
- Impact / Outcome
- Timeline when relevant
- Root / Systemic Causes
- Contributing Factors
- Detection / Response Learning
- What Worked
- Concrete Actions
- Durable Learnings

## Boundaries

- Do not blame individuals as root cause.
- Do not invent timestamps or impact numbers.
- Do not stop at "human error".
- Do not create vague actions such as "be more careful" or "improve monitoring".
- Do not generate dozens of low-value follow-ups.
- Do not keep executable follow-up only inside the postmortem document.
- Do not run a postmortem ceremony for trivial events with no useful learning.

## Completion gate

Before closing learning work, confirm:

- timeline and impact distinguish evidence from recollection;
- systemic causes go deeper than blame;
- contributing factors are separated;
- detection/response learning is captured;
- useful practices are preserved;
- actions are concrete and tied to evidence;
- follow-up work is tracked in the real project system;
- durable learning lives in its owning artifact.