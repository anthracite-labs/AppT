---
name: code-review
description: Use when reviewing a proposed code change or pull request for both accepted-contract compliance and engineering quality before human acceptance, especially when correctness, tests, architecture, security, performance, compatibility, or operational side effects may be material.
---

# Code Review

## Purpose

Review two independent questions:

1. Did the change implement the accepted thing?
2. Is the implementation technically sound?

A clean implementation can miss the contract. A contract-compliant implementation can still contain engineering defects.

## Authority

For Arena implementation PRs, read `../../execution/arena-dispatch.md`. It owns contract-review outcomes and the human acceptance/merge boundary.

## Workflow

### 1. Load context and evidence

Read:

- active Issue/requirement contract;
- accepted architecture constraints;
- candidate diff;
- tests;
- verification evidence;
- project conventions relevant to changed areas.

Do not invent requirements absent from the contract.

### 2. Review contract compliance

Check:

- every acceptance criterion has implementation and evidence;
- required behavior exists;
- scope/out-of-scope boundaries are respected;
- constraints and non-goals are preserved;
- implementation did not silently change user-visible behavior;
- verification actually proves the criteria.

Classify:

- valid contract + implementation miss → **CORRECTION REQUIRED**;
- material contract flaw/new project decision → **CONTRACT EXCEPTION**;
- satisfied contract with sufficient evidence → **CONTRACT-COMPLIANT**.

### 3. Review engineering quality

Inspect the changed behavior on the axes that matter:

| Axis | Questions |
|---|---|
| Correctness | wrong logic, edge cases, races, state consistency, errors, partial failure? |
| Tests | behavior-focused, regression-sensitive, useful failure signal, real boundaries? |
| Simplicity | unnecessary concepts, pass-through layers, tangled control flow, weak invariants? |
| Architecture | ownership, dependency direction, seams, canonical helpers, leakage across boundaries? |
| Security | does a security trigger require the security-engineering skill? |
| Performance | unbounded/multiplicative work, hot-path waste, missing bounds, retry pathologies? |
| Compatibility | public contracts, schemas, events, config, migration, timing/ordering changes? |
| Operations | diagnosability, retries, rollout, migration/recovery implications? |

Investigate before asserting.

### 4. Write evidence-backed findings

For each material finding, state:

- location/surface;
- concrete behavior;
- evidence;
- impact;
- required correction or recommended structural remedy.

Separate blockers from improvements and optional nits.

Do not elevate style preference into a blocker without a project standard.

### 5. Conclude without crossing authority

For Arena work, return exactly one contract outcome plus engineering findings and verification gaps.

Never convert review into human acceptance or merge authorization.

## Output contract

Produce:

- Contract Review outcome
- Engineering Findings
- Verification Gaps
- Review Notes for human inspection
- Explicit acceptance boundary

Each material finding should be actionable and evidence-backed.

## Boundaries

- Do not review code quality without understanding the accepted contract.
- Do not invent features or requirements.
- Do not reimplement the PR during review.
- Do not force broad refactors into a bounded correction.
- Do not report speculative security or performance concerns as facts.
- Do not let nits obscure material defects.
- Do not treat VERIFIED as CONTRACT-COMPLIANT or CONTRACT-COMPLIANT as ACCEPTED.

## Completion gate

Before finishing review, confirm:

- contract and engineering axes were reviewed separately;
- contract findings cite the active contract;
- engineering findings explain concrete impact;
- tests and verification evidence were inspected;
- security, performance, compatibility, and operational effects were considered where relevant;
- verification gaps are explicit;
- the result does not imply human acceptance.