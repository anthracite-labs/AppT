# Verification

This file defines universal verification strategy. The actual project defines its real commands, toolchain, tests, build system, formatters, generated artifacts, and verification checks.

## Core rule

Terminal repository verification is the final technical verification step for a finished candidate. It is not the implementation feedback loop.

## Implementation feedback loop

Use the smallest meaningful check that can prove or falsify the current implementation hypothesis.

Typical progression:

1. exact reproducer, focused test, or narrow check;
2. affected component, package, or module;
3. affected subsystem when the narrower layer is green;
4. broader repository checks only when evidence requires them;
5. terminal repository verification on the finished candidate.

Do not repeatedly run the entire repository suite or remote CI while diagnosing a narrow failure if a smaller reproducer can provide faster, clearer evidence. Do not use terminal full `verify` as the implementation debugger.

Within the selected affected scope, however, diagnostic execution should maximize useful failure evidence before correction. After the first symptom is reproduced, prefer the tool's native evidence-expansion mechanisms where safe: continue independent tasks, keep tests non-fail-fast, retain compiler/linter/type-checker multi-error output, and collect structured reports or artifacts. This is bounded breadth, not repository-wide breadth.

Diagnostic continuation must preserve strict pass/fail semantics. A failing task, test, compiler, linter, or analysis remains failing. If a failed prerequisite prevents dependent work from executing, record that dependency, correct only the blocker required to expose the rest of the affected scope, then rerun the bounded diagnostic before broader correction.

## Terminal repository verification

Before work is called complete, fixed, passing, ready, or technically verified, use `../skills/verification-before-completion/SKILL.md`.

Run complete project-defined repository verification only on a finished candidate.

Terminal repository verification must validate the candidate that is actually proposed for review. Any source or generated-state change after verification invalidates the previous terminal result and creates a new candidate.

CI or verification must not silently mutate the candidate being verified.

## Terminal verification failure

If terminal repository verification fails:

1. leave the terminal-verification loop immediately;
2. use `../skills/debugging-recovery/SKILL.md` to identify and confirm the smallest useful reproducer;
3. harvest the practical independent failures in the bounded affected surface using the tool's native reporting/continuation capabilities;
4. classify and group the resulting evidence by root cause before editing when reproduction is practical;
5. correct the coherent root causes minimally and add regression protection where practical;
6. regain targeted green evidence for the reproducer and the whole affected scope;
7. produce a new finished candidate;
8. run terminal repository verification again only after the targeted surface is green.

If a practical reproducer cannot be run in the current environment, preserve the exact blocker and use `../routing/route.md` to try the narrowest safe alternate route. Equivalent targeted evidence from that route may satisfy the focused-verification requirement. If no safe route can provide equivalent targeted evidence, stop and return the blocker; do not produce a new finished candidate or run terminal repository verification. Repeated terminal CI runs are not an acceptable substitute for the diagnostic loop.

## Distinct operation classes

Do not collapse unrelated work into a generic “run CI” action. Distinguish as needed:

- source/behavior feedback;
- compiler/build checks;
- format/lint checks;
- dependency-state generation;
- generated contracts or schemas;
- environment/toolchain execution availability;
- repository/provider administration;
- security-specific verification;
- terminal repository verification.

Dependency state should change only when the dependency graph actually changes.

Generated contracts follow the system that generates them; they are not automatically dependency state.

Formatting verification reports formatting state. It does not imply permission to mutate, commit, or push unrelated changes.

## Evidence

Verification evidence should state what was run, what candidate it applied to, and the relevant result.

Unresolved or unavailable verification must be reported explicitly rather than inferred as passing.