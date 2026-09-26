# Project Bootstrap and Reconciliation

Use this procedure when turning FinalTemplate into a real project or when reconciling an existing or partially established repository.

This file owns bootstrap policy: what foundations must be considered, how existing repository reality is classified, and where bootstrap begins and ends.

Use `../skills/project-bootstrap/SKILL.md` as the operating method for executing this procedure.

The procedure is project-neutral. It establishes decisions deliberately; it does not prescribe a language, framework, package manager, CI provider, test framework, database, deployment platform, security product, or release method.

## Principles

- The repository root belongs to the actual project.
- `.project-ai/` is the committed internal AI control plane.
- Existing valid project choices are preserved.
- Missing foundations are established deliberately.
- Inconsistencies are surfaced rather than silently normalized.
- Material changes to established choices require an explicit decision.
- Project facts live in their actual owning artifacts.
- Specialist lifecycle methods are loaded only when their concern is active.
- Bootstrap produces the project itself, not a permanent bootstrap report.

## Authority and execution

Bootstrap uses the same control-plane language as the rest of the repository.

- **Capability** identifies what kind of work is required.
- **Skill** supplies the method.
- **Route** identifies where or through what execution mechanism an operation runs.
- **Authority** determines who may decide or act.
- **Verification** supplies evidence.
- **State** records accepted durable reality only after acceptance and merge.

The control plane:

- inspects and classifies repository foundations;
- resolves or escalates `NEEDS DECISION` items;
- uses discovery, research, requirements, architecture, security, and other lifecycle skills when their triggers apply;
- establishes accepted bootstrap direction;
- prepares bounded implementation contract(s);
- reviews resulting implementation against the contract.

Arena:

- changes project artifacts inside an approved implementation contract;
- does not silently decide unresolved project-level bootstrap choices;
- follows `../execution/arena-dispatch.md` for implementation authority, branch/PR lifecycle, verification reporting, and contract exceptions.

Control-plane files remain control-plane-owned. Arena does not change the control plane.

Human acceptance remains the final authority before merge.

After accepted bootstrap work lands on `main`, reconcile `../PROJECT_STATE.md` if durable project reality changed.

## 1. Determine repository state

Classify relevant foundations as:

- **ESTABLISHED** — a valid project choice already exists;
- **MISSING** — the project needs a foundation that does not yet exist;
- **INCONSISTENT** — repository artifacts disagree materially;
- **NEEDS DECISION** — more than one valid direction exists and a human/project decision is required.

Do not reset an existing project toward the template.

For an existing repository, inspect authoritative project artifacts before proposing normalization.

For a greenfield repository, unresolved choices remain decisions; do not infer them from convention.

## 2. Establish project identity

Determine as appropriate:

- project name;
- purpose;
- intended users/consumers;
- scope and non-goals;
- repository visibility;
- relationship to other repositories/services where relevant.

Replace the root `README.md` skeleton with the actual project's README.

The root README describes the project, not the internal AI control plane.

If identity, need, users, or success are materially unclear, use `../skills/project-discovery/SKILL.md` before treating them as bootstrap facts.

## 3. Licensing

Make an explicit project licensing decision.

“No root license yet” is a legitimate choice for a private or not-yet-distributed project.

Do not inherit a license merely because the repository came from a template.

## 4. Technology and repository structure

Establish only what the actual project needs, such as:

- language/runtime;
- framework;
- package/dependency manager;
- dependency-locking strategy;
- build system;
- source/test layout;
- database or persistence approach;
- generated artifacts/contracts.

Project facts remain canonical in actual project artifacts. Do not create a parallel `.project-ai` project profile.

When a technology choice depends on uncertain external facts or viability, use `../skills/research-feasibility/SKILL.md`.

When the choice creates durable system boundaries, ownership, dependency direction, state/data rules, or interface contracts, use `../skills/architecture-interface-design/SKILL.md`.

## 5. Ignore rules

Create a root `.gitignore` only after the actual project produces artifacts that should be ignored.

Do not use a generic multi-language ignore file merely because the template could support many stacks.

`.project-ai/` is intentionally version-controlled.

## 6. Tests, formatting, and quality checks

Establish the project's actual:

- test strategy;
- test commands;
- formatter/linter;
- static analysis;
- build/compile checks;
- generated-state verification where required.

These project-owned commands are what `../execution/verification.md` will rely on.

Do not invent duplicate CI-only verification commands when the project can expose a reproducible project-owned command.

## 7. CI and repository automation

Determine whether CI or repository automation is required.

If it is, use `../skills/ci-cd-automation/SKILL.md` for the implementation method.

Bootstrap owns the need to establish an appropriate automation foundation. The CI/CD skill owns how that automation is designed and implemented.

Do not add generic CI, Dependabot, CodeQL workflows, release automation, or provider configuration merely because they are common.

Where organization-level GitHub features already provide a genuine organization-wide policy, prefer the native organization feature instead of copying files into every repository.

## 8. Security

Determine whether the project activates a security-engineering trigger.

When it does, use `../skills/security-engineering/SKILL.md`.

Bootstrap owns the requirement to consider project security foundations. The security skill owns the depth and method of security design, hardening, review, or audit.

Security depth follows risk, not ritual.

## 9. GitHub governance

Determine the repository governance the real project needs, including as appropriate:

- default branch;
- rulesets/branch protections;
- review requirements;
- required checks;
- merge methods;
- force-push/deletion policy;
- Actions permissions;
- secret scanning/push protection;
- security configuration.

Enforcement lives in GitHub settings. Do not maintain a shadow copy of every setting in `.project-ai/`.

Repository/provider administration follows `../routing/route.md`: use the narrowest execution mechanism that can safely perform the required operation.

## 10. Release and deployment

Determine whether the project ships, publishes, or deploys.

If it does, establish only the release/deployment foundations required for the project, such as:

- release/versioning strategy;
- deployment or publication target;
- artifact identity/publishing;
- environment/secrets handling;
- recovery expectations.

Use `../skills/release-deployment/SKILL.md` when an accepted candidate is actually being released or deployed.

Do not manufacture release infrastructure for projects that do not need it.

## 11. Durable project documentation

Create project, product, architecture, operational, or migration documentation only when the real project needs it.

Durable technical decisions belong to the actual project.

Use `../skills/documentation-adrs/SKILL.md` when documentation or decision-rationale preservation requires a method.

Use ADRs only when preserving the rationale materially improves future engineering decisions. Do not create `.project-ai/DECISIONS.md`.

## 12. Verify, accept, merge, and reconcile state

When the bootstrap implementation candidate is complete:

1. run targeted verification during implementation using `../execution/verification.md`;
2. run terminal repository verification on the finished candidate;
3. perform control-plane contract review;
4. obtain human acceptance;
5. merge the accepted implementation;
6. reconcile `../PROJECT_STATE.md` only after the accepted work lands on `main` and only when durable project reality changed.

Update `PROJECT_STATE.md` to reflect accepted durable reality:

- phase;
- current objective;
- accepted decisions affecting active work;
- durable blockers;
- latest accepted milestone;
- next authorized action;
- authoritative references.

Do not record anticipated state, temporary setup activity, command logs, workflow status, or chat history.

## Completion

Bootstrap/reconciliation is complete when:

- the repository is a usable starting state for the actual project;
- required foundations are established or explicitly blocked on a decision;
- project facts live in their canonical project artifacts;
- terminal repository verification has passed for the finished bootstrap candidate;
- the implementation is contract-compliant;
- the human has accepted the bootstrap result;
- accepted bootstrap work has merged;
- `PROJECT_STATE.md` has been reconciled when durable reality changed.

Do not create a separate bootstrap report.