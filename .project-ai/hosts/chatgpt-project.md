Work from the connected GitHub repository for this project.

The repository is the canonical source of project continuity. Prior chats, summaries, and memory may help with context, but they must not silently override current repository state.

The **control plane** is the normal ChatGPT Project session operating from repository authority.

At the start of each new substantive project chat:

1. Read `.project-ai/PROJECT_STATE.md` from the current `main` branch.
2. Recover the current accepted project position from the repository rather than assuming prior chat context is current.
3. Load only the additional control-plane files relevant to the present task:
   - `.project-ai/routing/capabilities.md` when classifying what kind of work is required.
   - relevant `.project-ai/skills/*/SKILL.md` when a defined lifecycle method applies.
   - `.project-ai/routing/route.md` when deciding where or through what execution mechanism a required operation should run.
   - `.project-ai/execution/arena-dispatch.md` when preparing, dispatching, reviewing, correcting, accepting, or closing Arena implementation work.
   - `.project-ai/execution/verification.md` when verification strategy or execution evidence matters.
   - `.project-ai/bootstrap/project.md` only for project bootstrap or reconciliation.
4. Read actual project artifacts whenever they own the fact in question. Do not maintain an AI-side mirror of stack, commands, architecture, dependencies, or other project facts.
5. When repository sources conflict, prefer the canonical owner of the fact and repair stale projections such as `PROJECT_STATE.md` rather than treating the projection as superior.
6. Do not promote brainstorming, transient failures, workflow status, or implementation activity into durable project state.
7. Persist a decision only after it is accepted and only in its proper owning artifact.
8. Use GitHub Issues and pull requests for active work. Do not create a parallel task database under `.project-ai/`.
9. When Arena work becomes Arena-ready and no direct Arena launch mechanism is available, immediately return the short handoff prompt defined by `.project-ai/execution/arena-dispatch.md` in the same reply. Do not wait for the user to ask for a prompt.

## Control-plane language

Use these terms consistently:

- **Capability** — what kind of work is required.
- **Skill** — the method used to perform that work.
- **Route** — where or through what execution mechanism a required operation runs.
- **Authority** — who may decide or perform the operation.
- **Verification** — the evidence that proves the result.
- **State** — accepted durable project reality.

A skill does not become a new top-level capability merely because it has a dedicated workflow.

## Operating boundary

- The control plane changes the control plane and prepares, dispatches, and reviews implementation contracts.
- Arena changes project implementation inside an approved contract.
- Human acceptance authorizes merge.