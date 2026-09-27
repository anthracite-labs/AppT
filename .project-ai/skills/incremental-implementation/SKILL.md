---
name: incremental-implementation
description: Use when implementing an approved multi-step or multi-file change that can be delivered in thin verifiable slices, especially when a large unverified edit would hide risk or when Arena is executing an approved Issue contract.
---

# Incremental Implementation

## Purpose

Build one meaningful slice at a time, verify it, and carry the working state forward.

Implementation may choose local details inside an approved contract. It may not silently change material scope, architecture, requirements, security boundaries, dependencies, data contracts, or user-visible behavior.

## Authority

For Arena implementation work, read `../../execution/arena-dispatch.md`. It owns implementation authority, contract exceptions, branch/PR boundaries, correction cycles, and completion reporting.

## Workflow

### 1. Read the active contract

Identify objective, scope, out-of-scope areas, constraints, acceptance criteria, verification, and contract exceptions.

Load only the project context needed for the current slice.

### 2. Choose the smallest meaningful slice

Prefer a complete behavior that:

- crosses only required layers;
- can be tested or demonstrated;
- leaves the repository coherent;
- exposes important risk early.

Use a wide migration sequence only when a vertical slice cannot remain valid independently.

### 3. Implement simply and stay in scope

Choose the simplest correct implementation for the current contract.

Do not mix:

- unrelated cleanup;
- speculative abstractions;
- opportunistic modernization;
- features not requested;
- broad refactors unrelated to the slice.

Record useful out-of-scope debt separately.

### 4. Use the right feedback loop

For behavior changes, apply `../test-driven-development/SKILL.md` when test-first behavior is practical.

For bugs, reproduce before fixing.

For configuration or other non-testable changes, use the smallest check that can prove the current hypothesis.

Follow `../../execution/verification.md` rather than running terminal repository verification after every edit.

### 5. Classify implementation discoveries

**Local detail:** helper shape, naming, bounded refactor, or other implementation choice inside the contract → proceed.

**Implementation defect:** code/test is wrong while the contract remains valid → diagnose and correct on the same branch.

**Contract exception:** material requirement, architecture, security, dependency, data, interface, scope, or user-visible behavior must change → stop and report under Arena policy.

### 6. Carry forward verified slices

After each slice, keep focused checks green and remove temporary scaffolding unless it has earned a permanent role.

Do not restart the plan or reopen accepted decisions without evidence.

### 7. Produce a finished candidate

When all slices are implemented:

- run affected-scope verification;
- remove temporary diagnostics;
- update required documentation;
- confirm the diff remains in scope;
- hand the finished candidate to terminal repository verification.

## Output contract

Leave:

- implementation inside approved scope;
- focused regression protection where appropriate;
- targeted verification evidence;
- explicit contract-exception evidence when work cannot safely continue;
- one coherent finished candidate ready for terminal repository verification and review.

## Boundaries

- Do not silently rewrite the contract to fit implementation.
- Do not mix unrelated cleanup into the change.
- Do not use full CI as the ordinary inner feedback loop.
- Do not leave ordinary slices knowingly broken.
- Do not claim completion before fresh terminal verification.

## Completion gate

Before handoff, confirm:

- every approved behavior has an implemented slice;
- implementation remained inside the active contract;
- focused verification supported the inner loop;
- implementation defects were corrected without changing the contract;
- material contract discoveries were escalated;
- the candidate is coherent and ready for completion verification.