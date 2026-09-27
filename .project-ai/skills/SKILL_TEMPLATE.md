# Anthracite SKILL.md Template

This file is the canonical authoring format for `.project-ai/skills/*/SKILL.md`.

It is maintenance guidance, not a runtime skill.

## Authoring rules

- Keep frontmatter first.
- Use exactly two frontmatter fields: `name` and `description`.
- Keep both values as single-line YAML plain scalars; rewrite wording rather than introducing quoted, multiline, or nested frontmatter.
- `name` must exactly match the skill directory.
- Write `description` as trigger conditions only. Start with `Use when...`.
- Keep descriptions provider-neutral and under 500 characters where practical.
- Use imperative voice.
- Keep `SKILL.md` as the runtime decision/workflow layer, not an encyclopedia.
- Prefer one default path before alternatives.
- Point to canonical project/control-plane owners instead of restating them.
- Wire every runtime skill into at least one canonical control-plane routing owner, normally `../routing/capabilities.md`; `validate.py` rejects orphaned skills and broken routing references.
- Put provenance and upstream adaptation notes in `../SOURCES.md`, not runtime files.
- Add supporting files only when optional depth, deterministic automation, or reusable assets genuinely need them.
- Keep runtime skills under 500 lines; target materially less.
- Remove repeated rationale, duplicated rules, and sections that do not change a decision, action, or verification step.

## Required structure

```markdown
---
name: <directory-name>
description: Use when <concrete trigger conditions; no workflow summary>.
---

# <Human-readable Skill Name>

## Purpose

<1-3 short paragraphs defining the skill's job and lifecycle boundary.>

## Workflow

### 1. <First decision/action>

<Imperative runtime guidance.>

### 2. <Next decision/action>

<Continue only as far as needed to execute the skill safely.>

## Output contract

<The minimum result the skill must produce or establish.>

## Boundaries

- <What this skill does not own.>
- <When to stop or escalate.>
- <What must not be duplicated or silently changed.>

## Completion gate

Before handoff, confirm:

- <observable completion condition>;
- <observable completion condition>;
- <no unresolved ownership/authority violation>.
```

## Optional sections

Add only when they change runtime behavior.

### Authority

Use when another committed file is the canonical owner of policy or lifecycle state.

Keep it short:

```markdown
## Authority

Read and follow `<relative-path>`. It owns <specific concern>.

This skill supplies <narrow operating method>. If they conflict, the canonical owner wins.
```

### Decision rules

Use for branching logic that is clearer as a table or compact rule set than as workflow prose.

### Reference routing

Use only when the skill has bundled `references/`, `scripts/`, or `assets/`.

Every supporting file must have an explicit "open/run when..." reason.

## Precision pass

Before adding content, prefer in this order:

1. replace a vague or stale rule;
2. narrow an over-broad rule;
3. move maintenance/provenance/reference detail out of runtime;
4. delete duplication;
5. add new guidance only when no existing rule can express the required behavior cleanly.

After editing:

1. re-read the skill as a runtime consumer;
2. remove repeated trigger language from the body;
3. remove provenance and source commentary;
4. remove rules already owned by a canonical control-plane file;
5. ensure every remaining line changes a decision, action, or verification step;
6. run `python .project-ai/skills/validate.py`.