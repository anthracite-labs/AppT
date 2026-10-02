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

### 2. Compile the implementation reuse plan

For every non-trivial behavior in the unit, resolve implementation leverage before dispatch. Start from the current AppT seam and the pinned implementation-harvest evidence in the owning architecture documents.

Record one disposition per behavior:

- `EXISTING APPT` — extend or compose accepted repository code rather than recreate it;
- `ADAPT` — modify compatible-license source under its recorded obligations;
- `PORT` — translate compatible-license implementation into AppT while preserving provenance and relevant tests;
- `CLEAN-ROOM REIMPLEMENT` — independently implement only the recorded observable behavior/protocol knowledge;
- `BEHAVIORAL REFERENCE` — use the upstream implementation only for states, races, edge cases, or design patterns;
- `TEST-VECTOR/DATA` — reuse compatible tests, fixtures, or vectors under their recorded terms;
- `NEW` — write AppT-specific implementation because no suitable reusable path exists.

`NEW` is not the default. State the concrete reason reuse is unsuitable: incompatible licence, wrong platform/architecture, stale or unsafe implementation, unacceptable dependency/permission/privacy cost, materially different AppT contract, or genuinely absent upstream implementation.

When compatible implementation and tests already solve the same bounded problem, prefer `ADAPT` or `PORT` over blank-page reimplementation. Reuse upstream tests/test vectors alongside code where their licence and fit permit it.

The plan identifies source repository, pinned revision, relevant path/function or test, licence, disposition, exact material to reuse, rejected material, and AppT destination. It must remain small enough to live in the Arena Issue; the owning architecture documents remain the provenance source of truth.

If the required provenance/disposition is missing or uncertain, resolve it before Arena dispatch rather than making Arena choose a licensing or architecture policy.

### 3. Decide whether the work fits one unit

By default, a unit should have:

- one coherent objective;
- one sensible reviewable branch/PR;
- observable acceptance criteria;
- a valid end state;
- no need for the implementer to invent project-level decomposition.

Split larger work before dispatch unless the human owner has explicitly authorized contiguous program execution under `../../execution/arena-dispatch.md` §2A. In that mode, treat the named ordered slice range as one program unit only when it has one coherent lifecycle end state, its dependency graph is explicit, and every included slice remains an internal checkpoint with its own acceptance and verification obligations.

### 4. Prefer vertical slices

Prefer a narrow complete capability through the layers it actually needs over horizontal "all database / all API / all UI" phases.

Each slice should be independently demonstrable or verifiable where practical.

For inherently wide migrations, use a compatible expand → migrate → contract sequence instead of fake vertical slices.

### 5. Model real dependency edges

A blocker exists only when later work cannot safely begin or complete without earlier work.

Identify independent frontier work rather than making list order imply dependency.

### 6. Move material risk early

Schedule the cheapest proof of a dangerous assumption before investing in dependent work: integration spike, migration proof, benchmark, compatibility check, or similar evidence.

Do not build speculative infrastructure merely to "de-risk" hypotheticals.

### 7. Define each unit by outcome

For each unit, define:

- objective;
- context/authority;
- implementation reuse plan;
- scope;
- out of scope;
- constraints;
- acceptance criteria;
- verification;
- dependencies;
- contract exceptions.

Use file paths only when they add durable execution context.

### 8. Stop at executable

A capable implementer should be able to choose local code structure while remaining inside the contract.

If the plan starts prescribing function bodies or line-by-line edits that the implementation can decide safely, stop.

For Arena work, render the final unit using the exact Issue contract sections in `../../execution/arena-dispatch.md`.

## Output contract

Produce:

- the implementation units;
- the per-unit implementation reuse plan;
- genuine dependency edges;
- risk-first proof work where required;
- a bounded execution contract per unit;
- intentionally deferred follow-up work.

For Arena, one Issue maps to one branch and one PR. Under explicitly authorized contiguous program execution, that single Issue/branch/PR may contain multiple ordered slice checkpoints as defined by `../../execution/arena-dispatch.md` §2A.

## Boundaries

- Do not re-decide accepted requirements or architecture.
- Do not create mandatory plan files or a shadow task store.
- Do not default to horizontal technical-layer slicing.
- Do not infer blockers from list order.
- Do not hide unrelated cleanup inside the plan.
- Do not pre-implement the solution in prose.
- Do not let `NEW` become a synonym for "the implementer can write this from memory."

## Completion gate

Before dispatch, confirm:

- each unit has one coherent outcome;
- each Arena unit fits one branch/PR, including an explicitly authorized contiguous program unit;
- dependencies are explicit and genuine;
- vertical slicing was preferred where appropriate;
- material uncertainty appears early;
- every non-trivial behavior has an explicit reuse disposition and any `NEW` implementation has a concrete reason;
- acceptance criteria are observable;
- verification points to project truth;
- contract exceptions return material decisions to the control plane;
- the plan stops at executable.