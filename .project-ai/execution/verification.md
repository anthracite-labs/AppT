# Verification

This file defines universal verification strategy. The actual project defines its real commands, toolchain, tests, build system, formatters, generated artifacts, and verification checks.

## Core rule

Terminal repository verification is the final technical verification step for a finished candidate. It is not the implementation feedback loop.

## Implementation feedback loop

Use the smallest meaningful check that can prove or falsify the current implementation hypothesis.

Within that chosen scope, prefer diagnostic execution that exposes all practical independent failures before correction. Narrow scope does not mean first-error-only. Use the owning tool's native non-fail-fast, continue-on-independent-failure, multi-error, report, or equivalent capabilities where they preserve valid evidence; if an umbrella command aborts early, run its independent owned sub-checks separately when safe.

Typical progression:

1. exact reproducer, focused test, or narrow check;
2. affected component, package, or module;
3. affected subsystem when the narrower layer is green;
4. broader repository checks only when evidence requires them;
5. terminal repository verification on the finished candidate.

Do not repeatedly run the entire repository suite or remote CI while diagnosing a narrow failure if a smaller reproducer can provide faster, clearer evidence. Conversely, do not repeatedly edit after the first surfaced error when the same narrow diagnostic surface can safely reveal sibling failures. Do not use terminal full `verify` as the implementation debugger.

## Terminal repository verification

Before work is called complete, fixed, passing, ready, or technically verified, use `../skills/verification-before-completion/SKILL.md`.

Run complete project-defined repository verification only on a finished candidate.

Terminal repository verification must validate the candidate that is actually proposed for review. Any source or generated-state change after verification invalidates the previous terminal result and creates a new candidate.

CI or verification must not silently mutate the candidate being verified.

## Terminal verification failure

If terminal repository verification fails:

1. leave the terminal-verification loop immediately;
2. use `../skills/debugging-recovery/SKILL.md` to identify and confirm the smallest useful reproducer;
3. map the practical independent failures in that bounded surface before editing;
4. classify/group the observed failures and establish evidence-backed root causes when reproduction is practical;
5. correct the root causes coherently and add regression protection where practical;
6. regain targeted green evidence for the reproducer and affected scope;
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