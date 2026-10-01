# AppT simplification & consolidation — implementation report

**Branch:** `arena/01a0f840-appt`
**Base:** `origin/main` at `754f6e23ed24447fa131722b242b671258b0308c`
**Implementation head before this report update:** `1f86aed`
**Final reconciliation pass:** 2026-10-01, on the complete branch (see verification evidence below).

## Correction addendum — post hosted verification (supersedes the locking retirement)

After this report was first written, one trusted `ci:full` dispatch (run `36909210678`,
head `a8a624c`) produced the first real hosted Android verification of the branch. Its
failures were classified, and one correction commit implements only the real defects.

### Failure classification (run 36909210678 @ a8a624c)

| Failure | Category | Disposition |
|---|---|---|
| repo-policy invokes deleted `enforce-dependency-policy.test.mjs` | 1 — expected old-main verifier incompatibility | none; the trusted route executes `main`'s workflow definition, which still references scripts this PR legitimately deletes. Self-resolves at merge. Not evidence for restoration. |
| repo-quality invokes deleted `workflow-cache-contract.test.py` | 1 — same | none |
| dependency-review invokes deleted `enforce-dependency-policy.mjs` | 1 — same | none |
| gate failure | 1 — downstream of the above plus category 2 | none |
| android-format: Spotless violation in `build.gradle.kts` | 2 — real defect | fixed: ktfmt collapses the now-single-statement `tasks.named("androidUnit")` lambda to one line |
| android-static: strict verification rejected `kotlin-stdlib` 1.9.21/2.1.20 module metadata in `:samsung:debugAndroidTestRuntimeClasspath` | 2 — real defect (graph drift caused by the locking retirement) | fixed by restoring standard dependency locking (below), not by regenerating verification metadata |
| android-build: `org.jetbrains.kotlin.plugin.compose:2.4.20` resolution failure | 2 — same root cause (unlocked resolution exploring previously locked-away artifacts) | same fix |
| "Basic caching failed to save entry" annotations | 3 — external/transient, non-fatal | none |
| CodeQL code-scanning check: 3 "potential cache poisoning" alerts on `verify.yml` dispatch-target checkouts (lines 188/493/564) | 4 — owner decision (see below) | pre-existing accepted pattern, re-flagged because the PR rewrote the file |
| CodeQL Default Setup toggle (403), Issue #127 closure (403), post-merge terminal verification of a CI-self-modifying PR | 4 — owner decisions | reported below |

### Dependency locking — re-evaluation and partial restoration

The hosted failure disproved the audit's broad conclusion that "exact direct versions
mean locking adds no useful determinism". With locking disabled, hosted resolution
explored transitive artifacts (kotlin-stdlib 1.9.21/2.1.20 module metadata) that the
committed lock state had kept out of the graph; strict verification correctly rejected
them. The known-good resolved graph was restored rather than accommodating the drift:

- Restored (standard Gradle machinery, byte-identical to `main`): `allprojects { dependencyLocking { lockAllConfigurations() } }`; the per-subproject `resolveAndLockAll` refresh task (Gradle's documented lock-all-in-one-execution pattern); root `gradle.lockfile` and `settings-gradle.lockfile`. Module lockfiles (`app`, `samsung`, `macrobenchmark`) were already present. Strict verification metadata is untouched — no metadata was regenerated to absorb drift.
- Still removed (custom machinery around locking): the ~64-line `dependencyLockCheck` resolution wrapper (Gradle natively fails any drifted resolution once lockfiles are committed, so every resolving task owns drift detection); the trusted hosted `dependency-state`/lock-refresh diagnostic route (refresh remains a reviewed local change per `docs/BUILD.md`; Issue #127's premise stands superseded); the custom dependency-policy enforcer (standard dependency-review owns the policy).
- Docs reconciled to the restored state: `docs/BUILD.md` (canonical refresh procedure), `docs/ARCHITECTURE.md`, `docs/architecture/release.md`, `.github/dependabot.yml` reviewer note, `verify.yml` change-detection pattern (lockfiles are dependency inputs again), `tools/security/run.sh` fail-closed description.

### Sonar / Kover — one bounded decision, no further oscillation

**Keep Sonar as-is in this PR.** It provides a unique consumed signal (cross-language
new-code coverage, duplication, maintainability, reliability quality gate; live evidence:
main verify run `36881699523` success with `quality-platform` required by the gate),
and removing it requires a separate accepted gate/architecture change. That
simplification is deferred out of this PR rather than oscillating inside it.
**Kover stays at the minimum Sonar requires:** product coverage aggregation + XML
reporting wired into `androidUnit`; no coverage floor anywhere (the build/CI floor was
ceremonial and is removed). This decision is final for this PR.

### Defects fixed in the correction commit

1. `gradle/libs.versions.toml`: restored the `kover` plugin alias left unresolved by the coverage-floor removal (all Gradle jobs died at script compilation: `Unresolved reference 'kover'`).
2. `build.gradle.kts`: Spotless/ktfmt formatting of the surviving Kover wiring.
3. Dependency locking restoration as described above.
4. Trimmed the retired-enforcer history comment in `verify.yml` (per complete-removal policy).

### Verification strategy note (bootstrap limitation)

This PR modifies the verification workflows themselves. The trusted `ci:full` route
executes `main`'s workflow definition, so it cannot validate its own replacement:
category-1 failures are structural, not convergent, and no further `ci:full` dispatches
will be used as the correction loop. Branch-native evidence instead: GitHub executes
the candidate workflow for `pull_request` checks (all green at the corrected head),
plus local suites (149 Node tests, Sonar boundary 13/13, project-state contract 8/8,
cache-mode contract, yamllint, markdownlint, backend verify, secret scan, stale-reference
sweeps). Final terminal verification of the simplified full graph happens on the first
push to `main` after merge — accepted as a bootstrap limitation requiring owner
acceptance, together with the owner actions below.

### Owner decisions outstanding (category 4)

1. **CodeQL code-scanning alerts** (3, "Potential cache poisoning", `verify.yml` dispatch-target checkouts): this is the accepted trusted-dispatch design, identical on `main`; the alerts are flagged only because this PR rewrote the file. Mitigation is provider-enforced `cache-mode: read` (workflow-level, structurally proven by `cache-mode-contract.test.py`), which CodeQL's Actions analyzer does not model. The integration cannot list or dismiss code-scanning alerts (403). Owner action: dismiss the three alerts with that rationale, or add a scoped query exclusion — a security-posture decision deliberately not made unilaterally.
2. **CodeQL Default Setup toggle**: `code-scanning/default-setup` API returns 403 for this token; remains an owner settings action.
3. **Issue #127 closure**: issue API forbidden for this integration; closure rationale recorded above (locking restored, but the *trusted hosted refresh route* stays retired — refresh is a reviewed local change, so the issue's contract remains superseded).
4. **Post-merge terminal verification**: first push to `main` exercises the simplified full graph (the trusted `ci:full` route cannot validate its own replacement before merge).
**Scope:** repository simplification only. No product source or product test was edited. `.project-ai/skills/**` is unchanged; `PROJECT_STATE.md` is left for post-acceptance reconciliation.

This implements the two accepted audits (`REPO_AUDIT_2026-10-01_main_754f6e2.md` and `COMPLEXITY_AUDIT_2026-10-01_main_754f6e2.md`) against the live repository and open PRs. It deliberately leaves S05-owned product, device, benchmark and generated-state edits to draft PR #118. PRs #123 and #128 were closed as superseded; see their closure comments. The cache and Sonar decisions below were revised after checking actual GitHub workflow history rather than applying audit conclusions mechanically.

## Before / after

**Counting rules:** tracked main/test source is product; support is every other tracked text file except `audit/**`, generated state, and the hard-excluded `.project-ai/skills/**`. Generated state includes dependency lockfiles, `gradle/verification-metadata.xml`, `backend/package-lock.json`, and Room schema JSON. Binary wrapper JAR has no LOC. These are broad, reproducible repository counts, not estimates of engineering effort.

| Measure | `main` @ 754f6e2 | This branch | Change |
|---|---:|---:|---:|
| Project tracked files (excluding audit deliverables) | 301 | 292 | −9 |
| Tracked files including this branch's 3 audit reports | 301 | 295 | −6 |
| Hand-authored support LOC (excluding product/tests, generated state, audits and skills) | 20,049 | 16,946 | **−3,103** |
| Generated support LOC | 12,797 | 12,767 | −30 |
| Hand-authored + generated support LOC | 32,846 | 29,713 | **−3,133** |
| Production source LOC (`app` + `samsung` Kotlin and backend TS) | 7,543 | 7,543 | 0 |
| Product test LOC | 9,015 | 9,015 | 0 |
| Workflows | 5 | 4 | −1 |
| Workflow YAML LOC | 2,703 | 1,842 | −861 (−32%) |
| CI jobs | 26 | 20 | −6 |
| Custom CI/security scripts + local composite actions | 22 files / 6,539 LOC | 18 files / 4,646 LOC | −4 files / −1,893 LOC |
| `docs/**` plus root documentation LOC | 6,946 | 6,727 | −219 |
| `.project-ai/**` LOC | 4,191 | 4,191 | 0 (skills are 3,279 LOC and were excluded from this work) |
| Distinct registered Gradle check/guard task names | 18 | 17 | −1 after the correction addendum (`dependencyLockCheck` wrapper stays removed; standard `resolveAndLockAll` restored) |
| Markdownlint configuration owners | 3 | 1 | −2 |
| Gradle lockfiles in tree | 5 | 5 | restored after the correction addendum: standard locking re-enabled, all five lockfiles active |

Current workflow jobs: `verify` 11, `diagnose` 6, `codeql` 2, `agent-control` 1.
Current generated lock state: legacy `app/gradle.lockfile`, `samsung/gradle.lockfile`, and `macrobenchmark/gradle.lockfile`; the build no longer enables dependency locking or checks/refreshes these files. Strict dependency verification remains active. The verification-metadata template also remains because active PR #118 edits it; see Deferred.

The hand-authored support reduction includes docs, CI, tools and non-skill support. The 3,279 LOC under `.project-ai/skills/**` contributes zero to this comparison and was not touched. Product architecture, fixtures and product tests are preserved.

## Implemented simplifications

### Configuration ownership

- Consolidated `.markdownlint.jsonc`, `.markdownlint-cli2.jsonc`, and `.markdownlintignore` into `.markdownlint-cli2.jsonc`. All intentional rule exclusions and ignore patterns are preserved. CI/editor uses one canonical owner; markdownlint reports 0 issues across 29 Markdown files.
- Consolidated backend full verification to one `backend` job running `npm run verify --prefix backend`; the package still owns its narrow local `verify:static` / `verify:test` entrypoints.
- Consolidated dependency vulnerability enforcement in the standard `actions/dependency-review-action` with `fail-on-severity: low`. Removed the custom policy script/tests and its obsolete jgit exception: the root constraint resolves jgit to patched `6.10.1.202505221210-r`, which is present in verification metadata.

### Gradle dependency and coverage stack

- Removed global `lockAllConfigurations`, `resolveAndLockAll`, `dependencyLockCheck`, lock-specific CI/change-detection wiring, and the trusted `dependency-state` diagnose mode/label. No dynamic, range, or SNAPSHOT versions exist in the version catalog; exact versions remain protected by `versionCatalogPinned`.
- Deleted the root and settings lockfiles. The remaining app, Samsung and benchmark lockfiles are no longer consumed by the build and are intentionally left for PR #118's S05-generated-state changes; no lock refresh command remains.
- `gradle/verification-metadata.xml` remains the single active artifact-integrity state under `--dependency-verification=strict`. The template is temporarily retained because PR #118 modifies it; it is not used by the active verification path.
- Retained Kover **product coverage reporting** because Sonar consumes its XML. Removed the 80% Kover threshold on build/CI implementation and all `koverVerify` wiring. Unit tests still run; no test was removed.

### CI, diagnostics and security platform

- Removed `maintenance.yml`, `tools/ci/purge-actions.mjs`, and the purge tests. The sole historical purge run does not justify a repository-owned destructive Actions workflow; normal GitHub retention/admin handles cleanup. `actions: write` is no longer granted for cache/run deletion.
- Reduced `diagnose.yml` to five modes (`app-unit`, `samsung-unit`, `android-static`, `android-build`, `backend`) plus the non-terminal feedback job. In a 46-run history sample, active modes were: app-unit 21, android-build 8, android-static 3, samsung-unit 2, dependency-state 2, and legacy device 10; neither backend-static nor backend-test was used. Removed the two unused backend peer modes and dependency-state; the combined backend mode still supports focused static/test selectors. The device workflow mode was already absent from current main and is not reintroduced (S05 Rev 5 owns its retirement).
- Removed the PR-local warm-state publisher, per-job cache upload/restore plumbing and sanitizer. Actual history showed this *was used*: **35 successful publishers in 46 recent diagnose runs sampled** (two skipped; earlier runs predated it). It materially sped repeated S05 iterations. Removal is a conscious simplicity/trust-surface tradeoff, not an “unused code” claim: the benefit lost is warm reruns; the surviving `cache-mode: read` boundary is stricter, so target code cannot save any provider cache. A small PyYAML contract test keeps `verify.yml` and `diagnose.yml` read-only and rejects job-level overrides, replacing the larger cache-writer contract suite.
- Right-sized CodeQL Advanced Setup: removed separate bundle URL pin and duplicate Gradle version declaration; the CodeQL action owns its bundle, and `tools/security/run.sh build` is the one local/CI Kotlin extraction command. GitHub Default Setup remains the preferred destination, but its settings API returned `403 Resource not accessible by integration`; switching setup is an owner action.
- Kept the agent-control bridge, exact-head/open-PR assertion, immutable action pins, least-privilege tokens, dependency review, secret scanner, CodeQL, detekt, Android Lint, CodeRabbit and AppT product guards. Actual recent history supports keeping the bridge: 56 agent-control runs were observed (55 successful).

### Sonar decision — retained after current-state research

Sonar was initially removed under the audit's duplication finding, then **restored before completion** after checking actual main history. Main verification run `36881699523` executed the Sonar scan successfully; its `quality-platform` job was a required `verify / gate` dependency. The configuration imports Kover/Jest coverage and waits for Sonar's quality gate. This is live, consumed signal, not an inert file. Its cross-language new-code coverage, duplication, maintainability and reliability signals are not supplied by Android Lint, detekt, CodeQL or ordinary tests, so removing it would violate the user's “remove only if no unique, used signal” criterion.

The secret-bearing boundary is therefore retained: the candidate is checked out as inert analysis data, trusted scanner settings remain at the anchor, `tools/ci/validate-sonar-inputs.py` rejects unsafe inputs, `SONAR_TOKEN` is scoped to the token check/scanner, and `tools/ci/test/sonar-boundary.test.py` covers the boundary. Sonar is not run on ordinary PR synchronization; it remains a full-verification gate.

## Mechanism disposition and guarantees

| Mechanism | Decision | Previous guarantee | Current owner / effect |
|---|---|---|---|
| Exact Gradle versions + `versionCatalogPinned` | Keep | No dynamic/range drift | Version catalog + guard |
| Gradle locking configuration and refresh tasks | Restore standard core; drop custom wrapper | Locked resolution graph | `lockAllConfigurations` + `resolveAndLockAll` restored byte-identical to main; the custom `dependencyLockCheck` wrapper stays removed because native Gradle lock enforcement already fails drifted resolution (see correction addendum) |
| Root/settings lockfiles | Restore | Generated resolved graph | Restored from main; active under standard locking |
| App/Samsung/benchmark lockfiles | Keep | Generated graph snapshots | Active under standard locking; left untouched for PR #118's S05 generated-state edits |
| Strict verification metadata | Keep | Detect changed/tampered artifacts | Single active committed integrity state |
| Verification metadata template | Defer deletion to PR #118 | Reviewable bootstrap policy header | Not consumed by CI/build; active S05 edits the file |
| Custom dependency-policy enforcer | Remove | Fail PR on vulnerable introduced package; allowed patched jgit exception | Standard dependency-review action fails on low+; exception obsolete at patched jgit version |
| Dependabot | Keep | Reviewed version proposals | Standard GitHub feature |
| Sonar + Kover reports | Keep | Cross-language new-code quality/coverage/duplication gate | Actual required successful main-run evidence; Kover floor on build code removed |
| Secret scanner | Keep | Catch AppT credential files/material outside provider token formats | 341 LOC, custom AppT patterns; platform scanner owns provider formats |
| CodeQL | Keep, simplify | Security SAST | Advanced workflow remains because Default Setup cannot be toggled here; local build has one command owner |
| Backend CI | Consolidate | Separate static and test status checks | One full backend verification job; skeleton remains non-deployable |
| Maintenance purge | Remove | Delete Actions caches/runs on demand | GitHub retention/admin; no repo-held destructive token |
| Warm-state cache publisher | Remove after usage review | PR-local warm Gradle state, with untrusted-cache sanitization | Read-only caches only; lose warm rerun speed; **35 successful uses acknowledged** |
| Exact-head trusted agent bridge | Keep | Workflow from default branch, exact open-PR head proven before checkout | 55/56 successful recent invocations; no PR code runs with write authority |
| Markdownlint config | Consolidate | Same rule/ignore policy | `.markdownlint-cli2.jsonc` only |

Security invariant for deleted cache writer: the old publisher prevented PR-controlled code from writing a poisoned cache into a trusted scope. Removing it leaves **no writer exception at all**: verify/diagnose are provider-enforced `cache-mode: read`, and the new structural test verifies that. No cache trust boundary was relaxed.

## Verification evidence

Green at the implementation candidate before report-only updates:

- Node suites: diagnose focus 67/67, dispatch bridge 38/38, exact-head assertion 16/16, tooling constraints 18/18, secret scanner 10/10.
- Sonar secret-boundary Python suite: 13/13; cache-mode contract passed.
- Project-state contract: 8/8 tests; contract check passed. No `.project-ai/skills/**` file was modified.
- Secret scanner tree scan passed. Tooling-constraint enforcer passed.
- `npm ci --prefix backend` and `npm run verify --prefix backend` passed (typecheck, lint/format/Knip, Jest 2/2; 100% coverage on the skeleton). Sandbox Node was v22.22.3 and emitted the expected `EBADENGINE` warning because the package requires Node 24; GitHub workflow setup remains pinned to Node 24.
- yamllint passed; all four workflow YAML files parse: verify 11 jobs, diagnose 6, CodeQL 2, agent-control 1.
- markdownlint-cli2 passed over 29 Markdown files.

**Android/Gradle terminal verification is blocked in this sandbox:** `java` and `JAVA_HOME` are absent, Android SDK variables/paths are absent, and HTTPS egress to Gradle/Maven/Google SDK endpoints fails (`SSL_ERROR_SYSCALL`; apt repositories also unreachable). `./gradlew --version` fails immediately with “JAVA_HOME is not set and no 'java' command could be found.” No green Gradle build is claimed. The Gradle changes have not had local compile/test verification; full GitHub `workflow_dispatch` verification is still required after push.

### Final reconciliation pass (this report's head)

Re-run green on the complete branch immediately before this update:

- Node suites (`node --test tools/ci/test tools/security/test tools/secret-scan/test`): **149 pass / 0 fail**.
- Sonar secret-boundary suite: 13/13. Cache-mode contract: passed (`verify` and `diagnose` parse as provider `cache-mode` read-only; no writer exception). Project-state contract: 8/8 plus `project_state_contract.py check` passed.
- `yamllint` clean across the four workflow files; `markdownlint-cli2 "**/*.md"` clean (rc 0).
- `npm ci --prefix backend` + `npm run verify --prefix backend`: typecheck/lint/Knip green, Jest 2/2, 100% coverage on the skeleton.
- Secret scanner tree scan: OK over 295 tracked files.
- Stale-reference sweep for every retired concept (`resolveAndLockAll`, `dependencyLockCheck`, `lockAllConfigurations`, `write-locks`, the `ci:dependency-state` live route, `purge-actions`/`maintenance.yml`, `backend-static`/`backend-test` as peer modes, `koverVerify`/`minBound`, warm-state publishing): **no live references remain** outside (a) the audit artifacts, (b) the PROJECT_STATE acceptance line for PR #129 — reconciled post-acceptance per that file's own policy, (c) the generic operation-class vocabulary in `.project-ai/execution/verification.md`, and (d) one historical note in `release.md` that correctly describes the maintenance-workflow retirement.
- Confirmed PR #118's head still edits `app/gradle.lockfile`, `samsung/gradle.lockfile`, `macrobenchmark/gradle.lockfile`, `gradle/verification-metadata.xml`, and `gradle/verification-metadata.template.xml`, so those files are deliberately retained here to avoid a modify/delete conflict with the active slice.

## Active PR boundary / deferred cleanup

- Draft PR #118 remains open and owns the S05 product/device/benchmark changes. No product source, Samsung protocol code, fixtures, release-boundary behavior, or `macrobenchmark/**` was changed here. `app/gradle.lockfile`, `samsung/gradle.lockfile`, and `gradle/verification-metadata.template.xml` are restored in this branch so PR #118's generated-state edits can merge without file-level modify/delete conflicts.
- A merge-tree simulation against PR #118's current head finds only **one content conflict** in `docs/BUILD.md`: the obsolete `backend-test` focus example in its S05 doc patch versus this branch's replacement with the current combined `backend` mode. No build/product file conflict remains. Resolve that single documentation hunk to the current `backend` selector when integrating; `backend-test` had zero active diagnostic runs in the 46-run sample.
- `.project-ai/PROJECT_STATE.md` still reflects accepted `main` state and the `ci:dependency-state` route. Per repo policy, it should be reconciled only after this work is accepted on `main`; no PROJECT_STATE change was made here. `.project-ai/execution/verification.md` uses “dependency-state” as a generic operation class, not a live route.
- CodeQL Default Setup remains a repository-owner settings action because the integration's API call was forbidden (403).
- **Issue #127 ("Trusted hosted Gradle lock refresh artifact") is superseded and should be closed by a repository owner** — this integration is forbidden from closing or commenting on issues (403 on both). Rationale after the correction addendum: standard dependency locking and `resolveAndLockAll` were restored, but the issue's actual contract — a *trusted hosted diagnose route* that regenerates lock state — stays deliberately retired as unnecessary custom machinery. Lock refresh remains a reviewed local change with the documented command (`docs/BUILD.md`); no CI mode exists or is planned to perform it, so there is no lock state for a hosted route to refresh. If a future workspace genuinely cannot run Java for a reviewed dependency change, that is a fresh decision.

## Outcome

The branch removes or consolidates redundant permanent machinery without flattening the AppT/Samsung product architecture, changing product behavior, deleting product tests, weakening strict artifact verification, or granting untrusted code cache/secrets/write authority. It is materially simpler: **one fewer workflow, six fewer CI jobs, four fewer script/action files, one fewer custom Gradle task name (the `dependencyLockCheck` wrapper), one markdownlint owner, one backend CI domain, and no custom machinery around standard dependency locking** — while standard locking itself was restored by the correction addendum after hosted verification proved it stabilizes the transitive graph. It deliberately retains two demonstrated high-value mechanisms the static complexity audit could not prove from repository shape alone: Sonar's active required quality gate and the frequently used trusted agent bridge; it retains the warm-cache removal as an explicit measured tradeoff.
