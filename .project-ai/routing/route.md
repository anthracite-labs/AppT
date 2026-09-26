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

## Arena

Project implementation is normally executed by Arena through the self-contained GitHub Issue contract defined in `../execution/arena-dispatch.md`.

Arena receives bounded implementation authority. It is not responsible for reconstructing project intent from prior chats.

## Hosted and provider execution

Hosted execution, CI, or provider administration may be used when the exact operation genuinely requires it.

Escalate the blocked operation, not the entire workflow.

Repository/provider settings remain owned by the provider where enforcement actually occurs.