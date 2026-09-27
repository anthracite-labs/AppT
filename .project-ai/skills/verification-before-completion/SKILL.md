---
name: verification-before-completion
description: Use when work is about to be called complete, fixed, passing, ready for review, or technically verified, or when a PR's acceptance criteria need fresh evidence tied to the exact candidate being reviewed.
---

# Verification Before Completion

## Purpose

Require fresh evidence before any completion claim.

Verification proves technical behavior for a concrete candidate. It does not imply contract compliance, human acceptance, or merge.

## Authority

Read and follow `../../execution/verification.md`. It owns the universal narrow-to-broad verification strategy and terminal repository verification behavior.

For Arena work, `../../execution/arena-dispatch.md` owns the distinction:

`VERIFIED ≠ CONTRACT-COMPLIANT ≠ ACCEPTED ≠ MERGED`.

## Workflow

### 1. Identify the candidate

Record the branch/commit/diff or other exact state being verified, plus the active contract and environment where runtime behavior matters.

Any required source or generated-state change after terminal repository verification invalidates that terminal verification evidence.

### 2. Map requirements to proof

For each material acceptance criterion, identify:

- implementation surface;
- static traceability;
- runtime/observable proof when static inspection is insufficient.

A green suite alone does not prove every requirement.

### 3. Finish narrow verification first

If a focused failure exists, leave terminal repository verification and return to the smallest useful reproducer.

Regain targeted green before producing another finished candidate.

### 4. Run terminal repository verification

Only on the finished candidate, run the complete project-defined repository verification required by `../../execution/verification.md`.

Read exit status, failures, material skips/unavailable checks, and whether verification mutated candidate state.

### 5. Re-check the acceptance boundary

Walk every material criterion against fresh evidence for the current candidate.

Add deeper integration, browser, migration, security, performance, generated-state, or compatibility proof only when risk or the contract requires it.

### 6. Report the actual result

Use:

- **VERIFIED** — required technical evidence and acceptance-criteria evidence is fresh and sufficient;
- **NOT VERIFIED** — a required check failed, is unavailable/stale, or does not prove the acceptance boundary.

State what was run, candidate identity, result, unresolved checks, and limitations.

## Output contract

Produce:

- Candidate identity
- Acceptance criterion → evidence map
- Focused verification used during correction
- Terminal repository verification result
- Additional risk-driven proof when required
- VERIFIED or NOT VERIFIED
- Explicit limitations/unavailable checks

## Boundaries

- Do not claim success from old evidence.
- Do not infer a full result from a partial suite.
- Do not equate lint with build or tests with full requirements compliance.
- Do not use terminal repository verification as the inner debugging loop.
- Do not use VERIFIED as a synonym for CONTRACT-COMPLIANT, ACCEPTED, or MERGED.
- Do not hide unavailable verification.

## Completion gate

Before stating VERIFIED, confirm:

- candidate identity is clear;
- focused evidence is current;
- terminal repository verification ran on the finished candidate;
- every material acceptance criterion maps to evidence;
- runtime proof exists where static traceability is insufficient;
- unavailable checks are disclosed;
- candidate state did not change after terminal repository verification;
- no claim exceeds the evidence.