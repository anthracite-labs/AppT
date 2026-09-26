---
name: documentation-adrs
description: Use when accepted project behavior, setup, public interfaces, operational procedures, concepts, migration guidance, or durable technical decision rationale needs documentation that future users or engineers cannot recover cheaply from code and configuration alone.
---

# Documentation and ADRs

## Purpose

Document information that reduces future uncertainty.

Explain how to use the system, what public contracts mean, how important concepts work, and why durable decisions were made. Do not duplicate facts already owned by machine-readable project artifacts.

## Decision rules

| Reader need | Documentation type |
|---|---|
| learn from a guided starting point | Tutorial |
| accomplish a concrete task | How-to |
| look up exact facts | Reference |
| understand concepts or rationale | Explanation |
| preserve an expensive/reversible technical choice | ADR or project decision record |

Do not write all types by default.

## Workflow

### 1. Identify reader and job

Establish who is reading, what they need to do or understand, and which project artifacts are authoritative.

### 2. Reuse existing conventions

Inspect README/CONTRIBUTING, docs sites, API docs, ADRs, naming, numbering, and tooling.

Do not start a second documentation or ADR convention.

### 3. Write only what earned permanence

Good durable content includes:

- non-obvious workflows;
- setup details that cannot be inferred safely;
- public interface behavior;
- operational procedures;
- compatibility constraints;
- important gotchas;
- migration guidance;
- rationale likely to be re-litigated.

Avoid prose that merely restates code or configuration.

### 4. Keep public documentation aligned

When accepted behavior changes, update the affected examples, references, setup instructions, errors, compatibility notes, or migration guidance consumers rely on.

Documentation should describe accepted reality, not anticipated features.

### 5. Preserve ADRs selectively

Write or update a durable decision record when:

- reversal is expensive;
- several plausible alternatives existed;
- future engineers are likely to question the choice;
- rationale cannot be recovered cheaply;
- forgetting the constraint could cause harmful reversal.

Follow the project's existing ADR format.

Preserve context, decision, material alternatives, consequences, status, and supersession relationships.

### 6. Comment the why locally

Use inline comments for non-obvious local intent.

Do not comment self-explanatory code or preserve commented-out code as history.

### 7. Verify documentation

Where practical:

- run examples;
- verify commands;
- check links and paths;
- compare references with actual interfaces/configuration;
- validate migration instructions.

## Output contract

Create or update only the documentation the reader needs.

Every output should identify its canonical source and fit the project's established documentation/ADR convention.

For durable decisions, preserve:

- Context
- Decision
- Material Alternatives
- Consequences
- Status

## Boundaries

- Do not document speculative or unaccepted behavior.
- Do not manually duplicate versions, commands, schemas, or settings already owned elsewhere.
- Do not create ADRs for trivial implementation choices.
- Do not invent a parallel decision archive in `.project-ai` for product architecture.
- Do not use one giant document when separate reader jobs need different shapes.
- Do not delete historical decision context merely because a choice changed.

## Completion gate

Before handoff, confirm:

- reader and purpose are explicit;
- documentation type matches the job;
- existing project conventions were reused;
- content derives from accepted reality;
- machine-owned facts are not needlessly duplicated;
- ADRs exist only where rationale is materially valuable;
- affected public behavior is documented where required;
- examples and commands are verified where practical.