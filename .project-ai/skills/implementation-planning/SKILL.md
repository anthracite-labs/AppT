---
name: implementation-planning
description: Use when approved requirements and architecture need to become one or more executable implementation units, especially when work is too large for one reviewable change, has real dependencies, or needs risk-first sequencing before Arena dispatch.
---

# Implementation Planning

## Purpose

Plan until the work is executable, then stop.

Decide the outcome, scope, constraints, acceptance, real dependencies, sequencing, and verification an implementer cannot safely infer. Preserve local implementation judgment.

## Authority

For Arena-dispatched implementation work, read `../../execution/arena-dispatch.md`. It owns Issue structure, dispatch eligibility, branch/PR lifecycle, contract exceptions, review, acceptance, and merge boundaries.

## Workflow

### 1. Load the accepted contract

Use authoritative requirements, architecture, security constraints, non-goals, project artifacts, and verification strategy.

Do not plan from chat summaries when repository artifacts own the decision.

### 2. Decide whether the work fits one unit

A unit should have:

- one coherent objective;
- one sensible reviewable branch/PR;
- observable acceptance criteria;
- a valid end state;
- no need for the implementer to invent project-level decomposition.

Split larger work before dispatch.

### 3. Prefer vertical slices

Prefer a narrow complete capability through the layers it actually needs over horizontal "all database / all API / all UI" phases.

Each slice should be independently demonstrable or verifiable where practical.

For inherently wide migrations, use a compatible expand → migrate → contract sequence instead of fake vertical slices.

### 4. Model real dependency edges

A blocker exists only when later work cannot safely begin or complete without earlier work.

Identify independent frontier work rather than making list order imply dependency.

### 5. Move material risk early

Schedule the cheapest proof of a dangerous assumption before investing in dependent work: integration spike, migration proof, benchmark, compatibility check, or similar evidence.

Do not build speculative infrastructure merely to "de-risk" hypotheticals.

### 6. Define each unit by outcome

For each unit, define:

- objective;
- context/authority;
- scope;
- out of scope;
- constraints;
- acceptance criteria;
- verification;
- dependencies;
- contract exceptions.

Use file paths only when they add durable execution context.

### 7. Stop at executable

A capable implementer should be able to choose local code structure while remaining inside the contract.

If the plan starts prescribing function bodies or line-by-line edits that the implementation can decide safely, stop.

For Arena work, render the final unit using the exact Issue contract sections in `../../execution/arena-dispatch.md`.

## Output contract

Produce:

- the implementation units;
- genuine dependency edges;
- risk-first proof work where required;
- a bounded execution contract per unit;
- intentionally deferred follow-up work.

For Arena, one Issue maps to one branch and one PR.

## Boundaries

- Do not re-decide accepted requirements or architecture.
- Do not create mandatory plan files or a shadow task store.
- Do not default to horizontal technical-layer slicing.
- Do not infer blockers from list order.
- Do not hide unrelated cleanup inside the plan.
- Do not pre-implement the solution in prose.

## Completion gate

Before dispatch, confirm:

- each unit has one coherent outcome;
- each Arena unit fits one branch/PR;
- dependencies are explicit and genuine;
- vertical slicing was preferred where appropriate;
- material uncertainty appears early;
- acceptance criteria are observable;
- verification points to project truth;
- contract exceptions return material decisions to the control plane;
- the plan stops at executable.