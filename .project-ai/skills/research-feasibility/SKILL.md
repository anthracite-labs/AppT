---
name: research-feasibility
description: Use when a project or feature decision depends on uncertain external facts, technical viability, alternatives, cost or resource implications, operational burden, or risky assumptions that need evidence before requirements are finalized.
---

# Research and Feasibility

## Purpose

Gather only the evidence needed to support a decision, then assess whether the proposed direction can reasonably proceed.

External-world claims should use authoritative current evidence. Project-state claims should use the project artifacts that own those facts.

## Workflow

### 1. State the decision

Write the concrete decision the research must support.

A topic is not enough. Research should be able to change or confirm a choice.

### 2. Identify decision-critical unknowns

Investigate only dimensions that could materially change the decision, such as:

- technical;
- domain or regulatory;
- market or competitive;
- user evidence;
- standards;
- resource or cost;
- operational burden;
- candidate comparison.

### 3. Plan the evidence

For each material unknown, choose the best evidence source and freshness requirement.

Prefer the source that owns the claim:

- official docs, specifications, source code, first-party APIs/data, primary research, regulators, or project owners for external facts;
- code, manifests, configuration, tests, CI, deployment config, telemetry, and accepted project docs for project facts.

Use secondary material for discovery or interpretation, not as a casual substitute for a primary owner.

### 4. Gather and trace findings

Collect only decision-relevant evidence.

For each load-bearing finding, retain enough provenance to recover the source.

Surface:

- conflicting evidence;
- stale evidence;
- thin evidence;
- absence of evidence;
- facts that remain uncertain.

Model memory may suggest a query or hypothesis; it is not sufficient evidence for a current material external claim.

### 5. Synthesize alternatives and uncertainty

Summarize what the evidence changes.

Separate:

- supported findings;
- alternatives;
- assumptions;
- conflicting evidence;
- evidence gaps.

Do not turn missing evidence into a positive conclusion.

### 6. Assess feasibility

Evaluate only the dimensions that matter.

**Technical:** compatibility, integration constraints, data/migration complexity, security/compliance blockers, performance or scale constraints, and unknowns needing a spike.

**Resource / economic:** meaningful engineering effort, infrastructure/vendor/licensing cost, opportunity cost, and whether burden is proportionate to expected value.

**Operational:** deployment/support burden, required expertise, maintenance, observability/incident implications, organizational constraints, and external-service dependency.

Avoid precise estimates that the evidence cannot support.

### 7. Promote risky assumptions

For each assumption that could invalidate the direction, define the cheapest useful validation method: targeted research, prototype, spike, benchmark, contract test, user validation, or vendor confirmation.

### 8. Decide

Use one outcome:

- **FEASIBLE**
- **FEASIBLE WITH CONDITIONS**
- **NOT CURRENTLY FEASIBLE**

State the conditions or blockers that make the outcome true.

## Output contract

Produce a decision-grade result containing:

- Decision Being Supported
- Key Findings with evidence
- Alternatives Considered
- Conflicting Evidence
- Uncertainty / Evidence Gaps
- Assumptions Requiring Validation
- Technical Feasibility
- Resource / Economic Feasibility when material
- Operational Feasibility when material
- Conditions
- Outcome

Pass downstream only the evidence, constraints, conditions, and unresolved assumptions that affect requirements.

## Boundaries

- Do not research broadly without a decision to support.
- Do not treat project assumptions as proof of external facts.
- Do not treat external documentation as proof of this project's state.
- Do not create mandatory research workspaces, digests, or feasibility files.
- Do not hide material uncertainty to produce a cleaner verdict.
- Do not drift into requirements, architecture, or implementation.

## Completion gate

Before handoff, confirm:

- the decision is explicit;
- material unknowns were investigated;
- load-bearing claims trace to authoritative evidence;
- uncertainty and conflicting evidence are visible;
- relevant feasibility dimensions were assessed;
- risky assumptions have a validation path;
- the outcome and conditions are explicit.