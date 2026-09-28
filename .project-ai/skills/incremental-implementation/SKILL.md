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

### 2. Establish implementation evidence and reuse disposition before coding

Do not treat model memory, familiarity with a framework, or an assumed API shape as implementation authority.

Before writing production code for a non-trivial behavior:

1. read the active AppT contract and the owning architecture/documentation for that behavior;
2. inspect the current AppT code at the seam being changed so local conventions and already-accepted behavior come from repository truth;
3. for material external API, platform, provider, protocol, security, storage, lifecycle, or toolchain behavior, re-open the relevant first-party authoritative source linked by the owning document, or the current first-party owner when the recorded link is stale;
4. inspect the relevant pinned implementation-evidence / harvest entries in the owning architecture documents for concrete algorithms, message shapes, races, edge cases, lifecycle patterns, or test strategies, respecting the recorded provenance, licence, and harvest method;
5. use AppT fixtures, tests, and physical/provider evidence to close the gap between external claims and AppT's accepted behavior;
6. read the Issue's Implementation Reuse Plan and identify the exact disposition for the behavior before creating production implementation.

The source hierarchy is deliberate:

- the accepted AppT contract and current repository own project truth;
- first-party authoritative sources own external contracts;
- pinned harvested OSS and physical evidence inform implementation technique and de-facto behavior without becoming provider guarantees;
- model memory may suggest what to inspect but is not evidence.

There is intentionally no separate harvest register. When a contract points to harvest material, recover it from the relevant owning architecture documents. The Issue's Implementation Reuse Plan is the executable subset for this slice, not a new provenance owner.

### Reuse before reimplement

Do not start from a blank page merely because the behavior is familiar.

Execute the recorded disposition:

- `EXISTING APPT`: extend, compose, or extract accepted AppT code first;
- `ADAPT`: begin from the pinned compatible source and make the smallest changes needed for AppT's contract and architecture;
- `PORT`: translate the pinned implementation deliberately, preserving algorithm/state behavior, provenance, and relevant upstream tests;
- `TEST-VECTOR/DATA`: import or translate the permitted vector/test material and use it to constrain the implementation;
- `CLEAN-ROOM REIMPLEMENT`: use only the recorded observable contract/evidence and produce an independent implementation;
- `BEHAVIORAL REFERENCE`: learn from the recorded states, races, and patterns without translating substantial source structure;
- `NEW`: write new AppT code only for the reason already recorded in the Issue.

When more than one legally and architecturally valid route exists, prefer existing AppT code, then compatible `ADAPT` or `PORT` leverage, before new implementation. Port or adapt upstream regression tests with the implementation when their licence and fit permit it.

Arena may narrow an approved reuse target to a smaller reusable unit, but it may not silently cross the recorded licence/reuse boundary or replace an approved reuse path with unrelated blank-page code. If the recorded disposition is missing, stale, unsafe, or materially wrong, stop and return the smallest contract exception needed to correct it.

An already-dispatched Issue whose active revision predates the reuse-plan requirement is grandfathered until its next material contract revision. Do not invent a new reuse policy mid-implementation; continue to respect the owning architecture document's recorded harvest methods.

Research only the implementation facts needed for the active behavior. Do not turn implementation into open-ended browsing. If authoritative or implementation evidence is missing, stale, or contradictory in a way that could materially change the accepted contract, stop and return a contract exception instead of inventing the missing behavior.

### 3. Choose the smallest meaningful slice

Prefer a complete behavior that:

- crosses only required layers;
- can be tested or demonstrated;
- leaves the repository coherent;
- exposes important risk early.

Use a wide migration sequence only when a vertical slice cannot remain valid independently.

### 4. Implement simply and stay in scope

Choose the simplest correct implementation for the current contract and its recorded reuse disposition.

Do not mix:

- unrelated cleanup;
- speculative abstractions;
- opportunistic modernization;
- features not requested;
- broad refactors unrelated to the slice;
- equivalent new implementation when an approved `EXISTING APPT`, `ADAPT`, or `PORT` path already supplies the behavior.

Record useful out-of-scope debt separately.

### 5. Use the right feedback loop

For behavior changes, apply `../test-driven-development/SKILL.md` when test-first behavior is practical.

For bugs, reproduce before fixing.

For configuration or other non-testable changes, use the smallest check that can prove the current hypothesis.

Follow `../../execution/verification.md` and `../../routing/route.md` rather than running terminal repository verification after every edit. Use the smallest practical local check first. If that exact operation is genuinely blocked, use the narrowest existing hosted diagnostic route. Temporary branch-scoped workflow YAML is last-resort infrastructure only when local tooling and existing diagnostic modes cannot perform the required operation; keep it narrowly scoped and unprivileged, and remove it before the finished candidate unless it is explicitly accepted as permanent infrastructure.

### 6. Classify implementation discoveries

**Local detail:** helper shape, naming, bounded refactor, or other implementation choice inside the contract → proceed.

**Implementation defect:** code/test is wrong while the contract remains valid → diagnose and correct on the same branch.

**Contract exception:** material requirement, architecture, security, dependency, data, interface, scope, or user-visible behavior must change → stop and report under Arena policy.

### 7. Carry forward verified slices

After each slice, keep focused checks green and remove temporary scaffolding unless it has earned a permanent role.

Do not restart the plan or reopen accepted decisions without evidence.

### 8. Produce a finished candidate

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
- executed reuse/provenance disposition, including reused or ported tests where applicable;
- explicit contract-exception evidence when work cannot safely continue;
- one coherent finished candidate ready for terminal repository verification and review.

## Boundaries

- Do not code material external behavior from model memory or unverified assumptions.
- Do not use `NEW` implementation as the default when the Issue authorizes reusable compatible code.
- Do not silently rewrite the contract to fit implementation.
- Do not mix unrelated cleanup into the change.
- Do not use full CI as the ordinary inner feedback loop.
- Do not leave ordinary slices knowingly broken.
- Do not claim completion before fresh terminal verification.

## Completion gate

Before handoff, confirm:

- every approved behavior has an implemented slice;
- implementation remained inside the active contract;
- every non-trivial behavior followed its recorded reuse disposition and any `NEW` code has the Issue-authorized reason;
- focused verification supported the inner loop;
- implementation defects were corrected without changing the contract;
- material contract discoveries were escalated;
- the candidate is coherent and ready for completion verification.