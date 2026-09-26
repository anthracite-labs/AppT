---
name: requirements-specification
description: Use when confirmed project or feature intent needs an approved behavioral contract defining required capabilities, applicable non-functional requirements, constraints, non-goals, acceptance signals, assumptions, and blocking open questions before design or implementation.
---

# Requirements Specification

## Purpose

Define what must become true without pre-implementing how it will be built.

Requirements own the behavioral boundary, constraints, and acceptance signals. They do not own architecture, task decomposition, or code.

## Workflow

### 1. Load the accepted inputs

Use confirmed discovery intent, relevant research and feasibility findings, accepted project decisions, and existing project contracts.

If the project already has a canonical RFC or specification system, use it instead of creating a parallel format.

### 2. Surface assumptions

List material assumptions that would otherwise become accidental requirements.

Do not silently choose technology, storage, deployment, authentication, performance targets, compatibility boundaries, or user roles unless they are already accepted facts.

Resolve blocking assumptions before approval.

### 3. State objective and user outcome

Capture:

- why the work exists;
- who benefits;
- what outcome they need.

Keep only the discovery context downstream work needs.

### 4. Define capabilities and functional requirements

Describe what the system must allow, prevent, produce, or preserve.

Prefer observable behavior over implementation structure.

Use stable identifiers only when traceability materially benefits the work.

### 5. Add applicable cross-cutting requirements

Include only concerns the project actually carries, such as:

- performance;
- reliability or availability;
- security or privacy;
- accessibility;
- compliance;
- compatibility;
- observability;
- data retention;
- localization;
- external contracts.

Make them measurable where practical.

### 6. Define constraints and non-goals

A constraint must materially restrict solution space.

A non-goal explicitly bounds what this effort will not solve.

Do not fill either section with decorative language.

### 7. Define acceptance signals

Translate each material requirement into evidence that can later be demonstrated, tested, inspected, or measured.

Prefer user-visible or contract-visible outcomes over implementation steps.

### 8. Classify open questions

Mark questions as:

- **BLOCKING** — design or implementation would be materially unsafe or ambiguous without an answer;
- **DEFERRED** — can safely remain unresolved until a later lifecycle point.

Resolve blockers before approval.

### 9. Run a preservation check and approve

Compare the draft with discovery and feasibility inputs.

Ensure no load-bearing need, condition, constraint, or non-goal was silently lost.

Human approval accepts the behavioral contract, not architecture choices that have not yet been made.

## Output contract

Use the lean kernel:

- Objective / Why
- User Outcome
- Capabilities / Functional Requirements
- Applicable Non-functional / Cross-cutting Requirements
- Constraints
- Non-goals
- Acceptance Criteria / Success Signals
- Assumptions
- Open Questions

Add journeys, data requirements, external contracts, compatibility matrices, or similar detail only when they materially clarify the contract.

Store the approved contract in the artifact that owns the work: for example an existing spec/RFC system, durable project documentation, or a GitHub Issue for an executable unit.

## Boundaries

- Do not turn requirements into implementation design.
- Do not produce task lists or planning artifacts.
- Do not mandate SPEC.md, a PRD, or a spec folder.
- Do not duplicate project facts already owned by code or configuration.
- Do not add generic non-functional requirements that have no observable meaning.

## Completion gate

Before handoff, confirm:

- objective and user outcome are clear;
- material capabilities are implementation-neutral;
- applicable cross-cutting requirements are measurable where practical;
- constraints materially restrict downstream choices;
- non-goals bound the scope;
- acceptance signals prove required behavior;
- assumptions are explicit;
- blocking questions are resolved;
- load-bearing discovery and research inputs were preserved;
- the human has approved the behavioral contract.