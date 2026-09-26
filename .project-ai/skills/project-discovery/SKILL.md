---
name: project-discovery
description: Use when a project, feature, or materially underspecified request lacks a clear problem, affected user, desired outcome, success signal, constraint, non-goal, or evidence that new software is needed.
---

# Project Discovery

## Purpose

Turn a raw idea or proposed solution into confirmed intent that downstream work can trust.

Discovery establishes the need and outcome. It does not research external facts, write requirements, choose architecture, plan implementation, or build the solution.

## Workflow

### 1. Read existing authority first

Use canonical project material before asking the user to repeat known facts.

For greenfield work, unresolved choices are unknowns, not inferred project facts.

### 2. Form a working hypothesis

State the current best interpretation and identify only the missing information that could materially change direction.

Do not silently fill load-bearing gaps.

### 3. Clarify interactively

Ask one focused question at a time when later questions depend on the answer.

Where useful, include a concise best guess so the user can correct a concrete interpretation.

Translate vague signals such as "modern", "scalable", or "best practice" into observable outcomes or constraints.

### 4. Establish intent

Confirm:

- problem;
- affected user or stakeholder;
- desired outcome;
- why now;
- observable success;
- binding constraints;
- non-goals.

Keep the problem separate from the proposed solution.

### 5. Challenge the need

Establish the evidence that the problem is real.

Check:

- current workaround or status quo;
- cost of doing nothing;
- existing project capability;
- configuration or process changes;
- internal or external alternatives;
- whether a smaller change would satisfy the need.

Permit the conclusion that new software should not be built.

### 6. Surface assumptions and unknowns

Separate:

- accepted facts;
- assumptions;
- open questions;
- external facts that require research;
- project facts that should be recovered from canonical artifacts.

### 7. Confirm the decision

Restate the discovered intent and ask for correction only where material ambiguity remains.

Use one outcome:

- **PROCEED** — intent and need are clear enough for the next lifecycle step;
- **VALIDATE FURTHER** — evidence or intent is still too weak;
- **DO NOT BUILD** — current evidence does not justify new implementation.

## Output contract

Produce a compact discovery result containing:

- Problem
- User / Stakeholders
- Desired Outcome
- Why Now
- Success
- Constraints
- Non-goals
- Evidence of Need
- Existing Alternatives / Workarounds
- Assumptions
- Open Questions
- Decision

Persist it only when durable continuity is needed, using the artifact that owns the work.

## Boundaries

- Do not turn discovery into product research or feasibility analysis.
- Do not convert intent into an implementation spec or task list.
- Do not create a mandatory discovery document or shadow project profile.
- Do not keep questioning once remaining uncertainty is non-load-bearing.
- Do not treat the requested feature itself as proof of need.

## Completion gate

Before handoff, confirm:

- the problem is stated independently of the proposed solution;
- affected users or stakeholders are known;
- desired outcome and success are observable enough for downstream work;
- constraints and non-goals are explicit;
- evidence of need exists or its absence is acknowledged;
- viable non-build alternatives were considered;
- assumptions and open questions are visible;
- the proceed / validate / do-not-build decision is explicit.