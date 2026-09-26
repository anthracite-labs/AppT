---
name: project-bootstrap
description: Use when turning FinalTemplate into a real greenfield project, adopting an existing repository into this control plane, reconciling a partial setup, or repairing inconsistent project foundations before normal engineering proceeds.
---

# Project Bootstrap

## Purpose

Operationalize the canonical bootstrap procedure without creating shadow project state.

Greenfield work decides missing foundations deliberately. Brownfield work discovers and preserves valid existing choices.

## Authority

Read and follow `../../bootstrap/project.md`. It owns bootstrap policy and the ESTABLISHED / MISSING / INCONSISTENT / NEEDS DECISION model.

This skill supplies the operating method. If they conflict, the canonical bootstrap procedure wins.

## Workflow

### 1. Classify current foundations

Inspect the repository before changing it.

Classify each relevant foundation as:

- **ESTABLISHED**
- **MISSING**
- **INCONSISTENT**
- **NEEDS DECISION**

Do not normalize an established project back toward the template.

### 2. Establish identity and accepted technology choices

Use accepted discovery, requirements, architecture, and feasibility decisions to establish only what the project needs:

- project identity and purpose;
- language/runtime/framework;
- package/dependency manager and locking;
- source/test layout;
- build system;
- persistence/data choices;
- generated artifacts.

For greenfield work, ask or rely on accepted decisions. Do not infer a stack because it is common.

### 3. Create the smallest usable baseline

As applicable, make the project reproducibly installable/restorable, buildable, runnable/exercisable, testable, and statically checkable.

Prefer a minimal real baseline over a large scaffold of unused choices.

### 4. Put project facts in their real owners

Examples:

- runtime/dependencies → manifests and lockfiles;
- commands → project scripts/configuration;
- CI → workflow/provider configuration;
- deployment → deployment configuration;
- repository enforcement → provider settings;
- project purpose/usage → project documentation.

Do not create a parallel project profile.

### 5. Establish executable quality checks

Create project-owned checks only where the project needs them, such as:

- tests;
- formatting;
- lint/static analysis;
- type checking;
- build/compile;
- generated-state verification;
- security, accessibility, or performance checks when required.

Prefer executable enforcement over duplicated prose.

### 6. Add CI, governance, and release foundations conditionally

Add CI only after local project commands exist.

Add issue/PR templates, labels, CODEOWNERS, rulesets, security settings, release automation, or deployment foundations only when the actual project needs them.

Prefer provider-native organization policy over copied repository files when it already enforces the requirement.

### 7. Verify the baseline

Follow `../../execution/verification.md`.

Bootstrap is not complete because files exist; required project-owned commands and foundational workflows need fresh evidence.

### 8. Reconcile durable state after acceptance

After accepted bootstrap work lands on `main`, update `../../PROJECT_STATE.md` only if durable project reality changed.

Activity is not project state.

## Output contract

Bootstrap leaves:

- a usable project repository;
- required foundations established or explicitly blocked on a decision;
- project facts stored in their canonical artifacts;
- executable project checks where applicable;
- provider governance established only where needed;
- no permanent bootstrap report or shadow project profile.

## Boundaries

- Do not choose a technology stack without an accepted basis.
- Do not rewrite valid brownfield conventions to match the template.
- Do not create generic CI, release, governance, or security files by default.
- Do not duplicate provider settings in Markdown.
- Do not update PROJECT_STATE before the underlying change is accepted.

## Completion gate

Before handoff, confirm:

- required foundations are ESTABLISHED or explicitly blocked;
- inconsistencies are resolved or surfaced;
- the repository is usable for actual work;
- project facts live in their real owners;
- required baseline checks have fresh evidence;
- no shadow bootstrap/state artifact was created.