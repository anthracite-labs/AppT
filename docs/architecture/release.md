# CI, release, environments, and supply chain

The accepted baseline is GitHub Actions, Gradle, Android App Bundle, Play App Signing, internal testing of the production candidate, then staged production. This file makes that implementable and adds the environment separation the account/licensing architecture needs. Workflow files themselves are created by slices, not by the architecture map.

## Gradle

- Version catalog `gradle/libs.versions.toml`. Exact versions are pinned when the skeleton slice runs. This map names the set and does not freeze today's numbers into prose, where they would go stale.
- No `+` ranges. No snapshot dependencies. No dynamic versions.
- `dependencyResolutionManagement.repositoriesMode = FAIL_ON_PROJECT_REPOS`.
- Repositories: `google()` and `mavenCentral()`. Plugin portal only under `pluginManagement`.
- No JitPack and no ad-hoc Maven URLs in V1.
- Dependency locking enabled. Lockfiles committed.
- Dependency verification metadata committed. CI uses strict verification.
- Updates arrive as reviewed pull requests. Silent upgrades are not allowed.

### Dependency set

Runtime: Kotlin, Android Gradle Plugin, Compose BOM, Navigation Compose, kotlinx-serialization, Coroutines, Lifecycle, Hilt, Room, DataStore, OkHttp, WorkManager, Firebase BOM artifacts `firebase-auth` and `firebase-appcheck-playintegrity`, Play Billing, Play Integrity, Credential Manager.

Test: JUnit, coroutines-test, JVM/Compose UI tests, Robolectric for Android-facing state/DataStore/migrations, Firebase emulator suite for backend functions, plus the single stock-debug Engineering Verifier for all S06–S17 device-side evidence. No S06–S17 acceptance gate requires Android instrumentation, connected tests, an emulator, adb, or a hosted device runner.

Backend toolchain: TypeScript on the Cloud Functions 2nd gen Node.js LTS runtime, owned by `backend/`, with `npm` and a committed `package-lock.json` (`npm ci --prefix backend` in CI), ESLint and Prettier configuration in the same directory, and Jest plus `firebase-functions-test` for the unit and emulator tests described in [testing.md](testing.md). The backend is not a Gradle module and never enters the Android dependency graph; the only contract between the two is the HTTPS API in [account-entitlement.md](account-entitlement.md).

**Backend commands are always package-prefixed and run from the repository root**, so no documented command depends on the caller's working directory: `npm ci --prefix backend`, `npm run typecheck --prefix backend`, `npm run lint --prefix backend`, `npm test --prefix backend`, and `npm run test:emulator --prefix backend` (which starts the Firestore and Functions emulators from `backend/firebase.json` and runs the emulator suite). The Firebase CLI is a `backend/` devDependency, so emulator and deploy runs go through that package rather than a globally installed CLI, and CI calls `npm run deploy --prefix backend` with an explicit `--project` per environment. The script names are owned by `backend/package.json`; [account-entitlement.md](account-entitlement.md#backend-source-architecture) records the same set.

Not in the graph: `firebase-firestore` (server-only; the client never talks to Firestore directly), `firebase-analytics`, `firebase-crashlytics` or any other crash reporter, advertising and attribution SDKs, ACRA, Whisperlink, Cast, Consumer IR, and any Samsung reference library. Protocol code is written in this repository.

`samsung` stays free of the Firebase BOM, Play Billing, and Play Integrity. The dependency boundary itself is owned by [modules.md](modules.md#dependency-set-boundaries) and enforced by the CI checks in that file.

## Environments

Three environments, three isolated Google Cloud and Firebase projects, one Play app.

| Concern | `dev` | `internal` | `production` |
|---|---|---|---|
| Purpose | Local development, emulator suite, unit and function tests | Tester builds and end-to-end checks on real devices against internal infrastructure | Customer builds and real purchases; also hosts the release candidate on the Play internal testing track |
| Firebase / Google Cloud project | `appt-dev` | `appt-internal` | `appt-prod` |
| Firebase Authentication | Emulator plus dev project providers | Internal project providers | Production providers, hardened settings |
| Firestore instance | Emulator plus dev project | Internal project | Production project, daily backups enabled |
| Entitlement service | Emulator plus dev functions | Internal functions | Production functions |
| Marker and fingerprint keys (Secret Manager) | Development key versions | Internal key versions | Production key versions, separate rotation schedule |
| Proof signing key (Cloud KMS) | Development key | Internal key | Production key, non-exportable, distinct key set |
| Signing identity | Debug keystore, generated per machine | Internal signing key, a distinct certificate registered only in this project | Play App Signing: the app-signing key is held by Google, CI holds only the upload key |
| App Check | Debug provider with registered debug tokens | Play Integrity provider registered for an app distributed **exclusively outside Google Play**, enforcement on | Play Integrity provider registered for an app distributed **on Google Play**, enforcement on |
| Play Billing | Fake adapter; no real billing | No real billing from the outside-Play tester build, which Play cannot sell to; the tester build runs the purchase path against the fake billing adapter, and licence-testers purchase on the Play-distributed candidate | Play production |
| Play Integrity | Fake verdicts | Real verdicts for the internal signing certificate; the Play Integrity API is linked to this project | Real verdicts for the Play app-signing certificate; the Play Integrity API is linked to this project |
| Diagnostics | Local only; the diagnostics path has no network client and no environment-specific endpoint | Same | Same |
| Backend URL resolved by app | `dev` | `internal` | `production` |

Isolation rules:

- A debug build can never resolve production identifiers, and a release build can never resolve development identifiers. A CI check fails if either is possible.
- Production credentials never appear in the repository. CI authenticates to Google Cloud with Workload Identity Federation; repository secrets hold no long-lived Google key.
- Each environment has its own service accounts with least privilege: the verification function holds only the Play publisher scope it needs, and the notification handler can read and write only its own collections.
- Development and internal environments may use narrower integrity enforcement so engineers are not blocked; production enforcement is not weakened.
- Certificate registration is per environment and is never shared: the internal signing certificate is registered only in `appt-internal`, only the Play app-signing certificate authenticates production clients, and the debug certificate reaches neither real environment. A CI check fails if a registration fingerprint recorded for one environment appears in another.

### Play and RTDN are shared by design

The Play app and its product catalogue are single: there is no separate Play product set for development, internal testing, or production.

- One Play app means one package name and one product id for the lifetime unlock everywhere.
- Real-time developer notifications are configured once per Play app against one Pub/Sub topic, so one-time product and voided purchase events land in the production notification pipeline regardless of the track that generated them. If multiple backend consumers are ever needed, fan-out uses multiple Pub/Sub subscriptions on that topic rather than assuming multiple Play RTDN topics.
- The Android application id is therefore the same across environments. Environment separation happens in Firebase/Cloud project, backend URL, and signing configuration, not in the Play product.
- Licence-test purchases are identified through `ProductPurchaseV2.testPurchaseContext` and never grant a durable production Lifetime Entitlement, so an internal tester cannot accidentally create a paid production account.
- A test purchase made through a licence-tester account still produces real Play records. The pipeline treats them as verification evidence with a test verdict and **never** grants a durable Lifetime Entitlement from it, in any environment, which is exactly the case `testPurchaseDoesNotGrantLifetime` covers.
- Because test purchases are withheld everywhere, the paid success path is proven in three places: backend tests with a fake Play verifier that returns a standard purchase; a real licence-test purchase run on the Play internal testing track that asserts the withholding contract; and one recorded real purchase before public promotion, refunded afterwards, which exercises grant, acknowledgement, RTDN void, revocation, and restore on the live production path. The release checklist carries that drill.

V1 deliberately configures no promo or rewarded acquisition path. If either is introduced later, its current `ProductPurchaseV2` representation must be validated before the entitlement verifier can accept that acquisition mode; that future gate is listed in [README.md](README.md#needs-validation).

## Backend deployment and rollback

| Aspect | Approach |
|---|---|
| Deploy mechanism | GitHub Actions deploys functions, the scheduled reconciliation function, Firestore rules, and indexes with Workload Identity Federation. Manual dispatch for production, automatic or manual for `dev` and `internal` |
| Artifact identity | A deployment records the commit SHA and function revision; production deploys are tagged |
| Rollout | Cloud Functions revisions with traffic split. A new revision starts at 0% traffic in production, then moves after the smoke checks pass |
| Rollback | Shift traffic back to the previous revision; no code change and no data migration required. If a rollback still needs a data fix, the two-step migration below supplies it |
| Rules and indexes | Deployed as versioned artifacts in the same pipeline as the code that depends on them |
| Schema changes | Additive first: add the field, deploy readers, backfill, then stop writing the old field. A destructive change is a separate, later deployment |
| Secrets | Secret Manager versions; a rotation adds a version and retires the old one only after the new one is proven |
| Backups | Firestore scheduled backups for the production instance, with a documented restore drill |
| Observability | Function error rate, verification failures, revocation events, and stuck deletions are alerting signals: an account that has been `deleting` for more than seven days, or a frozen binding whose `deletionId` names no `deleting` account. No behavioral analytics, no per-user event stream |
| Incident response | Revocation state can be corrected by an audited support action; an outage degrades to cached-proof behavior by design, not to broken remotes |

## Verification architecture

The accepted verification design has one owner per concern. GitHub-native controls
own repository-host security and policy; Gradle owns Android build/static
verification; the backend package owns TypeScript verification; repository-owned
guards own AppT-specific privacy and module invariants. A tool is added only when
it owns a concern that no existing control owns.

The live repository workflows are `.github/workflows/verify.yml`,
`.github/workflows/diagnose.yml`, `.github/workflows/codeql.yml`,
`.github/workflows/maintenance.yml`, and `.github/workflows/agent-control.yml`.
Historical `ci.yml` is retired; its GitHub Actions runs remain archival
evidence, not an active baseline. This section describes the current
verification topology.

Issue #56 landed that topology on `main`; Issue #88 replaced its monolithic
Android job with parallel failure domains, moved focused feedback into
`diagnose.yml`, and added the agent-control bridge. Issue #88 also introduced
manual Actions maintenance; that workflow was later retired during simplification
and has now been reintroduced as the permanent manual-only `maintenance.yml`
owner for two bounded operations: aggressive repository comment maintenance
through reviewable patches/PRs, and explicitly confirmed Actions run/cache purge.
It is not a verification or diagnostic workflow. Do not recreate deleted one-shot
or legacy workflow YAML, and do not add per-slice diagnostic workflow files:
focused diagnostics are permanent and live in `diagnose.yml`.

### GitHub-owned controls

Repository settings, not workflow YAML, own:

- the default-branch ruleset: pull requests required, deletion and force pushes
  blocked, no bypass actors;
- the Actions policy: repository default `GITHUB_TOKEN` is read-only and every
  external Action reference is a full immutable commit SHA;
- CodeQL analysis for Java/Kotlin, JavaScript/TypeScript and GitHub Actions,
  using the `security-extended` query suite. GitHub-managed Default Setup is the
  preferred owner but cannot be toggled by the control-plane token (the
  `code-scanning/default-setup` API returns 403 for this repository), so
  `.github/workflows/codeql.yml` carries an Advanced Setup until a repository
  owner enables Default Setup and it proves Kotlin analysis on the current
  Kotlin version;
  ordinary pull-request synchronization runs only the build-free GitHub Actions
  and JavaScript/TypeScript analyses; Java/Kotlin CodeQL is human-triggered during
  implementation and still runs on `main` and the scheduled security pass;
- secret scanning and push protection;
- the dependency graph, Dependabot alerts, security updates and grouped
  version-update proposals. Automatic dependency submission is not part of
  ordinary feature-branch iteration because it launches a Gradle build on
  repository pushes; repository owners keep it disabled during rapid coding
  loops and enable or run dependency submission only when its transitive graph
  evidence is explicitly needed.

Repository workflow code must not duplicate those platform controls.

### Repository verification workflow

The repository owns one authoritative verification workflow:
`.github/workflows/verify.yml`. Pull-request synchronization runs only the two cheap repository domains —
repository policy and repository quality — plus dependency review when the
candidate changed dependency inputs. It does not compile Android, run backend
verification, run the Sonar quality platform, or execute `ciCheck` or any of its
narrower domains.

The full verification domains run on pushes to `main` and on an explicit
`workflow_dispatch` (there is no `mode` input; a dispatched run is the full
suite). This is the accepted response to measured Actions cost and
implementation latency: coding iterations stay cheap, while the human or an
authorized control-plane integration chooses the point at which the long
verification bill is paid.

Focused, permanent, non-terminal feedback lives in `.github/workflows/diagnose.yml`
as `workflow_dispatch` modes: `app-unit`, `samsung-unit`, `android-static`,
`android-build`, and `backend`. A diagnostic run is implementation feedback
only. It never produces `verify / gate`,
and it must not be read as terminal repository verification. It replaced the
retired `samsung-targeted` dispatch mode of `verify.yml`, which no longer
exists.

The workflow exposes one stable branch-protection interface: `verify / gate`
(workflow `verify`, job `gate`). On ordinary pull requests that gate represents
the two cheap repository domains and dependency review; a full run produces the
same gate after Android, backend, dependency-review and Sonar quality-platform
evidence. Internal job names may evolve
without changing branch protection. No diagnostic run can satisfy or masquerade
as that gate.

The workflow runs these failure domains as independent parallel jobs, so one
failing domain cannot hide the evidence of another. Each job re-establishes its
own toolchain through the repository-local composite actions under
`.github/actions/**` (`setup-node`, `setup-jvm`); because GitHub resolves a
local action from the workspace on disk, every job that uses one runs
`actions/checkout` before it, and the composites own toolchain setup only:

1. **repo-policy** — diff/whitespace validation, repository security-policy
   self-tests, secret scanning, and tooling-constraint checks. No language
   build.
2. **repo-quality** — repository-generic checks that do not belong to a language
   build: `actionlint` for workflow correctness, `zizmor` for GitHub Actions
   security posture, ShellCheck plus `shfmt` for authored shell, `yamllint`
   for generic YAML, `markdownlint` for canonical Markdown. These tools run
   directly or through package-native entrypoints rather than through
   MegaLinter or Super-Linter. This domain deliberately does not repeat the
   policy self-tests above.
3. **android-format** — the single Android formatting responsibility:
   deterministic Kotlin formatting through Spotless + ktfmt, exposed as the root
   `androidFormat` task. Spotless and ktfmt are one check, never two.
4. **android-static** — the root `androidStatic` task: Android Lint, detekt and
   `appTGuards`, under strict dependency verification and Gradle `--continue`
   so Lint, detekt and the guard floor all report in one run.
5. **android-build** — the root `androidBuild` task: AppT debug assembly plus
   the merged-manifest, release-boundary and `AD_ID` guards.
6. **android-unit** — the root `androidUnit` task: `:app:testDebugUnitTest` and
   `:samsung:test`. Samsung unit tests are authoritative verification evidence,
   not diagnostics, so a full run always produces them.
7. **backend** — `npm run verify --prefix backend`, the whole backend
   verification interface: strict TypeScript typechecking, typed ESLint,
   Prettier checking, Knip dead-code/dependency analysis, then Jest with LCOV
   coverage. The backend is a small, non-deployable skeleton, so one job owns
   it; if it grows into a deployable service this job may be split along real
   failure domains.
8. **changes** — change detection over the GitHub compare API, so a
    conditional job below never leaves a required check pending the way a
    top-level `paths:` filter would.
9. **dependency-review** — GitHub Dependency Review on pull requests, and on
    full runs when the candidate changed dependency inputs. It consumes the
    GitHub dependency graph, fails on newly introduced vulnerable packages, and
    complements committed dependency locking and strict verification.
10. **quality-platform** — SonarQube Cloud analysis of the Android and backend
    code, consuming current-run Kover XML and Jest LCOV reports. It owns the
    cross-language new-code coverage, duplication, maintainability and
    reliability quality gate that Android Lint, detekt, CodeQL and tests do not
    provide. It runs only on full verification, after the Android and backend
    producers. Historical evidence confirms it is active: main verification run
    `36881699523` (2026-10-01) completed the Sonar scan successfully, and the
    gate required `quality-platform` success.
11. **gate** — depends on every domain above and succeeds only when all required
    domains succeeded. This is the sole stable repository-owned status intended
    for default-branch protection. It requires `changes` to have succeeded in its
    own right: `dependency-review` declares `needs: changes`, so a failed
    change-detection job makes GitHub skip `dependency-review`, and a gate that
    merely accepted a skipped dependency review would let that failure fail
    open. `skipped` is accepted for `dependency-review` only once `changes` has
    succeeded.

`ciCheck` remains the local umbrella over the four Android domains
(`androidFormat`, `androidStatic`, `androidBuild`, `androidUnit`) so a developer
keeps one convenient aggregate command. CI does not use it, because one
monolithic invocation cannot report a formatting failure and a unit-test failure
independently.

The Android verification floor preserved behind those domains includes:

- Kotlin compiler extra diagnostics with warnings treated as errors, without a
  blanket suppression/baseline used merely to make CI green;
- deterministic Spotless + ktfmt checking;
- assembly and unit/Robolectric tests;
- Android Lint and detekt;
- Kover XML product-coverage reports for Sonar's new-code coverage quality gate;
  no percentage floor is applied to build/CI implementation;
- architecture tests only for concrete source/bytecode laws that are not already
  owned by Gradle dependency guards; do not install an empty architecture
  framework merely to claim coverage;
- committed dependency lock state and strict dependency verification;
- the manifest permission allowlist and `AD_ID` prohibition;
- no telemetry/crash-reporting/advertising/attribution artifact (`noTelemetryDependency`
  on `:app`; the crash-reporting vocabulary is part of this guard, and the
  former `noCrashReportingInApp` task was removed as a duplicate resolution of
  the same graph);
- no Firestore client in `:app`;
- the `:samsung` dependency boundary and no direct `android.util.Log` (the
  boundary also owns `:samsung`'s telemetry question, which is why the `:samsung`
  instance of `noTelemetryDependency` was removed);
- no removed television-sync record or mutation-queue model in production code;
- the version-catalog pin policy;
- all other accepted `appTGuards` invariants.

The backend verification floor remains package-owned and reproducible from the
repository root. `package-lock.json` is committed and `npm ci` is the only
CI install mode. The package emits Jest LCOV coverage and treats Knip findings
as dead-code/dependency failures rather than allowing agent-generated residue to
accumulate.

The package exposes that floor as one interface — `npm run verify --prefix
backend` (typecheck, ESLint, Prettier, Knip, then Jest) — used by both CI and
local use; `verify:static` and `verify:test` remain available as narrower local
entrypoints. Firebase emulator tests join the backend verification surface when
the backend slice makes them real.

### Checks that deliberately stay separate

CodeQL remains the authoritative security SAST owner; generic Semgrep or another
general SAST engine is not added without a concrete AppT invariant CodeQL and the
project-native tools cannot express.

Sonar remains because the full verification gate actually consumes its distinct
cross-language new-code coverage, duplication, maintainability and reliability
quality gate; those signals are not supplied by Android Lint, detekt, CodeQL or
ordinary tests. This is supported by a successful main-run scan, not merely by
a configuration file. The Sonar job does not execute pull-request-controlled
build/install scripts: it checks out the exact candidate as data, keeps trusted
scanner configuration at the anchor, validates inputs before exposing
`SONAR_TOKEN`, and uses a pinned scanner. The detailed operational trust boundary
and its regression test are documented in `docs/BUILD.md` and
`tools/ci/test/sonar-boundary.test.py`.

GitHub secret protection owns provider/generic secret detection, and the
repository must not duplicate it. `tools/secret-scan/secret-scan.mjs` therefore
keeps only the AppT-specific credential-file and credential-material guards:
forbidden extensions and basenames (keystores, certificates, keys,
`google-services.json`, service-account files), private-key blocks, private-key
assignments, service-account key documents, Firebase Cloud Messaging server
keys, JSON Web Tokens, and hardcoded credential assignments. Provider token
formats — Google API keys, AWS access key ids, GitHub tokens, Slack tokens,
Stripe secret keys — are owned by GitHub Secret Scanning and Push Protection and
are deliberately not detected here; `PROVIDER_OWNED_SAMPLES` in that file and its
tests hold that boundary in place. A provider key written as a hardcoded
assignment is still caught, by the assignment guard rather than by provider
detection.

Android Lint, detekt, Kover, ESLint, Prettier, Knip and Jest remain native to
their project toolchains. Spotless + ktfmt is one formatting responsibility, not
two checks: it is exposed once as the `android-format` domain and the root
`androidFormat` task, and is never split into separate formatting jobs.
actionlint, zizmor, ShellCheck, shfmt, yamllint and markdownlint remain narrow
repository specialists. None is re-hosted through MegaLinter or Super-Linter.

AppT does not maintain a repository-owned Android instrumentation, benchmark/GMD,
or emulator-based E2E pipeline for S06–S17. Device-side acceptance is attached to
the slice that owns the behavior: focused executable tests prove structural
properties, and the one stock-debug Engineering Verifier records exact-device
evidence when physical/device behavior is load-bearing. Protocol fuzz/property
tests, Firebase emulator backend integration, and physical Samsung acceptance are
added only when their corresponding implementation surfaces exist.

Mutation testing is a later deep-verification concern, not a ceremonial PR gate.
When enough non-trivial pure business/protocol logic exists to produce a useful
mutation score, `deep.yml` may add a reviewed mutation-testing owner (for
example StrykerJS for backend logic). Do not create mutation infrastructure over
a skeleton merely to report an impressive empty score.

### Stock-debug Engineering Verifier release boundary

The Engineering Verifier is compiled only into the stock debug source set and is the sole S06–S17 device-evidence surface. It may reuse production app/session code and debug-only scripted adapters, but it is never a distributable product feature.

The release boundary must prove that internal/production distributable artifacts contain no verifier route, entry point, scenario/controller/report type, verifier copy, debug adapter selector, or verifier evidence file. The accepted S05 release-exclusion guard is expanded into this general Engineering Verifier guard rather than duplicated per slice.

The trusted `android-build` diagnostic remains the delivery route for the exact-head debug APK used by the human/device verifier. Release/internal artifact construction, signing, Play upload, provider deployment, and production purchase drills remain separate release/provider operations; the verifier does not receive those credentials and cannot self-approve them.

### Agent-authored change assurance

Code produced by ChatGPT, Arena, another coding agent, or a human is held to the
same repository evidence. Agent authorship never lowers a gate and never counts
as evidence that the implementation is correct. Sonar's deterministic
quality-platform result is one required full-run evidence source; it does not
replace human review or CodeQL security analysis.

CodeRabbit is an independent PR-review layer, configured in version-controlled
`.coderabbit.yaml`. Automatic reviews, linked-issue assessment and focused
pre-merge checks evaluate whether the diff satisfies its Issue, stays in scope,
preserves verification/security controls and avoids placeholder/stub work.
CodeRabbit's slop detector is enabled as advisory evidence, but it is never the
sole AI-code guard because service/automation authors may be exempt from that
detector. New CodeRabbit pre-merge checks begin in warning mode and may move to
blocking/error only after their signal has been observed on real AppT PRs.

An Arena implementation PR is never self-approved or self-merged. After Arena
returns its PR and machine checks are green, ChatGPT runs the repository
`code-review` capability against the fixed base and originating Issue, keeping
Standards and Spec findings separate. Review findings are resolved, verification
is rerun, and the human remains the merge decision-maker.

### Trust domains beyond pull requests

Do not put release credentials or LAN-connected persistent hardware into the
ordinary pull-request workflow.

When release automation becomes real, `.github/workflows/release.yml` is a
separate protected trust domain. It authenticates to Google Cloud with OIDC /
Workload Identity Federation, builds a candidate once, records its immutable
artifact identity and provenance/attestation, uploads that candidate to Play
internal testing, and promotes the same tested artifact rather than rebuilding
it.

A separate `deep.yml` is created only if long-running or hardware-backed checks
eventually need a cadence/trust domain that cannot sensibly live in pull-request
verification. The target repository therefore owns one verification workflow now
(`verify.yml`) and at most three ordinary verification/release workflows when
those later responsibilities become real: `verify.yml`, optional `deep.yml`, and
`release.yml`. `.github/workflows/codeql.yml` is the separate security-analysis
workflow and is not counted in that ordinary-verification inventory.
`.github/workflows/diagnose.yml` (focused, non-terminal feedback) and
`.github/workflows/agent-control.yml` (trusted dispatch bridge) are not ordinary
verification workflows either, and are not counted in that inventory.

### Focused diagnostics (`diagnose.yml`)

`.github/workflows/diagnose.yml` is the permanent home of focused
implementation feedback. It is `workflow_dispatch` only, one mode per meaningful
failure domain (`app-unit`, `samsung-unit`, `android-static`, `android-build`,
`backend`), and each mode uploads the reports it produces so an agent or a human
can read the failure without re-running the whole suite.

A diagnostic run is implementation feedback only. It is not terminal repository
verification, it does not satisfy `verify / gate`, and no job in it can produce
that status. It replaced the retired `samsung-targeted` dispatch mode of
`verify.yml`, which no longer exists.

Do not add per-slice or one-shot diagnostic workflow files. When a new focused
feedback need appears, add a mode to this workflow.

#### Focus within a mode

Every mode accepts an optional `focus` input that narrows that mode without
widening it. `tools/ci/diagnose-focus.mjs` owns what "narrow" means per mode and
returns the exact argv vector to run, so the value is data rather than command
text. The contract:

- an empty `focus` reproduces the mode's existing, unfocused command exactly — a
  focus can only narrow, never replace, the mode;
- a non-empty `focus` narrows only within the mode, through the underlying
  tool's native selector or a finite allowlist of sub-responsibilities the mode
  already owns;
- a value that starts with `-`, contains a control character, has the wrong shape
  for the mode, or names a sub-responsibility belonging to a different mode fails
  closed before anything runs;
- the run summary records the mode and the effective focus.

| Mode | Focus form | Narrows to |
|---|---|---|
| `app-unit`, `samsung-unit` | Gradle test class/method pattern | `--tests <pattern>` on that module's own test task |
| `android-static` | `lint` \| `detekt` \| `guards` | the owned tasks `androidStatic` already depends on |
| `android-build` | `app` \| `release-guard` | the owned AppT build/release-boundary tasks `androidBuild` already depends on |
| `backend` | `static:<sub>` \| `test:<pattern>` | a named static sub-responsibility, or a Jest positional file pattern |

The selectors were verified against the pinned toolchain before being committed:
the Gradle task paths are the ones `build.gradle.kts` declares for each failure
domain and the npm script names are the ones `backend/package.json` declares.
A focused diagnostic is still implementation feedback only; full `verify` is
unchanged by any of this.

The label bridge keeps ordinary command labels full-mode by default. When an
agent that lacks Actions-write permission needs the already-accepted narrow
`focus`, the pull request may carry exactly one mode-bound marker on a line by
itself, for example:

`<!-- appt-ci-focus app-unit: PairingToFirstControlFlowTest -->`

Applying the existing matching label (`ci:app-unit` in this example) makes the
trusted bridge read that marker from the same pull-request metadata snapshot and
pass only its value as the `focus` workflow input. A marker for another mode is
ignored; no marker preserves the existing whole-mode dispatch. Duplicate
matching markers fail closed. The pull-request body remains untrusted: the
bridge never evaluates marker text as shell or command syntax, and the trusted
`diagnose-focus.mjs` resolver validates the mode/focus pair before dispatch and
again inside `diagnose.yml`. `ci:full` never forwards focus.

### Agent invocation (`agent-control.yml`)

Arena and the ChatGPT control plane must be able to run focused diagnostics
without a human clicking through the Actions UI. Two routes exist:

1. **Direct dispatch** — an actor that holds Actions write permission dispatches
   `verify` or `diagnose` with `workflow_dispatch`. This is the preferred route.
2. **Trusted command bridge** — `.github/workflows/agent-control.yml`, for an
   integration that can mutate pull-request metadata but cannot dispatch
   workflows. An optional mode-bound `appt-ci-focus` marker in the PR body may
   narrow a diagnostic label while remaining untrusted data validated by the
   trusted resolver. Adding one of the command labels (`ci:full`, `ci:app-unit`,
   `ci:samsung-unit`, `ci:android-static`, `ci:android-build`, `ci:backend`)
   to a pull request makes
   the bridge resolve that pull request's exact head SHA and branch, dispatch
   the matching workflow **from the repository's default branch** carrying the
   target as inputs, consume the label, and record what it dispatched.

The bridge is the only privileged control path in the repository. It never
checks out or executes pull-request-controlled code: the one checkout it
performs is of the **base branch** — which is what `pull_request_target` runs
the workflow definition from and what `github.sha` points at — and the only code
it executes is the reviewed repository module
`tools/ci/dispatch-workflow.mjs`. The workflow it dispatches then runs under
that workflow's own read-only permissions. Its token permissions are limited to
`contents: read`, `actions: write`, `issues: write` and `pull-requests: write`,
and only the labels in its allowlist do anything.

#### Why the bridge dispatches the default branch, and how the head guarantee survives

GitHub's Create Workflow Dispatch endpoint takes `ref` as the git reference for
the workflow — a **branch or tag name**. It is not a commit SHA, and passing one
is rejected. A branch is also mutable, so dispatching a branch alone would not
guarantee that the run verifies the head the command was issued against.

That is only half the problem, and the other half is the reason the dispatch ref
is the default branch rather than the pull request's head branch. GitHub runs the
workflow **as the dispatch ref defines it**. Dispatching the head branch would
therefore let the pull request supply the workflow YAML and every local composite
action it uses — including `.github/actions/assert-dispatch-target`, the very
assertion that is supposed to check the target. A pull request could rewrite its
own assertion and pass it.

So the dispatch ref is the repository's **default branch**, resolved from the
provider rather than assumed, and the requested target travels as inputs:

- `target_ref` is the pull request's head branch name;
- `expected_sha` is the exact commit it resolved to.

The dispatched workflow then does the rest, in this order, in every job:

1. **Check out the dispatch anchor.** For a bridge dispatch `github.sha` is the
   default branch, so this checkout — and the assertion and the local composite
   actions it uses — is trusted repository content.
2. **Assert the dispatch target.** With `target_ref` set, the run's own commit is
   the anchor and not the target, so the ref is resolved through the API and
   required still to point at `expected_sha` — and `expected_sha` is required to
   be the head of an **open** pull request whose head ref is `target_ref`. That
   second part matters: a `workflow_dispatch` input is settable by any actor who
   can dispatch, so without it an arbitrary commit SHA could be supplied, pass the
   ref check, and then be built. With it, the only commit that can pass is one a
   real open pull request actually points at. A branch that moved between the
   command and the run hard-fails with a message naming both SHAs and telling the
   issuer to re-issue the label. With `target_ref` empty — an ordinary manual
   dispatch — there is no pull request to bind to, so the run's own commit is the
   target and is compared directly.
3. **Set up the toolchain and resolve any diagnostic focus**, still from the
   trusted anchor, so the code that decides *what* to run is trusted.
4. **Check out the commit the assertion proved**, published as its `sha` output,
   rather than the raw `expected_sha` input. The distinction is deliberate: an
   input is attacker-reachable, whereas the output is a value trusted logic has
   just verified. From here on the code under verification is
   pull-request-controlled, executed by a trusted workflow under that workflow's
   own read-only repository-token permissions and provider-enforced read-only
   cache access.

Both `verify.yml` and `diagnose.yml` declare workflow-level **`cache-mode: read`**.
GitHub enforces this with scoped cache tokens, independently of `GITHUB_TOKEN`
permissions. The default-branch dispatch can restore caches but cannot write
PR-controlled data into the default branch's cache scope. No target job may
override this with `write` or `write-only`. The same read-only restriction applies
to trusted-only runs; performance does not take precedence over isolation.
See GitHub's cache access reference [1](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching).

`expected_sha` is empty for `pull_request` and `push` events, so the assertion is
a no-op there and the cheap PR cadence is unaffected.

A **fork** pull request cannot be dispatched this way at all: the provider
dispatches a ref that must exist in this repository, and a fork's head branch
does not. The bridge fails closed with an explicit error rather than dispatching
something else.

The translation, the allowlist and the request shape live in
`tools/ci/dispatch-workflow.mjs` and are proven by
`tools/ci/test/dispatch-workflow.test.mjs` against a fake API. The test contract
rejects a commit SHA used directly as `workflow_dispatch.ref`, which is the
defect the first Issue #88 contract review found, and it rejects the earlier
mutable-ref shape outright: `targetRef` is required and `ref` may never equal it,
so the design that loaded the assertion from the branch being verified cannot be
expressed. The same tests assert that no checkout in either workflow names a
`workflow_dispatch` input as its ref.

The assertion's own decision logic is extracted from the action and executed for
real against a fake `gh` by `tools/ci/test/assert-dispatch-target.test.mjs`, so
every branch is proven: the no-op for non-dispatch events, the manual-dispatch
comparison, the moved-ref failure, and — the case that matters most — the refusal
of a commit that is not the head of an open pull request.

Owner follow-ups that cannot be represented in repository code:

- direct `workflow_dispatch` by the control-plane integration requires that
  actor to hold Actions write permission on this repository;
- GitHub dispatches only workflows that exist on the default branch, so
  `diagnose.yml` becomes invocable through the bridge once it lands on `main`.

### Migration sequencing

The expand-contract migration below has landed on `main`. The live inventory is
`verify.yml`, `diagnose.yml`, `codeql.yml` and `agent-control.yml`. The steps
remain as the accepted history of that contraction, not as pending work to
resurrect `ci.yml`. (`maintenance.yml` was later added by Issue #88 and later
still retired; retention settings and ordinary GitHub administration cover that
need.)

The replacement was an expand-contract migration; verification coverage must not
be silently dropped while infrastructure changes.

1. Introduce the project-owned verification interfaces (`ciCheck` and backend
   `verify`), the narrow quality-tool surface, coverage producers, and
   `verify.yml` while the accepted existing CI still runs.
2. Connect CI-based SonarQube Cloud analysis to the generated coverage evidence
   and prove the quality gate on a real pull request. Configure CodeRabbit's
   repository review policy and prove that it reviews a real AppT PR. Any
   required vendor/GitHub administration or secret setup is an owner action, not
   a reason to weaken repository checks.
3. Prove the new workflow produces equivalent-or-stronger evidence for every
   currently accepted gate, including runtime device acceptance and dependency
   review, and establish the old→new owner map before contraction.
4. Remove the path classifier, standalone detekt job, duplicated workflow-level
   backend lint/format orchestration, third-party emulator runner, maintenance
   cleanup/export plumbing that has no remaining product purpose, and any other
   superseded CI-only code.
   Until that contraction lands, the repository Actions allowlist must continue
   to permit every transitional third-party Action still referenced by the
   accepted workflow. For actions exposed from a repository subdirectory, the
   selected-action pattern must match the workflow reference shape, for example
   `gradle/actions/setup-gradle@*`.
5. CodeQL setup and migration-back condition:
   GitHub-managed Default Setup is the preferred owner, but the control-plane
   token cannot toggle it (the `code-scanning/default-setup` API returns
   `403 Resource not accessible by integration` for this repository), so AppT
   operates an Advanced Setup workflow (`.github/workflows/codeql.yml`) using the
   `codeql-action`'s own bundle. The workflow performs manual compilation under
   strict dependency verification for `java-kotlin` (`./gradlew ... assembleDebug`),
   and analyzes `javascript-typescript` and `actions` using the `security-extended`
   query suite. Migration-back condition: once a repository owner enables Default
   Setup in repository settings and managed Java/Kotlin analysis succeeds on the
   current Kotlin version, delete `.github/workflows/codeql.yml`.
6. After `verify / gate` has completed successfully, add it as the required
   status check and require the branch to be up to date before merging. Promote
   CodeRabbit checks from warning to blocking only after their AppT signal is
   observed and the human explicitly accepts that owner-setting change.
7. Update `docs/BUILD.md` and any CI comments to the final commands and delete
   stale implementation documentation. `main` must finish with no parallel
   legacy CI topology.
8. Issue #88 replaced the monolithic Android job with the parallel failure
   domains listed above, moved focused feedback out of `verify.yml` into the
   permanent `diagnose.yml` modes, removed the duplicated underlying guard work
   (`noCrashReportingInApp`, the `:samsung` instance of `noTelemetryDependency`,
   and the provider token formats GitHub Secret Scanning owns), added the
   trusted `agent-control.yml` dispatch bridge. The `samsung-targeted` dispatch
   mode is gone and must not be restored.

Every external Action that remains in repository workflow YAML is pinned to a
full immutable commit SHA. Repository permissions stay least-privilege, and
ordinary pull-request verification receives no release credentials.

`main` stays releasable. A red required check is not merged.

### Manifest allowlist

```text
INTERNET
ACCESS_NETWORK_STATE
ACCESS_WIFI_STATE
CHANGE_WIFI_MULTICAST_STATE
com.android.vending.BILLING
```

The allowlist governs *capability requests*: permissions AppT asks the platform
or another app to grant it. It is an upper bound, not a mandate — S01 declares
none of them.

One narrow category is outside it. A library may define a **self-permission**: a
`<permission>` the app declares and then requires of other apps to guard its own
exported component, most commonly a `FileProvider` or a `WorkManager`-style
internal receiver. Such an entry grants AppT no capability at all; it restricts
who may talk to AppT. The merged-manifest guard therefore exempts an entry only
when it satisfies **both** conditions:

1. it is scoped to the application id (`dev.anthracite.appt.*`), so it cannot be
   a platform or third-party capability; and
2. it is declared `android:protectionLevel="signature"`, so only a build signed
   with the same key can hold it.

Anything failing either condition — including any unscoped permission and any
`normal`/`dangerous` self-permission — must appear in the allowlist above or it
fails CI. This exemption cannot widen AppT's capability surface, because a
signature-level self-permission is not a capability AppT receives.

Anything else fails CI until the architecture map is changed. In particular the allowlist does not include `AD_ID`, `ACCESS_FINE_LOCATION`, `ACCESS_LOCAL_NETWORK`, `NEARBY_WIFI_DEVICES`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, or install-packages. `NEARBY_WIFI_DEVICES` is added only in the change that adopts a Wi-Fi API which requires it, with `neverForLocation`. A target-37 bump must change this allowlist in the same change that adopts `ACCESS_LOCAL_NETWORK`. See [discovery.md](discovery.md).

## Upgrade-in-place release proof

A release candidate is not complete merely because a clean install works. The data/secret/cache migrations in [data.md](data.md) and launch behavior in [lifecycle.md](lifecycle.md) must be exercised as an install-over-install journey.

- For the first public release, use the latest accepted internal/pre-release artifact carrying the prior schema/state shape; this proves the mechanism without inventing a nonexistent public predecessor.
- From the second public release onward, install the immediately previous production artifact, seed representative local TV/pairing/preferences/account/entitlement state through supported app/test seams, install the candidate over it without clearing data, then launch and exercise the normal route.
- The proof must demonstrate that a valid Samsung secret remains decryptable or reaches the documented recovery state, Room/DataStore migrations preserve user-visible state, entitlement remains valid or safely refreshable, and no migration silently behaves like a fresh install.
- A destructive migration fallback, data-directory wipe, or "clear app data" instruction is not an acceptable release strategy.
- The source and candidate artifact identities plus candidate commit SHA are recorded with the release evidence.

The journey contract and artifact requirements are in [testing.md](testing.md#whole-application-lifecycle-e2e).

## Release path and artifact identity

The release artifact is an Android App Bundle. Signing is deliberately split across three identities, and AppT never signs an artifact with a production app-signing key:

| Identity | Key | Held by | Signs |
|---|---|---|---|
| Play app-signing key | The Google-managed Play App Signing key | Google. AppT never receives, stores, or uses it | Every APK Play generates and delivers, on every track, including the internal testing track and staged production |
| Upload key | AppT's upload certificate | CI secret; not committed | The `productionRelease` bundle CI uploads to Play. Play verifies it, then re-signs the delivered APKs with the app-signing key |
| Internal signing key | A separate AppT keystore, distinct from the upload key and from any debug key | CI secret plus the organisation's credential store; not committed | The `internalRelease` tester build distributed outside Play |

Keeping the app-signing key Google-managed is the chosen strategy. The rejected alternative — retaining the app-signing key ourselves and using it to sign the outside-Play tester build — would put the key that protects every production install on an artifact distributed outside Play, and would give tester and store artifacts one custody model. The cost of the choice is one extra signing identity and one extra certificate registration, both made explicit below.

**Baked versus resolved configuration.** The Firebase configuration file and the backend URL are compiled into the artifact, so an artifact cannot change its environment after it is built. Promotion therefore moves the artifact that was tested, and the environment is chosen before the build rather than at promotion time:

| Build | Environment it talks to | Where it is distributed |
|---|---|---|
| `dev` build | `dev` | Developer machines and the emulator suite only |
| `internalRelease` | `internal` | Testers, outside the Play production tracks (Firebase App Distribution or direct install from CI) |
| `productionRelease` | `production` | **The only artifact uploaded to Play** |

### Signing identity and distribution channel

**AUTHORITATIVE:** Firebase's [App Check with Play Integrity](https://firebase.google.com/docs/app-check/android/play-integrity-provider) explicitly supports apps distributed on Google Play, outside Google Play, or both, with different advanced-verdict expectations. That source establishes the provider capability; the three-environment/certificate split below is the **APPT DECISION** that keeps each distribution identity isolated.

Signing and registration follow the distribution channel, because Firebase App Check registers an app by its signing-certificate SHA-256 fingerprint and the two channels do not share a certificate:

| Artifact | Distributed through | Signed with | Certificate registered in its Firebase project | App Check advanced settings |
|---|---|---|---|---|
| `dev` | Developer machines and the emulator suite only | Debug keystore, generated per machine | `appt-dev`: the debug certificate fingerprint, plus registered App Check debug tokens | Debug provider. No real verdicts |
| `internalRelease` | Outside Play: Firebase App Distribution or direct install from CI | The internal signing key | `appt-internal`: the internal signing certificate fingerprint | Play Integrity provider for an app distributed **exclusively outside Google Play**: `PLAY_RECOGNIZED` not required, `LICENSED` not required, minimum accepted device integrity `MEETS_DEVICE_INTEGRITY` |
| `productionRelease` | Play only: internal testing track candidate, then staged production | The upload key for the bundle CI uploads; Play re-signs delivered APKs with the Play app-signing key | `appt-prod`: the **Play app-signing certificate** fingerprint | Play Integrity provider for an app distributed on Google Play: `PLAY_RECOGNIZED` required, `LICENSED` required, no explicit device-integrity minimum |

Details that are easy to get wrong, so they are stated rather than implied:

- App Check stores a signing-certificate SHA-256 fingerprint and nothing else about the build. Registering the upload certificate in `appt-prod` would be wrong, because customers install APKs signed by the app-signing certificate. Registering the internal certificate there would let an outside-Play build authenticate to production.
- The Play Integrity API is linked per environment from Play Console (Release, then App integrity, then Play Integrity API) to the matching Cloud project: `appt-prod` for the production app and `appt-internal` for the internal app. Both use the same Play app entry and the same product catalogue; only the certificate registration and the verdict requirements differ.
- Before the first Play app-signing key upgrade, register the new Play app-signing certificate fingerprint with production App Check before rollout, keep any still-needed previous registration during the transition, and run a Play-delivered build through real App Check plus Play Integrity. The provider-documented certificate update is the contract; the first live upgrade remains a continuity drill rather than an assumed zero-risk transition.
- The three certificates are not interchangeable: the debug certificate appears in no environment but `appt-dev`, the internal certificate is accepted only by `appt-internal`, and `appt-prod` accepts only the Play-signed app.
- The `appt-internal` registration covers a build Play never distributes, so Firebase's "exclusively outside Google Play" settings apply to it. The `appt-prod` registration covers the app Play delivers, so the "on Google Play" settings apply. The "both channels" row applies to no single registration here, because no certificate is registered in two projects.
- The release candidate installed from the Play internal testing track is re-signed by Play, so it satisfies the `appt-prod` registration while it is still a candidate. That is what lets internal testing exercise production App Check enforcement before promotion.
- Consequence for billing and Play services: Play Billing only serves apps installed from Play, so the outside-Play tester build can never complete a real purchase and never receives an install-sourced Play verdict. Its purchase path runs against the fake `PlayBilling` adapter, and every piece of real purchase evidence — including the withheld-test-purchase check — comes from the Play-distributed candidate.
- Fingerprints live in the console and in the deployment log, never in the repository. Secret scanning fails on any keystore, certificate, or key file appearing in the tree.

One application id is shared by all three builds, so Play Billing and Play Integrity resolve the same product and the same app entry everywhere, and each variant embeds only its own environment's Firebase configuration. The signing identities differ, which has one visible consequence: with one application id and different certificates, a device cannot hold the internal-signed tester build and a Play-signed build at the same time, so moving between them needs an uninstall.

**One version code per release.** The `versionCode` identifies a release, not an environment, so `internalRelease` and `productionRelease` built from the same commit carry the **same** `versionCode` and the same `versionName`, taken from a monotonically increasing release counter that advances once per release commit and is recorded in the deployment log. Distinct codes would buy nothing here: only `productionRelease` is ever uploaded to Play, the two variants cannot coexist on one device anyway because their certificates differ, and a same-code rule keeps the comparison below strict instead of carving out an exception for a value that is part of the build output. Play's version-code sequence stays contiguous because production bundles are its only uploads, and every release commit's code is higher than the last, so promotion can never collide with a tester build. `dev` builds use a separate local scheme and never enter a Play upload or the artifact comparison. Artifact identity is recorded in the deployment log together with the commit SHA and the signing certificate fingerprint.

Workflows, manual dispatch only:

| Workflow | Result |
|---|---|
| Tester build | Builds `internalRelease` against the `internal` backend, signs it with the internal signing key, and distributes it to testers outside Play. Never uploaded to Play |
| Candidate upload | Builds `productionRelease` from the tested commit and uploads it to the **Play internal testing track**, pointed at the `production` backend, as a release candidate |
| Production promote | Promotes **that same uploaded bundle** to a production track with Play staged rollout. No rebuild, no re-pointing, no environment change |

So the tested artifact and the promoted artifact are the same artifact: the candidate that passes internal testing is the one that is promoted. Internal-environment testing does not use a Play track, which is what makes that possible.

**Comparing the release artifacts.** The two release variants are signed with different identities, so comparing signed artifacts byte for byte would fail for a reason that is not the code. `releaseArtifactsDifferOnlyByConfig` therefore normalizes before it compares:

1. Both variants are built from the same commit in the same job, and the same release unit tests and accessibility checks run against both.
2. Signature material is stripped: the entry list and the uncompressed entry contents are compared with every `META-INF/` signature entry removed, so the comparison is between unsigned build contents.
3. The only entries allowed to differ are the environment configuration files, in either direction: the Firebase configuration `google-services.json`, the resolved backend URL, and their generated resources. Any other differing entry fails the check, including a development or internal configuration file appearing in the production artifact, and including the `versionCode` or `versionName` in the manifest or the bundle's build output. Those two are equal by construction under the one-version-code-per-release rule above, and `releaseVariantsShareVersionCode` asserts that equality independently of the content comparison, so the comparison needs no version-code exception and none exists.
4. Signing identity is asserted separately instead of by byte comparison: the tester artifact carries the internal certificate, the bundle uploaded to Play carries the upload certificate, the two certificates differ, and neither is a debug certificate.
5. Certificate registration is asserted against the environment: the production project's App Check registration carries the Play app-signing certificate fingerprint recorded in the deployment log, and the internal project's carries the internal certificate fingerprint. Fingerprints are compared as recorded values, never committed as files.

Staged rollout starts below 100 percent. Promotion is a human action. Halt if the Play Console Android vitals crash or ANR rate rises, or an entitlement error rate rises. Exact percentages are release operations, not product intent, and are chosen at promote time within Play's staged rollout.

Play internal testing and Play production are the V1 channels. F-Droid and GitHub Releases are not committed channels.

R8 is on for release. Keep rules cover Room, Firebase Auth, Kotlin serialization, Play Billing, and Credential Manager. The R8 mapping file is uploaded to Play with the release so Android vitals stack traces can be read; it contains no user data.

An uncaught crash is not reported by the app, because V1 ships no crash-reporting SDK. Crash and ANR signal comes from Play Console Android vitals plus the customer's explicit diagnostic export. The release checklist records that, so a thin crash picture is not mistaken for a regression.

## Two external gates

Two gates sit on public production promotion and are not slices:

- Human confirmation of the final AppT source license. The tree currently contains an MIT `LICENSE`, but repository presence is not the human source-license decision; the gate closes only when the decision is explicitly accepted and recorded.
- Focused Samsung vendor-terms review, already required before public release.

Internal testing may proceed before those gates. Public production promotion may not.

## Provenance

New significant dependencies need a license and provenance note in the pull request that adds them. Prefer maintained, auditable libraries with narrow network behavior. Reject embedded credentials, tracker SDKs, unnecessary listeners, and opaque proprietary control-path blobs.

Incompatible-license Samsung reference code stays reference. Do not vendor it. The clean-room rule is in [protocol.md](protocol.md).

## Supply-chain checks that protect the invariants

| Check | Protects |
|---|---|
| `samsung` has no Firebase or Play artifact | Local control does not need the cloud |
| `app` has no Firestore client | No client-side account or television data path exists |
| Permission allowlist | No surprise LAN listener, location, or ads id |
| Fixture grep | Secrets and addresses do not enter git |
| No `Log` in `samsung` | Tokens do not reach logcat by accident |
| No crash-reporting, analytics, advertising, or attribution artifact | Nothing is uploaded automatically and no provider installs an identifier |
| No HTTP client in the diagnostics package | No hidden diagnostic upload path exists |
| Pinned, verified dependencies | Unreviewed protocol or tracker code does not slide in |
| Action SHA pins | CI cannot be retargeted by a moving tag |
| Environment guard | A test build cannot touch production data or purchases |
| Signing identity per channel | The tester build and the uploaded bundle cannot be confused, and no debug certificate reaches a real environment |
| Certificate registration per environment | An outside-Play build cannot authenticate to production |
| Secret scanning | No long-lived credential enters the tree |
| Backend schema test on forbidden fields | No television or personalization field can be added silently |
