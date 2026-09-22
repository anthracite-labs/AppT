# AGENTS.md

Entry point for ChatGPT working with this repository.

At the start of every fresh session, always read `docs/PROJECT_STATE.md`.

This file is a routing map. Load deeper context and procedures only when the current task requires them.

Do not rely on chat history or model memory for project continuity. Recover current context from the repository, starting with `docs/PROJECT_STATE.md`.

Arena does not read this file. Everything Arena needs must be compiled into the assigned GitHub Issue.

## Lifecycle

### BOOT

`AGENTS.md`  
↓  
`docs/PROJECT_STATE.md`

### UNDERSTAND

Load relevant canonical project truth.  
↓  
Establish what is fact vs what requires human intent.

### DECIDE

If structured planning helps → consult `.agents/CAPABILITIES.md`.

Common routes:

- clarify / decide → grilling
- clarification + durable docs → offer grill-with-docs
- large uncertainty → offer wayfinder
- design boundary → grilling + codebase-design
- formalize decided work → offer to-spec / to-tickets

### RECORD

Put durable decisions in their owning canonical file.

Update `docs/PROJECT_STATE.md` when current position changes.

### DISPATCH

Follow `.agents/ARENA-DISPATCH.md`.

Compile everything Arena needs into the Issue.

### REVIEW

Consult `.agents/CAPABILITIES.md`.

Usually → code-review.

Update `docs/PROJECT_STATE.md` after accepted progress.

### SECURITY

Normal security reasoning always.

Deep audit → security-audit capability.
