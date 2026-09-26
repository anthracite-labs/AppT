---
name: architecture-interface-design
description: Use when approved requirements need durable system boundaries, ownership, dependency direction, state or data rules, or interface contracts so independently built parts cannot make materially incompatible choices.
---

# Architecture and Interface Design

## Purpose

Record only the structural decisions that independent implementation must share.

Architecture is a consistency contract, not a complete description of the codebase. Interface design defines how meaningful parts communicate across the seams architecture creates.

## Workflow

### 1. Establish scope and recover reality

State whether the work governs the whole project, a subsystem, feature slice, integration, library, or migration boundary.

For brownfield work, inspect actual code, configuration, existing contracts, and accepted architecture before proposing change.

Separate:

- durable accepted invariant;
- current implementation state;
- accidental structure;
- unknown.

### 2. Apply the architecture inclusion test

Centralize a decision only when:

- independent implementers could choose differently;
- the difference would materially harm consistency;
- the answer is not already obvious from canonical project artifacts;
- the choice is a real invariant or trade-off.

Otherwise defer it to implementation.

### 3. Define responsibilities and ownership

Make material ownership explicit:

- responsibility;
- authoritative state or data;
- who may mutate it;
- where derived state may exist;
- how consistency is maintained when relevant.

### 4. Define dependency direction

Record only the dependency rules needed to prevent structural drift.

Use the vocabulary native to the project rather than forcing a framework.

### 5. Choose meaningful seams

Create a seam when it provides real leverage, locality, testability, or replaceability.

Prefer deep modules: substantial behavior behind a small understandable interface.

Avoid speculative abstraction. A useful test is whether removing the abstraction would spread meaningful complexity back across callers.

### 6. Define interface contracts

For each material interface, define only what consumers need:

- inputs and outputs;
- invariants;
- errors and failure semantics;
- ordering when contractually relevant;
- trust validation;
- retry/idempotency behavior where side effects make it necessary;
- compatibility/versioning expectations for existing consumers.

Apply this to module/library interfaces, APIs, events/messages, queues, CLIs, files/schemas, and external integrations as appropriate.

### 7. Design compatibility intentionally

Treat observable behavior as a potential dependency.

Before changing an existing shared/public contract, identify consumers and migration implications.

Prefer additive evolution when it reduces real compatibility risk.

Do not invent versioning machinery where no compatibility requirement exists.

### 8. Defer local implementation detail

Leave private helpers, local algorithms, internal naming, and other non-shared choices to implementation.

Preserve rationale durably only when losing it would likely cause unsafe reversal or repeated re-litigation; use the project's actual ADR/decision mechanism.

## Output contract

Produce the minimum architecture contract needed for consistent implementation:

- Architecture Scope
- Durable Invariants
- Responsibilities / Ownership
- Dependency Direction
- State / Data Ownership
- Material Interfaces / Contracts
- Operational Constraints that shape architecture
- Deferred Decisions
- Open Questions
- Rationale that warrants durable preservation

Persist it in the project's actual architecture/decision artifacts. Diagrams are optional.

## Boundaries

- Do not document every directory or class as architecture.
- Do not invent greenfield conventions for a brownfield system without inspecting reality.
- Do not centralize choices implementation can safely make locally.
- Do not create speculative abstractions without real leverage or variation.
- Do not omit failure semantics from material contracts.
- Do not treat external inputs or third-party responses as trusted by default.
- Do not pre-write the implementation plan.

## Completion gate

Before handoff, confirm:

- scope is explicit;
- every durable rule passes the architecture inclusion test;
- material ownership and dependency direction are clear;
- state/data mutation ownership is explicit where needed;
- material interfaces define behavior and failure semantics;
- trust and compatibility concerns are intentional;
- seams earn their complexity;
- non-shared implementation detail remains deferred.