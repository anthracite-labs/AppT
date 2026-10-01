# Building AppT

## Requirements

- JDK 17 (the minimum and default JDK for the pinned AGP 9.4.x)
- Android SDK with platform 37 (`compileSdk` 37; `targetSdk` stays 36 — see
  `docs/architecture/discovery.md`) and the build tools AGP 9.4.1 requires
- Node.js 24 (backend package; the Cloud Functions 2nd gen `nodejs24` LTS
  runtime)

## Kotlin under AGP 9

AGP 9's built-in Kotlin compiles Kotlin in every module that applies AGP, so
no module applies `org.jetbrains.kotlin.android`. The `kotlin` version in
`gradle/libs.versions.toml` still versions the compose and serialization
compiler plugins, which remain separate plugins. See the Android "Migrate to
built-in Kotlin" documentation.

## Gradle wrapper

The repository pins the Gradle distribution in
`gradle/wrapper/gradle-wrapper.properties` (Gradle 9.7.1, an exact version).

The wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) is
**committed**, together with `distributionSha256Sum`, so the toolchain itself is
pinned by checksum. CI does not generate it: `.github/workflows/verify.yml` fails if
it is missing.

These files were produced once by a one-shot bootstrap workflow, which generated
the wrapper in an empty scratch build (running `gradle wrapper` inside this
repository would configure the AppT build, and resolve AGP, before the wrapper
meant to run that build exists), recorded the published distribution checksum,
generated the lockfiles and the SHA-256 verification metadata, proved the result
passes strict verification, and committed it. That workflow was **deleted** once
the artifacts landed: the committed state is now the only source, and CI never
regenerates it.

`distributionSha256Sum` was read from the published
`gradle-9.7.1-bin.zip.sha256` and independently matches the Gradle
release-checksums reference.

To regenerate locally with a Gradle 9.7.1 installation:

```bash
gradle wrapper --gradle-version 9.7.1 --distribution-type bin
```

## Commands

Android/Kotlin verification interfaces, from the repository root.

CI runs the four failure domains below as independent parallel jobs so one
failing domain cannot hide another's evidence; `ciCheck` is the local umbrella
over exactly the same work, in one invocation:

```bash
# Root Android/Kotlin verification lifecycle interface aggregating strict
# compiler diagnostics (-Werror), Spotless + ktfmt, debug assembly, unit/Robolectric
# tests, Android Lint, detekt, Kover coverage verification, dependency locks,
# appTGuards, and macrobenchmark compilation:
./gradlew --no-daemon --dependency-verification=strict ciCheck

# The CI-owned failure domains, one at a time:
./gradlew --no-daemon --dependency-verification=strict androidFormat
./gradlew --no-daemon --dependency-verification=strict --continue androidStatic
./gradlew --no-daemon --dependency-verification=strict --continue androidBuild
./gradlew --no-daemon --dependency-verification=strict --continue androidUnit

# Spotless formatting:
./gradlew spotlessApply
./gradlew spotlessCheck

# Installed-app runtime acceptance on Gradle Managed Device (API 29):
./gradlew --no-daemon --dependency-verification=strict \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  :app:pixel2api29DebugAndroidTest
```

`androidUnit` runs `:app:testDebugUnitTest` **and** `:samsung:test` plus Kover
coverage generation and verification: Samsung unit tests are authoritative
verification evidence, not diagnostics.

### Dependency verification

`gradle/verification-metadata.xml` is committed with SHA-256 checksums for every
resolved component, and CI runs every Gradle command with
`--dependency-verification=strict`. An artifact whose checksum does not match is rejected
rather than used.

`gradle/verification-metadata.template.xml` records the reviewed *policy* header
(verify-metadata on, no trusted-artifact wildcards, and why signature
verification is not yet enabled) separately from the generated checksum body, so
the policy decision stays reviewable without being buried in thousands of
generated lines.

Regenerate the checksums after a reviewed dependency change:

```bash
./gradlew --write-verification-metadata sha256 \
  spotlessApply ciCheck
```

Dependency lockfiles are committed. After a deliberate, reviewed dependency
change, refresh them with:

```bash
./gradlew resolveAndLockAll --write-locks
```

### Plugin-classpath tooling constraints (Issue #68)

Some vulnerable coordinates are never declared by an AppT module. They reach the
dependency graph only as transitives of the Gradle plugins this build applies —
`jose4j` through AGP's `bundletool`, `jdom2` through AGP's
`jetifier-processor`, and `org.eclipse.jgit` through Spotless's
`spotless-lib-extra`. They appear in no `*.gradle.lockfile` and nowhere in
`gradle/libs.versions.toml`, and the two sides that reason about them are
looking at different things:

- AppT's `Automatic Dependency Submission (Gradle)` workflow runs the Gradle
  build, resolves the relevant Gradle graph — including the
  plugin/buildscript classpath — and submits that resolved snapshot to GitHub's
  dependency graph. The alerts raised from the submitted snapshot are real.
- Dependabot's Gradle updater works from a separate and much narrower view. It
  parses the declared Gradle dependency files (`Dependabot::Gradle::FileParser`)
  and cannot mutate an undeclared transitive coordinate merely because that
  coordinate appears in the submitted dependency graph. A security update for
  such a coordinate fails with `dependency_not_found`, so the job stays red
  while the alert stays open.

The seam that owns them is the root `buildscript { dependencies { constraints {
classpath(...) } } }` block in `build.gradle.kts`. A constraint records the
coordinate in the declaration surface the updater reads *and* is a floor in
Gradle conflict resolution, never a downgrade, so it stays correct when a later
plugin bump moves the same transitive further forward.

`tools/security/enforce-gradle-tooling-constraints.mjs` enforces both halves of
that invariant for every coordinate in `TOOLING_ADVISORY_CONSTRAINTS`: the
coordinate must be declared at or above its first patched version somewhere
Dependabot's Gradle file parser reads, and `gradle/verification-metadata.xml`
must not still record a version below it.

```bash
node tools/security/enforce-gradle-tooling-constraints.mjs
tools/security/run.sh deps
```

`--write-verification-metadata` only ever adds entries, so when a constraint
raises a plugin transitive's resolved version the superseded `<component>` block
stays behind. Remove it in the same reviewed change: it is stale, and leaving it
makes the committed supply-chain artifact claim a vulnerable version the build
no longer resolves. Strict verification fails loudly if the removed entry was
still needed, so the removal is self-checking.

Backend, always package-prefixed from the repository root
(docs/architecture/release.md):

```bash
npm ci --prefix backend
# Backend static verification interface (typecheck, typed ESLint, Prettier, Knip):
npm run verify:static --prefix backend

# Backend tests with LCOV coverage:
npm run verify:test --prefix backend

# Backend verification interface aggregating both of the above:
npm run verify --prefix backend
```

## Where the Android verification runs

Probe the live session before assuming Gradle cannot run. Missing preinstalled
JDK, Android SDK, or network egress is not by itself unavailability. Follow
`.project-ai/routing/route.md`: probe, use or install disposable local tooling
when safe, and escalate only the blocked operation.

1. Probe `java -version`, `./gradlew --version`, and (when a download is
   required) reachability of `services.gradle.org`, `repo1.maven.org`, and
   `dl.google.com`.
2. If the toolchain is missing but the session can install a disposable JDK 17
   and the Android SDK components this file requires, install them and run the
   narrowest local command that can falsify the current change.
3. After a concrete capability blocker (failed probe plus failed
   install/download), escalate only that blocked operation. The hosted fallback
   for Android/JVM implementation feedback is a `workflow_dispatch` of
   `.github/workflows/diagnose.yml` with the narrowest mode that answers the
   question: `app-unit`, `samsung-unit`, `android-static`, `android-build`, `dependency-state`, or
   `device`. Use `dependency-state` only after a reviewed Gradle dependency/configuration
   change requires `resolveAndLockAll --write-locks`; it uploads bounded generated lockfiles
   and never writes the target branch. A diagnostic run is implementation feedback only. It is not terminal
   repository verification and does not satisfy `verify / gate` for a finished
   candidate. Do not dispatch a full `verify` run merely to discover the next
   compile, format, or unit-test error.
4. Temporary branch-scoped workflow YAML is last-resort, only when a live
   capability probe shows no narrower route exists (local tooling or an existing
   `diagnose.yml` mode). Do not add per-slice workflow files as slice
   infrastructure: add a mode to `diagnose.yml` instead.

The backend package, project-state/control-plane validators, the secret scanner,
yamllint, markdownlint, and ShellCheck run locally when those tools are present:

- `python3 .project-ai/skills/validate.py`
- `python3 tools/ci/test/project-state-contract.test.py`
- `python3 tools/ci/project_state_contract.py`
- `npm ci --prefix backend`
- `npm run verify --prefix backend`
- `node --test "tools/secret-scan/test/secret-scan.test.mjs" && node tools/secret-scan/secret-scan.mjs`
- `node --test "tools/ci/test/purge-actions.test.mjs"`
- `node --test "tools/ci/test/dispatch-workflow.test.mjs"`
- `yamllint -c .yamllint.yml .`
- `tools/security/run.sh`

Installed-app acceptance executes on GitHub Actions via Gradle Managed Devices
(API 29) with KVM acceleration.

## Verification cadence

Pull-request synchronization is intentionally cheap. `.github/workflows/verify.yml`
runs only the two cheap repository domains on ordinary PR updates — repository
policy (whitespace/diff validation, repository security-policy self-tests, secret
scanning, tooling constraint checks) and repository quality (yamllint,
markdownlint, ShellCheck, shfmt, actionlint, zizmor) — plus change detection and
dependency review when the candidate changed dependency inputs. It does not build
Android, run backend verification, start a managed device, run SonarQube,
regenerate dependency state, or execute `ciCheck` or any of its narrower domains.

The gate requires change detection to have succeeded in its own right, because
`dependency-review` declares `needs: changes`: if change detection fails, GitHub
skips dependency review, and a gate that accepted that skip would let the failure
pass silently.

The full verification domains run on pushes to `main` and on an explicit
**Run workflow** dispatch of `verify` on the branch that needs the complete
suite. There is no `mode` input: a dispatched run is the full suite. Focused,
non-terminal feedback is a `diagnose.yml` dispatch, never a `verify` dispatch.

Do not regenerate Gradle locks or verification metadata as an iteration step.
Regenerate them only after an actual reviewed dependency change requires it.

## CI topology and agent invocation

Five active workflows, each with one responsibility:

| Workflow | Trigger | Responsibility |
|---|---|---|
| `verify.yml` | `pull_request`, `push` to `main`, `workflow_dispatch` | Authoritative verification and the sole stable `verify / gate` |
| `diagnose.yml` | `workflow_dispatch` only | Focused, permanent, non-terminal diagnostics |
| `codeql.yml` | `pull_request`, `push` to `main`, `schedule`, `workflow_dispatch` | Security SAST |
| `maintenance.yml` | `workflow_dispatch` only, confirmed with `PURGE` | Manual destructive Actions maintenance |
| `agent-control.yml` | `pull_request_target` (label added) | Trusted dispatch bridge for agents |

Repository-local composite actions live under `.github/actions/**`
(`setup-node`, `setup-jvm`, `assert-dispatch-target`). Runner filesystem state is never shared between
jobs, so each job re-establishes its own toolchain; the Gradle version is not
duplicated in workflow configuration because the committed wrapper plus its
`distributionSha256Sum` is the version authority.

GitHub resolves a local action (`uses: ./.github/actions/...`) from the
workspace on disk, so **every job that uses a composite action must run
`actions/checkout` before it**. A checkout inside the composite would run too
late to be found — this is why the composites own toolchain setup only and each
job checks out explicitly first.

Invoking diagnostics without clicking through the Actions UI:

- an actor with Actions write permission dispatches `diagnose.yml` directly;
- an integration that can only mutate pull-request metadata adds one of the
  command labels (`ci:app-unit`, `ci:samsung-unit`, `ci:android-static`,
  `ci:android-build`, `ci:backend`, `ci:backend-static`, `ci:backend-test`,
  `ci:device`, `ci:full`) to the pull request, and `agent-control.yml` resolves
  that pull request's head SHA and branch, dispatches the matching workflow from
  the repository's default branch, removes the label, and records what it
  dispatched.

To narrow a diagnostic through that metadata-only bridge, put exactly one
mode-bound marker on a line by itself in the pull-request body before applying
the matching command label:

`<!-- appt-ci-focus app-unit: PairingToFirstControlFlowTest -->`

The marker is optional and does not change ordinary label behavior: no matching
marker runs the whole mode, and a marker for another mode is ignored. Duplicate
matching markers fail closed. The PR body is untrusted data; the bridge passes
the selected value only as the `focus` workflow input, and the trusted
`diagnose-focus.mjs` resolver validates it before dispatch and again before
target code is checked out. `ci:full` ignores focus markers.

The bridge dispatches the **default branch**, not the pull request's head branch,
and that is the load-bearing part of the trust model. GitHub runs a workflow as
its dispatch ref defines it, so dispatching the head branch would let the pull
request supply the workflow YAML and the local composite action that asserts the
dispatch target — a pull request could rewrite its own assertion and pass it.
Dispatching the default branch keeps the workflow and its validation logic
trusted.

GitHub's Create Workflow Dispatch endpoint also requires `ref` to be a branch or
tag name and rejects a commit SHA, so the requested target travels as inputs
instead: `target_ref` is the pull request's head branch and `expected_sha` is the
exact commit it resolved to. Every job in `verify` and `diagnose` then, in order:

1. checks out the dispatch anchor, which for a bridge dispatch is the default
   branch and therefore trusted;
2. asserts the dispatch target with `.github/actions/assert-dispatch-target`,
   which resolves `target_ref` through the API, requires it still to point at
   `expected_sha`, and requires that commit to be the head of an **open** pull
   request — hard-failing with both SHAs named if the branch moved, and refusing
   an arbitrary commit that no open pull request points at;
3. sets up its toolchain and resolves any diagnostic `focus`, still from the
   trusted anchor;
4. checks out the commit the assertion proved, published as its `sha` output,
   rather than the raw input.

From step 4 the code under verification is pull-request-controlled, executed by a
trusted workflow under read-only repository-token permissions. Both `verify.yml`
and `diagnose.yml` additionally declare workflow-level `cache-mode: read`: GitHub
scopes the cache token so target code can restore, but cannot save caches into
the default branch's scope. Repository-token permissions alone do not enforce
this boundary. This applies to every job and event in those two workflows;
provider cache writes are intentionally unavailable even for trusted-only runs.

For bridge-dispatched **Gradle-backed diagnostics only**, `diagnose.yml` may
reuse the previous run's local Gradle build cache under a PR-scoped provider-cache
key derived from the **validated pull-request number** published by
`assert-dispatch-target`. Jobs that execute PR code remain under workflow-level
`cache-mode: read`: they may restore the matching `appt-pr-gradle-<pr>-`
prefix, but they cannot save provider caches.

After the exact validated PR head runs, the diagnostic packages only
`~/.gradle/caches/build-cache-1` into a one-day workflow artifact. A separate
trusted `publish-gradle-warm-state` job then downloads that current-run artifact
by its action-produced artifact ID, validates path shape, entry types, expanded
size and file count, stages only `build-cache-1`, and saves it under the
PR-number namespace. That publisher is the repository's sole narrow exception:
it is `cache-mode: write-only`, never checks out PR code, never invokes
repository-local actions, receives no repository secrets, and cannot read provider
caches. The parsed-YAML cache contract proves that structure explicitly.

Missing, invalid, or oversize warm state degrades to the ordinary read-only
diagnostic path. Red diagnostics can still publish bounded task outputs for the
next fix/retest. `verify.yml`, CodeQL, and release evidence never receive the
publisher's write capability, so acceptance evidence remains independent of
PR-local warm state.

Fork pull requests cannot be dispatched at all and fail closed. The logic lives in `tools/ci/dispatch-workflow.mjs`, proven by
`tools/ci/test/dispatch-workflow.test.mjs`; the tests reject a commit SHA used
directly as `workflow_dispatch.ref` and reject the earlier mutable-ref shape
outright, because `targetRef` is required and `ref` may never equal it. The
assertion's own decision logic is proven by
`tools/ci/test/assert-dispatch-target.test.mjs`, which extracts it from the
action and runs it against a fake `gh`.

The parsed-YAML cache contract runs in the `repo-quality` job after yamllint:

```sh
python3 tools/ci/test/workflow-cache-contract.test.py
python3 tools/ci/test/sonar-boundary.test.py
```

It checks effective workflow/job modes and rejects missing boundaries or
write-capable job overrides, including flow mappings and aliases. PyYAML comes
from the pinned yamllint install. The pinned actionlint parser does not yet recognize provider `cache-mode`
syntax at either the workflow or job level; only those two exact unknown-key
diagnostics are excluded, with syntax and effective access validated by this
mandatory parsed-YAML contract. All other actionlint findings remain failures.
Remove that narrow compatibility exception when the pinned parser supports both
provider placements.

### Secret-bearing Sonar analysis

The quality-platform job does **not** execute target build/install scripts. It
keeps its trusted dispatch-anchor checkout at the workspace root, checks the
validated target into `sonar-target`, and downloads coverage into separate
runner-temporary directories. The scanner reads the anchor's
`sonar-project.properties` through an explicit `project.settings` argument.
The scanner stays at the trusted anchor (no target `projectBaseDir` input);
explicit `sonar.sources`, `sonar.tests` and `sonar.java.binaries` overrides select
only `sonar-target/...` paths. Entire `-Dkey=value` arguments are quoted because
the pinned action's parser preserves quotes placed only around a value.
Target and artifact copies are not scanner configuration. Module settings are
disabled, endpoints are fixed, and scanner state starts in fresh directories
outside all input trees. Input symlinks and special files fail closed before any
secret-bearing step. Both Kover XML and backend LCOV remain required and are
imported from the current run's producer artifacts through explicit paths.

Only the token-presence check and pinned scanner receive `SONAR_TOKEN`; the
scanner no longer receives an unnecessary `GITHUB_TOKEN`. The scanner action and
CLI version stay pinned. The parsed-YAML and inert-file regression tests above
check this split without running target code or contacting Sonar. See the
[release security analysis](architecture/release.md#secret-bearing-sonar-boundary)
for the pinned upstream source evidence and the remaining hosted-proof boundary.

### Diagnostic breadth and failure capture

AppT diagnostics optimize for **maximum useful evidence inside the smallest relevant
failure domain**. A first failure confirms that the domain is red; it is not
automatically the whole failure set.

For the Gradle-backed `app-unit`, `samsung-unit`, `android-static`, and
`android-build` modes, `tools/ci/diagnose-focus.mjs` includes Gradle
`--continue` in the reviewed argv. Gradle documents that this continues
independent requested tasks after a task failure, while tasks that depend on a
failed prerequisite are not executed. It therefore expands evidence where the
task graph allows it; it cannot make a blocked dependent task runnable or turn
the build green. See the first-party Gradle task-failure behavior:
<https://docs.gradle.org/current/userguide/custom_tasks.html>.

Gradle JVM `Test` tasks are non-fail-fast by default and execute all detected
tests before reporting the task failure. AppT does not enable `failFast` on
these diagnostic test tasks. See:
<https://docs.gradle.org/current/userguide/java_testing.html>.

The backend Jest configuration leaves `bail` unset; Jest's default is `0`, so
it runs the selected tests and reports their failures rather than stopping after
the first one. See:
<https://jestjs.io/docs/configuration#bail-number--boolean>.

Use those native behaviors deliberately:

1. prove the reported symptom with the narrowest useful reproducer;
2. run the whole affected diagnostic mode when multiple independent failures
   may exist;
3. inspect the normal logs **and** the structured reports/artifacts before
   editing — `diagnose.yml` uploads app and Samsung JVM test reports even when
   their test task fails;
4. group the observed failures by root cause and separate implementation
   defects, test defects/stale expectations, infrastructure failures, and
   contract exceptions;
5. correct coherent root causes, then rerun the whole affected diagnostic
   domain so a newly exposed failure is evidence, not another blind CI cycle.

Continuation is diagnostic breadth, not failure tolerance. Do not set
`ignoreFailures`, suppress compiler/linter failures, weaken `-Werror`, lower
assertions, or otherwise make a red diagnostic appear green.

If native logs and reports cannot expose enough evidence, add the smallest
targeted temporary diagnostic instrumentation that can: a listener, assertion,
trace, stack output, or report hook. Keep it branch-scoped, mark it temporary,
and remove it before terminal verification unless it has earned a permanent
observability/testing role. Temporary workflow YAML remains the last resort
described above, after local tooling and the existing `diagnose.yml` modes
cannot perform the needed diagnostic operation.

### Focusing a diagnostic

Every `diagnose.yml` mode accepts an optional `focus` input that narrows that
mode without widening it:

```bash
# the whole mode, unchanged
gh workflow run diagnose.yml -f mode=app-unit

# narrowed inside the mode
gh workflow run diagnose.yml -f mode=app-unit -f focus=com.example.FooTest
gh workflow run diagnose.yml -f mode=android-static -f focus=lint
gh workflow run diagnose.yml -f mode=backend-test -f focus=name:handles a retry
gh workflow run diagnose.yml -f mode=device -f focus=class:dev.anthracite.appt.SmokeTest
```

`tools/ci/diagnose-focus.mjs` owns the per-mode grammar and returns the exact
argv vector to run, so the value is data rather than command text. An empty
`focus` reproduces the mode's existing command exactly; a non-empty one narrows
only within the mode, through the underlying tool's native selector or a finite
allowlist of sub-responsibilities the mode already owns. Anything else — a wrong
shape, a foreign prefix, a sub-responsibility belonging to another mode, a
leading `-`, a control character — fails closed before anything runs, and the run
summary records the mode and the effective focus. The grammar and its selectors
are proven by `tools/ci/test/diagnose-focus.test.mjs` against the pinned
toolchain. A focused diagnostic is still non-terminal; full `verify` is
unchanged.

The maintenance purge deletes every Actions cache and every workflow run except
its own. GitHub has no "delete every cache" endpoint — `DELETE .../actions/caches`
requires a `key` — so the purge lists the caches and deletes each one by cache
ID. Its logic is `tools/ci/purge-actions.mjs`, proven by
`tools/ci/test/purge-actions.test.mjs` against a fake API that refuses the
invalid bare deletion.

The purge is serialized repository-wide (not per ref), because a manual dispatch
can target any ref and two concurrent purges would cancel each other. Its dry
run reads the repository's real state and suppresses only mutations, so the
preview reports the actual number of runs and caches:

```bash
node tools/ci/purge-actions.mjs --dry-run
```

`--settle-timeout-minutes` and `--poll-interval-seconds` are forwarded into the
purge loop, so the workflow genuinely controls the timing it declares. Invalid
or non-positive values are rejected rather than silently becoming `NaN`.

The final pre-deletion run listing is re-read and treated exactly like the first:
the cache phase takes real time, and a run queued during it is still active when
that listing is taken. GitHub refuses to delete a run that has not finished, so
any newly active non-current run is cancelled and settled under the same bounded
rules before anything is deleted. A run that never settles fails the purge closed
instead of being reported as a clean end state.

## CodeQL static analysis

CodeQL static analysis keeps ordinary pull-request synchronization build-free:
PR updates scan GitHub Actions and JavaScript/TypeScript only. Java/Kotlin CodeQL
runs when explicitly dispatched, on pushes to `main`, and on the scheduled
security run.

Because GitHub-managed Default Setup uses CodeQL bundle 2.27.0 which does not
support Kotlin 2.4.20 (supported starting in CodeQL CLI / bundle 2.27.1), AppT
temporarily uses an Advanced Setup workflow pinned to CodeQL Action `v4.38.2`
and bundle `2.27.1`. The workflow analyzes `java-kotlin` (built manually under
strict dependency verification via `./gradlew ... assembleDebug :macrobenchmark:assembleBenchmark`),
`javascript-typescript`, and `actions` using the `security-extended` query suite.

Local reproduction of the deterministic Kotlin extraction build:

```bash
tools/security/run.sh build
```

Migration-back condition: once GitHub-managed Default Setup bundles advance to
CodeQL 2.27.1 or higher and Default Setup analysis passes on Kotlin 2.4.20,
repository owners can re-enable Default Setup in repository settings and delete
`.github/workflows/codeql.yml`.
