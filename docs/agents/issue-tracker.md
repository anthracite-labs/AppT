# Issue Tracker

This repository uses GitHub Issues.

Use GitHub's native issue relationships rather than maintaining a parallel planning system.

## Core operations

When a skill says **publish to the issue tracker**, create a GitHub Issue.

When a skill says **fetch the relevant ticket or spec**, read the Issue body and comments.

Use the authenticated GitHub interface available in the current runtime. When working in a repository shell, use `gh` so the repository is resolved from the current Git remote.

Common shell operations:

```sh
gh issue create --title "..." --body "..."
gh issue view <number> --comments
gh issue comment <number> --body "..."
gh issue close <number>
```

## Readiness

`ready-for-agent` means the planning artifact is sufficiently specified for downstream agent work.

It is not, by itself, authorization for Arena to execute the Issue.

An Issue becomes Arena-ready only when `.agents/ARENA-DISPATCH.md` has compiled and validated the execution context and the Issue is assigned to Arena.

Prefer enriching an existing implementation Issue during dispatch rather than creating a duplicate Issue.

A parent specification produced by `to-spec` is an input to implementation planning. Do not assign the parent specification itself to Arena when implementation has been decomposed into child tickets.

## Decomposition

When work needs multiple implementation slices, use GitHub sub-issues.

Create a child directly from a shell:

```sh
gh issue create --parent <parent-number> --title "..." --body "..."
```

Or attach an existing Issue:

```sh
gh issue edit <parent-number> --add-sub-issue <child-number>
```

Use native issue dependencies for blocking relationships:

```sh
gh issue edit <issue-number> --add-blocked-by <blocker-number>
```

A ticket is executable only when its blockers are closed and it has passed Arena dispatch.

## Wayfinding operations

`wayfinder` uses GitHub Issues as its shared decision map.

- Map → one Issue labelled `wayfinder:map`.
- Decision tickets → sub-issues of the map.
- Ticket types → `wayfinder:research`, `wayfinder:prototype`, `wayfinder:grilling`, or `wayfinder:task`.
- Blocking → native GitHub issue dependencies.
- Frontier → open, unassigned child Issues with no open blockers.
- Claim → assign the Issue to the person resolving that decision.
- Resolve → record the decision on the ticket, close it, and let the map point to that resolved ticket rather than duplicating its detail.

Wayfinder produces decisions, not Arena work orders. Any later implementation still passes through `.agents/ARENA-DISPATCH.md`.

## Labels

The agent workflow depends only on these custom labels:

- `ready-for-agent`
- `wayfinder:map`
- `wayfinder:research`
- `wayfinder:prototype`
- `wayfinder:grilling`
- `wayfinder:task`

Before applying one of these labels, create it if it does not already exist in the repository.

Do not introduce a broader triage state machine unless the repository later develops a real need for one.
