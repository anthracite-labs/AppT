# AppT simplification & consolidation — implementation report

**Branch:** `arena/01a0f840-appt`
**Base:** `origin/main` at `754f6e23ed24447fa131722b242b671258b0308c`
**Implementation head before this report update:** `1f86aed`
**Final reconciliation pass:** 2026-10-01, on the complete branch (see verification evidence below).
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
| Distinct registered Gradle check/guard task names | 18 | 16 | −2 |
| Markdownlint configuration owners | 3 | 1 | −2 |
| Gradle lockfiles in tree | 5 | 3 | −2; remaining files are inactive, pending S05 ownership |

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
| Gradle locking configuration and refresh tasks | Remove | Locked resolution graph | Exact pins + strict dependency verification; no dynamic declarations to lock |
| Root/settings lockfiles | Remove | Generated resolved graph | Removed; no consumer |
| App/Samsung/benchmark lockfiles | Defer physical deletion to PR #118 | Generated graph snapshots | No longer consumed; files left untouched while S05 updates them |
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
- **Issue #127 ("Trusted hosted Gradle lock refresh artifact") is superseded and should be closed by a repository owner** — this integration is forbidden from closing or commenting on issues (403 on both). Rationale: the issue's contract requires a trusted route to run `./gradlew resolveAndLockAll --write-locks` and stage the five known lockfiles, but locking was retired wholesale here (commit d6a8f05), `resolveAndLockAll` no longer exists, and no active lock state remains to refresh. The motivating Baseline Profile producer change in PR #118 belongs to the pre-Revision-5 S05 shape retired by Contract Revision 5. The companion implementation PR #128 was already closed with this rationale. If dynamic/range/SNAPSHOT declarations are ever reintroduced, locking and a refresh route become a fresh decision.

## Outcome

The branch removes or consolidates redundant permanent machinery without flattening the AppT/Samsung product architecture, changing product behavior, deleting product tests, weakening strict artifact verification, or granting untrusted code cache/secrets/write authority. It is materially simpler: **one fewer workflow, six fewer CI jobs, four fewer script/action files, two fewer custom Gradle task names, one markdownlint owner, one backend CI domain, and no active Gradle lock lifecycle**. It deliberately retains two demonstrated high-value mechanisms the static complexity audit could not prove from repository shape alone: Sonar's active required quality gate and the frequently used trusted agent bridge; it retains the warm-cache removal as an explicit measured tradeoff.
