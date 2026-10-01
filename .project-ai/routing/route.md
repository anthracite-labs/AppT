# Execution Routing

Route answers **where or through what execution mechanism a required operation should run**.

It is separate from capability classification and from the skill that defines the method for the work.

## Default sequence

For each required operation:

1. Identify the exact operation required.
2. Probe the execution mechanisms and tools available in the current session/environment.
3. Prefer existing local or directly connected tooling when it can safely perform the operation.
4. Attempt the smallest relevant command or action.
5. If blocked, preserve concrete failure evidence.
6. Try a safe alternate route when one exists.
7. Escalate only the genuinely blocked operation to hosted or remote execution.
8. Return to the narrowest effective route after the blocked operation is complete.

Do not move an entire development loop to hosted infrastructure merely because one operation is unavailable locally.

For diagnosis, route the smallest affected surface to the mechanism that can return the richest safe evidence from that surface. Prefer tool-native continuation/non-bail/reporting and existing structured artifacts before inventing new infrastructure. The goal is to expose practical independent failures together, not to widen execution into unrelated domains.

When ordinary output is insufficient, targeted temporary diagnostic instrumentation in the implementation branch — for example a test listener, extra assertion, trace, or report hook — is preferred over a new workflow when it can close the evidence gap safely. Mark temporary instrumentation clearly and remove it before terminal verification unless it earns a permanent role.

## Valid blocker evidence

A blocker report should identify:

- the required operation;
- the current-session execution/tooling probe;
- the command or action attempted;
- the route attempted;
- the relevant failure output;
- safe alternatives attempted;
- why escalation is necessary.

“The environment cannot do X” without evidence is not a sufficient blocker report.


## AppT execution facts

Project-owned commands and environment facts live in `../../docs/BUILD.md`, project configuration, and `../../.github/workflows/`.

When Android/JVM verification is required, probe the live session first. Missing preinstalled JDK, Android SDK, or network is not automatically unavailable tooling; install disposable local tooling when that is safe. Escalate only a proven blocked operation.

The project-owned hosted fallback for that blocked Android/JVM feedback is documented in `../../docs/BUILD.md`: a `workflow_dispatch` of `.github/workflows/diagnose.yml` in the narrowest mode that answers the question (`app-unit`, `samsung-unit`, `android-static`, `android-build`), not a full terminal suite and not a new workflow file. A diagnostic run is implementation feedback only and never satisfies `verify / gate`.

Within that chosen mode, follow the diagnostic-breadth behavior owned by `../../docs/BUILD.md` and the reviewed resolver/reporting path. Narrow scope and rich evidence are complementary: choose the smallest relevant domain, then harvest its practical failure set before correction.

Temporary branch-scoped workflow YAML is last-resort infrastructure. Add it only after a live capability probe shows that local tooling and the existing `diagnose.yml` modes cannot perform the required operation. Do not recreate retired one-shot regeneration workflows, and do not add per-slice diagnostic workflow files: focused diagnostics are permanent modes of `diagnose.yml`.

Agent and control-plane invocation follows `.github/workflows/agent-control.yml`: an integration that can mutate pull-request metadata adds a `ci:*` command label and the trusted bridge dispatches the matching workflow from the trusted default branch, carrying the requested branch as `target_ref` and resolved head SHA as `expected_sha` inputs so every job can hard-fail if the branch moved before target-controlled work begins. When a diagnostic must be narrower than the whole mode and direct `workflow_dispatch` is unavailable, the PR body may additionally contain one exact mode-bound `<!-- appt-ci-focus <mode>: <selector> -->` marker; the bridge treats it only as untrusted data and the trusted diagnose resolver validates it before dispatch. No marker preserves whole-mode behavior, another mode's marker is ignored, duplicate matching markers fail closed, and `ci:full` never forwards focus. The bridge never checks out or executes pull-request-controlled code, and it is the only privileged control path in the repository. Terminal verification of a finished candidate remains a full `verify` run.

Dependency or generated-state regeneration remains a distinct operation. Use the narrowest safe route that can produce the required artifacts, then verify the resulting candidate with the repository's strict checks. Ordinary source or formatting fixes are not regeneration triggers. Generated contracts are not dependency state.

Repository-administration settings remain owner/provider operations. Do not weaken project workflows merely to work around missing administration permission.

## Security-audit execution boundary

Static source inspection is read-only.

When security-audit evidence requires executing target-controlled builds, tests, processes, browsers, emulators, fuzzers, or fixture processing, use an OS-enforced sandbox that provides all of the following:

- no external network; isolated loopback only when the check genuinely requires local client/server traffic;
- an empty environment populated from an explicit safe allowlist;
- a read-only target and toolchain;
- writes confined to an isolated scratch location;
- bounded CPU, memory, process, file-size, disk, and wall-clock resources.

Use dummy principals, fixtures, and secrets. Do not probe production, shared infrastructure, real user data, external services, or live control planes.

If every required control cannot be enforced, do not execute the target-controlled code for audit evidence. Record the exact **NEEDS VERIFICATION** blocker and provide the smallest safe validation plan.

This boundary governs execution safety only. Security findings and audit depth remain owned by `../skills/security-engineering/SKILL.md`.

## Arena

Project implementation is normally executed by Arena through the self-contained GitHub Issue contract defined in `../execution/arena-dispatch.md`.

Preparing or updating that Issue and launching Arena are distinct operations.

When an Issue becomes Arena-ready:

1. use a direct Arena launch mechanism when the current host exposes one;
2. otherwise use the manual handoff route by returning the short copy/paste prompt defined in `../execution/arena-dispatch.md` in the same response.

The manual handoff prompt references the Issue rather than repeating its contract. Do not claim Arena was launched or dispatched when only the Issue or prompt was produced.

Arena receives bounded implementation authority. It is not responsible for reconstructing project intent from prior chats.

## Hosted and provider execution

Hosted execution, CI, or provider administration may be used when the exact operation genuinely requires it.

Escalate the blocked operation, not the entire workflow.

Repository/provider settings remain owned by the provider where enforcement actually occurs.