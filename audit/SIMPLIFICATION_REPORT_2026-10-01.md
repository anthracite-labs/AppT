# AppT simplification report — 2026-10-01

Branch `arena/01a0f840-appt`, final head `9f31088` (commits `28369eb`,
`d6a8f05`, `705a513`, `9f31088`), base `origin/main` at `754f6e23`.

Execution record for the complexity audit
(`audit/COMPLEXITY_AUDIT_2026-10-01_main_754f6e2.md`): remove what a
boring, conventional, secure, maintainable AppT would not deliberately build,
keep every genuine product and security guarantee, and leave the repository
in a state where the simplified architecture itself proves the work.

Superseded PRs closed with rationale comments: #123 (PR-local warm Gradle
cache — the machinery is deleted here) and #128 (trusted hosted Gradle lock
refresh — locking is retired here). PR #118 (draft S05) is untouched.

## 1. Before / after

| Metric | main @ 754f6e2 | This branch | Delta |
|---|---|---|---|
| Workflows | 5 (verify, diagnose, codeql, maintenance, agent-control) | 4 (maintenance deleted) | −1 |
| CI jobs | 26 (verify 12, diagnose 9, codeql 2, maintenance 1, agent-control 1, quality-platform inside verify) | 19 (verify 10, diagnose 6, codeql 2, agent-control 1) | −7 |
| Workflow YAML LOC | 2,703 | 1,711 | −992 (−37%) |
| verify.yml | 1,046 lines / 12 jobs | 846 lines / 10 jobs | −19% |
| diagnose.yml | 1,222 lines / 9 jobs | 546 lines / 6 jobs | −55% |
| Custom CI/security scripts + tests (`tools/`, `.github/actions/`) | 6,539 LOC | 4,293 LOC | −2,246 (−34%) |
| Guard task registrations | 18 | 12 | −6 |
| Gradle lockfiles | 5 (root, settings, app, samsung, macrobenchmark) | 1 (macrobenchmark, owned by PR #118) | −4 |
| Dependency integrity states | 2 (lockfiles + verification metadata) | 1 (verification metadata) | −1 concept |
| Support LOC total (hand-authored + generated, excluding prod/tests/audits) | ~31,860 | ~18,900 | ~−13,000 |
| Docs LOC | 6,946 | 6,365 | −581 |
| `.project-ai` files / LOC | 30 / 4,191 | 30 / 4,191 | unchanged (mostly off-limits) |
| Net diff vs main | — | 40 files, +1,941 / −4,911 | net −2,970 |

Production code (7,782 LOC) and product tests (9,015 LOC) are untouched —
every deleted line is machinery, not product.

## 2. Deletions — what, why, old guarantee, new owner

| Deleted | Why | Old guarantee | New owner / disposition |
|---|---|---|---|
| `gradle.lockfile`, `settings-gradle.lockfile`, `app/gradle.lockfile`, `samsung/gradle.lockfile`, all locking config, `resolveAndLockAll`, `dependencyLockCheck` | Version catalog pins every dependency exactly (guarded by `versionCatalogPinned`); no dynamic/range/SNAPSHOT declarations exist. Gradle's own docs state locking "makes sense only with dynamic versions". Lock state policed nothing resolution did not already fix | Reproducible resolution | Exact versions + strict dependency verification (686 components, SHA-256) give equal-or-stronger determinism and integrity |
| `gradle/verification-metadata.template.xml` | Bootstrapping template for a generated file that is already committed; regeneration is one documented command | Bootstrapping guidance | One command in `docs/BUILD.md` |
| `tools/security/enforce-dependency-policy.mjs` + tests | Its only AppT-unique rule was a manifest-scoped exception for jgit, obsolete because the root buildscript constraint resolves jgit to the patched release already recorded in the verification metadata; its remaining fail-on-severity rule duplicates the standard action | Vulnerable-dep PR gate | `actions/dependency-review-action` with `fail-on-severity: low`, `warn-only` removed — equal-or-stronger enforcement, one owner |
| Sonar chain: quality-platform job, `sonar-project.properties`, `tools/ci/validate-sonar-inputs.py`, `tools/ci/test/sonar-boundary.test.py`, secret-bearing-boundary docs | Sonar added no consumed signal: its quality gate was never wired to a decision, coverage thresholds measured build/CI code, and it forced a secret-bearing job plus its own trust-analysis machinery (input validator, boundary tests, pinned-source proofs) | Cross-language quality gate | Android Lint + detekt + CodeQL (`security-extended`) remain the static/security owners; Jest LCOV remains locally. No decision depended on Sonar, so nothing needed to take over |
| Kover: plugin (root/app/samsung), 80% floor, `androidUnit` coverage wiring, CI upload | Kover existed solely to feed Sonar; its floor counted CI/build code, not product quality | Coverage no-regression floor | None needed: the underlying tests remain and run in `android-unit`; coverage can be regenerated locally if ever wanted |
| `maintenance.yml`, `tools/ci/purge-actions.mjs` + tests | One historical run. GitHub retention settings and ordinary Actions administration cover cache/run cleanup | Destructive housekeeping on demand | Manual GitHub administration; nothing in the repo needs periodic purging |
| Warm-state machinery in `diagnose.yml`: per-job outputs, cache-restore steps, warm packaging/upload, trusted `publish-gradle-warm-state` job, `tools/ci/test/workflow-cache-contract.test.py` | Performance optimization for repeated diagnostics with its own trust analysis, sanitized-publisher job and parsed-YAML contract to police it — the only `cache-mode: write-only` exception in the repo, built for iteration speed on a small app | Faster repeated Gradle diagnostics | Standard provider Gradle caches with `cache-mode: read`. First runs of a mode are slower; the audit judged that acceptable against the machinery it deletes |
| `backend-static` + `backend-test` jobs and diagnose modes | The backend is a 2-file, non-deployable skeleton; two CI domains and two dispatch labels policed it | Independent static/test failure evidence | One `backend` job running `npm run verify --prefix backend` (static then tests); split permitted to return when the backend becomes deployable |
| `dependency-state` diagnose mode/label | Its only job regenerated lock state that no longer exists | Lock-state diagnostics | Gone with locking |
| Pinned CodeQL bundle + duplicated `GRADLE_VERSION` in `codeql.yml` | The codeql-action's own bundle supports the current Kotlin; the wrapper is the Gradle version authority | Known-good CodeQL CLI | `codeql-action` default bundle; wrapper + `distributionSha256Sum` |

Consolidations: `markdownlint` to the single `.markdownlint-cli2.jsonc`
canonical config (Batch A, commit `28369eb`, proven 0 issues / 28 files);
backend verification to one job and one `npm run verify` interface;
dependency policy to the standard dependency-review action.

## 3. Retained (and why)

| Kept | Rationale |
|---|---|
| Strict dependency verification (`--dependency-verification=strict`, 686 SHA-256 entries) | The single, genuinely valuable integrity state; the audit's removal proofs (exact pins, no dynamics, working strict verification) all lean on it |
| `diagnose.yml` core: 5 modes, `diagnose-focus.mjs` validated argv, dispatch bridge, `assert-dispatch-target` | ≥50 diagnose dispatches and ≥30 bridge dispatches in two days of history — the most-used machinery in the repo. Focus input stays data-only; the exact-head assertion stays the load-bearing trust boundary |
| `cache-mode: read` on verify/diagnose | Provider-enforced cache isolation for PR-controlled code, zero maintenance |
| `tools/secret-scan` | Platform secret scanning misses generic PEM keys, raw JWTs, GCP service-account JSON — AppT-relevant patterns; 341 LOC, zero dependencies |
| `enforce-gradle-tooling-constraints.mjs` | Actively remediates real advisories on the plugin classpath (e.g. GHSA-2363-cqg2-863c on jdom2 this run); owned in one dependency-free file |
| `codeql.yml` (minimized) | Officially recommended security SAST; advanced setup is now the smallest viable form |
| Dependabot, SHA-pinned actions, version catalog + pin guard, appTGuards, CodeRabbit config, project-state contracts | All standard, cheap, or actively enforcing product invariants |

## 4. Security statements for removed security-adjacent mechanisms

- Dependency locking: prevented silent transitive-graph drift. The pinned
  catalog (guarded) plus strict verification against recorded SHA-256 hashes
  means any changed artifact — including a moved transitive — fails the build
  loudly. The attack class is covered more directly.
- `enforce-dependency-policy.mjs`: prevented vulnerable packages at PR time.
  The standard dependency-review action now runs with `fail-on-severity: low`
  and no warn-only escape; the retired jgit exception was proven unnecessary
  (root buildscript constraint already forces the patched 6.10.1 release,
  confirmed in `gradle/verification-metadata.xml`).
- Warm-state publisher: prevented cache poisoning by PR code while restoring
  speed. With it gone there is no cache-write path for PR-controlled code at
  all in verify/diagnose — the boundary got stronger, not weaker.
- workflow-cache-contract test: policed a write-capable exception that no
  longer exists; `cache-mode: read`, SHA-pinned actions and the read-only
  default token still enforce the cache boundary structurally.
- Sonar boundary machinery: protected `SONAR_TOKEN` from PR-controlled
  scanner configuration. With Sonar gone there is no secret-bearing analysis
  job at all — the threat surface was removed rather than re-guarded.
- Maintenance purge: destructive by design (`actions: write`, run deletion).
  Removing it shrinks the repository's destructive surface; GitHub's own
  retention settings manage history.

## 5. Verification evidence at final head `9f31088`

Executed in-sandbox (no Android SDK available; Android domains remain
CI-verified, unchanged by this work):

- Node suites: diagnose-focus 67/67, dispatch-workflow 38/38,
  assert-dispatch-target 16/16, enforce-gradle-tooling-constraints 18/18,
  secret-scan 10/10 — 149 pass, 0 fail.
- Python: `project-state-contract.test.py` OK (8 tests);
  `project_state_contract.py` passed; `.project-ai/skills/validate.py`
  passed (20 skills, routing coverage complete).
- `node tools/secret-scan/secret-scan.mjs`: OK, 287 tracked files.
- `node tools/security/enforce-gradle-tooling-constraints.mjs`: floor
  passed (remediation evidence emitted).
- `npm ci --prefix backend` + `npm run verify --prefix backend`: full
  backend interface green (typecheck, ESLint, Prettier, Knip, Jest 2/2,
  100% coverage reported).
- yamllint (`.github/`, repo config): clean. PyYAML parse of all four
  workflows: valid — verify 10 jobs, diagnose 6, codeql 2, agent-control 1.
- markdownlint-cli2 over all 28 tracked Markdown files: 0 issues.
- Repo-wide residual grep for every removed concept (sonar, kover, purge,
  maintenance, warm, dependency-state, backend-static/test,
  enforce-dependency-policy, lockfile, dependency-lock): remaining hits are
  only intentional history (release.md migration narrative), the retirement
  comment in verify.yml's dependency-review step, `package-lock.json`
  (npm, standard, kept), and the two deferred control-plane notes below.

## 6. Deferred / owner actions

- `.project-ai/PROJECT_STATE.md` (route `ci:dependency-state`) and
  `.project-ai/execution/verification.md` (dependency-state bullet) still
  mention the retired mode. Left for control-plane reconciliation at PR
  acceptance per the standing rule that `.project-ai` state updates are not
  authored here; both spots are exact and small.
- `macrobenchmark/gradle.lockfile` remains: its removal is owned by draft
  PR #118 (S05). If #118 does not land, deleting that one file completes the
  locking retirement.
- CodeQL Default Setup: the documented migration remains an owner action —
  the control-plane token cannot toggle it (code-scanning/default-setup API
  returns `403 Resource not accessible by integration`). The minimized advanced
  workflow records this condition in-repo.
- PR #118 (draft S05) is intentionally untouched; nothing here reintroduces
  anything retired by S05 Contract Revision 5.

## 7. Verdict

The audit found AppT materially overengineered relative to its actual
product, team and risk profile. This branch removes the over-provisioned
layers — duplicate dependency-integrity ownership, an unconsumed quality
platform and its coverage feeder, a performance cache with its own trust
machinery, destructive housekeeping with one use, and a CI split sized for a
deployable backend — while strengthening the boundaries that matter: strict
verification is now the single integrity state, dependency review is the
single PR supply-chain gate, no PR-controlled code path can write caches,
and no secret-bearing analysis job exists. Concept count, not line count,
was the metric: five fewer dependency concepts, seven fewer jobs, one fewer
workflow, one fewer secret surface, zero product-code changes.
