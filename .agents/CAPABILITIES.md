# Capabilities

Router and catalog for deliberately adopted capabilities.

Select the narrowest capability that materially helps the current task.

A catalog entry is not the procedure. After selecting a capability, read its referenced file before applying it.

If no capability clearly fits, proceed with normal reasoning.

Exact upstream commits and vendoring provenance live in `.agents/PROVENANCE.md`.

## Usage

- Prefer one primary capability.
- Compose capabilities only when each contributes distinct work.
- Respect each vendored skill's upstream invocation policy. A skill with `policy.allow_implicit_invocation: false` or `disable-model-invocation: true` is user-invoked: recommend or offer it when relevant, but do not start it implicitly.
- When a capability invokes another adopted capability by name, resolve it through this catalog and read that capability before continuing.
- If a capability requires a runtime primitive that is unavailable, do not pretend it ran successfully or silently rewrite its semantics.
- Vendored skill contents remain unchanged; repository-specific integration belongs outside the vendored skill.
- Tracker-backed planning capabilities use `docs/agents/issue-tracker.md`.
- For `wayfinder` integration, research tickets use normal ChatGPT primary-source research because the upstream `research` skill is not adopted; prototype tickets are dispatched to Arena through `.agents/ARENA-DISPATCH.md` and their results feed back into the map.
- For Arena, naming a capability under `Capabilities to load` in a dispatched Issue is explicit invocation.

## Skills

| Capability | Actor | Select when | Source | Path |
|---|---|---|---|---|
| writing-for-agents | ChatGPT | Creating or materially editing agent-consumed instructions or routed documentation | `mattpocock/skills` | `.agents/skills/writing-for-agents/SKILL.md` |
| grilling | ChatGPT | Human intent, tradeoffs, or consequential decisions need systematic clarification | `mattpocock/skills` | `.agents/skills/grilling/SKILL.md` |
| grill-with-docs | ChatGPT | Clarification should also capture durable domain language or qualifying decisions | `mattpocock/skills` | `.agents/skills/grill-with-docs/SKILL.md` |
| domain-modeling | ChatGPT | Canonical domain vocabulary needs to be established or sharpened, or a qualifying ADR recorded | `mattpocock/skills` | `.agents/skills/domain-modeling/SKILL.md` |
| codebase-design | Both | A driver needs shared vocabulary for module interfaces, seams, depth, or testability | `mattpocock/skills` | `.agents/skills/codebase-design/SKILL.md` |
| wayfinder | ChatGPT | A large uncertain effort cannot be coherently resolved in one planning session | `mattpocock/skills` | `.agents/skills/wayfinder/SKILL.md` |
| to-spec | ChatGPT | Already-decided work needs to become a formal implementation specification | `mattpocock/skills` | `.agents/skills/to-spec/SKILL.md` |
| to-tickets | ChatGPT | Decided work genuinely needs multiple tracer-bullet Issues with explicit dependencies | `mattpocock/skills` | `.agents/skills/to-tickets/SKILL.md` |
| prototype | Arena | A bounded implementation experiment is needed to answer one design question | `mattpocock/skills` | `.agents/skills/prototype/SKILL.md` |
| implement | Arena | Arena is executing decided work from an Issue or specification | `mattpocock/skills` | `.agents/skills/implement/SKILL.md` |
| tdd | Arena | Behavior should be implemented test-first through agreed seams | `mattpocock/skills` | `.agents/skills/tdd/SKILL.md` |
| diagnosing-bugs | Arena | A difficult observed failure needs a reproducible diagnosis and regression fix | `mattpocock/skills` | `.agents/skills/diagnosing-bugs/SKILL.md` |
| resolving-merge-conflicts | Arena | An active merge or rebase conflict must be resolved from source intent | `mattpocock/skills` | `.agents/skills/resolving-merge-conflicts/SKILL.md` |
| improve-codebase-architecture | Both | Architectural friction or deepening opportunities need to be surveyed or evaluated | `mattpocock/skills` | `.agents/skills/improve-codebase-architecture/SKILL.md` |
| code-review | Both | A change needs review against documented standards and intended behavior | `mattpocock/skills` | `.agents/skills/code-review/SKILL.md` |
| security-audit | Arena | A dedicated deep security audit is required | `cloudflare/security-audit-skill` | `.agents/skills/security-audit/SKILL.md` |

## Routing

### UNDERSTAND

Use normal reasoning and repository evidence by default.

- Domain language itself is unclear or inconsistent → **domain-modeling**

### DECIDE

- Human intent or tradeoffs need clarification → **grilling**
- Clarification should also produce durable domain or decision artifacts → offer **grill-with-docs**
- The uncertainty exceeds a coherent single planning session → offer **wayfinder**
- A specific module interface or seam must be designed → **grilling** + **codebase-design**
- Broader architectural friction or deepening opportunities need evaluation → offer **improve-codebase-architecture**
- Decisions are settled and need formalization → offer **to-spec**
- Settled work requires multiple dependent Issues → offer **to-tickets**

### DISPATCH

Select Arena capabilities only when they materially affect execution of the compiled Issue.

The compiled Issue explicitly invokes every capability named under `Capabilities to load`.

- General decided implementation → **implement**
- Explicit test-first implementation → **tdd**
- Difficult observed failure → **diagnosing-bugs**
- Bounded design experiment → **prototype**
- Active merge or rebase conflict → **resolving-merge-conflicts**
- Decided architecture implementation → **implement** + **codebase-design** when its vocabulary materially helps
- Dedicated deep security audit → **security-audit**

Arena dispatch itself is governed by `.agents/ARENA-DISPATCH.md`.

### REVIEW

- Review a proposed change → **code-review**
- Review interface or seam quality → **code-review** + **codebase-design**
- Broader architectural exploration would materially help → offer **improve-codebase-architecture**

Use normal reasoning for additional risk or evidence checks unless a listed capability materially improves the review.

### SECURITY

Normal security reasoning is always part of the task and requires no special capability.

Use **security-audit** only when a dedicated deep audit is warranted or explicitly requested.

## Known overlaps

- Offer **grill-with-docs** instead of bare **grilling** when clarification should create durable domain or decision artifacts.
- Offer **wayfinder** only when normal clarification and specification cannot reasonably contain the uncertainty.
- **codebase-design** supplies shared design vocabulary and reference; use it under a driver such as **grilling**, **implement**, **tdd**, **code-review**, or **improve-codebase-architecture**, not as a standalone process.
- Offer **improve-codebase-architecture** for architectural discovery or evaluation; do not use it as the implementation driver.
- **implement** may invoke implementation capabilities such as **tdd** and **code-review**; do not duplicate them in an Issue unless the distinction materially matters.
- Use **code-review** for the normal review pass.
- Use **security-audit** for dedicated audit work, not ordinary security reasoning.
