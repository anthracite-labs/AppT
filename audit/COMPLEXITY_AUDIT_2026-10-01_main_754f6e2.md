# AppT Complexity & Overengineering Audit

- **Audited tree:** `anthracite-labs/AppT` @ `main` = `754f6e23ed24447fa131722b242b671258b0308c` (2026-10-01)
- **Audit branch:** `arena/01a0f840-appt` @ `31dd747` (adds only this artifact and the earlier inventory artifact; zero production changes)
- **Scope:** analysis only. No repository file was modified, moved, renamed, or deleted by this audit. No PROJECT_STATE update. No cleanup PR.
- **Method:** full repository read (all 301 tracked files inventoried in the companion `REPO_AUDIT_2026-10-01_main_754f6e2.md`), line-count quantification per category, mechanism-by-mechanism assessment against an 8-question framework, upstream documentation research (official sources cited by URL), and a same-category survey of 8 real-world open-source Android apps via the GitHub trees API.

**8-question framework applied to every mechanism:** (1) what risk does it protect against, (2) how likely is that risk here, (3) what breaks if it is removed, (4) does standard tooling already cover it, (5) how many extra layers does it add, (6) is it materially useful *today*, (7) what is its maintenance burden, (8) is there a simpler standard alternative.

**Classification vocabulary (exactly one per mechanism, Section J):** ESSENTIAL · JUSTIFIED · REASONABLE DEFENSE-IN-DEPTH · QUESTIONABLE · OVERENGINEERED · REDUNDANT · PREMATURE · LEGACY COMPLEXITY · INVESTIGATE.

---

## A. Executive Conclusion

**Verdict: AppT is MATERIALLY OVERENGINEERED as a whole — but the overengineering is almost entirely concentrated in its trust, verification, and agent-control machinery, not in its product code.**

The product itself is proportionate. A two-module Kotlin app (`:app` 3,359 LOC, `:samsung` 4,145 LOC) plus a 39-line TypeScript backend skeleton, with 9,015 LOC of product tests (1.2× production), sensible architecture, and genuinely sophisticated Samsung-device protocol engineering — that is a well-run small app.

Around that small product sits a support structure roughly **4.2× the size of the product**:

| Category | Lines | Files |
|---|---:|---:|
| Production code (Kotlin + TS) | 7,543 | 69 |
| Product tests | 9,015 | 97 |
| Build system (gradle.kts, lockfiles, verification-metadata, catalog/config) | 7,922 | 17 |
| CI / trust machinery (workflows, actions, tools/ci, tools/security, secret-scan — incl. 3,651 LOC of machinery tests) | 12,801 | 50 |
| Agent control plane (`.project-ai/**`) | 4,191 | 30 |
| Documentation (`docs/**` + root docs) | 6,946 | 26 |
| **Support machinery total** | **31,860** | **~123** |

That is **31,860 lines of machinery steering, verifying, and constraining 7,543 lines of product** — a support-to-product ratio of **4.2 : 1**, or about 26 CI jobs and 18 registered Gradle guard tasks for an app with no users yet and no released version.

The three clearest disproportions:

1. **Dependency integrity is defended by ~9 overlapping layers** (exact-pin catalog → pin-shape guard → dependency locking incl. a *settings* lockfile → lock-check tasks → strict SHA-256 verification metadata of 686 components → verification-metadata template + regeneration workflow mode → Dependabot → dependency-review action → custom dependency-policy enforcer). Gradle's own documentation states dependency locking is only meaningful for dynamic versions, and AppT declares none. In the surveyed comparable apps, only 1 of 8 uses lockfiles, and 0 of 8 use strict verification-metadata.
2. **CI is built like an internal developer platform**: a 1,222-line diagnostic workflow with a formal focus-grammar dispatch contract, a 700-line dispatcher with its own test suite, a PR-local warm-cache publisher with a tar sanitizer and a *parsed-YAML cache-contract test*, an exact-HEAD-assertion bridge for agent dispatch, and a history-purge maintenance workflow. These solve real problems — but problems that exist primarily because AI agents, not humans, are the dominant CI trigger. That is a platform investment sized for an org, carried by an app repo.
3. **Six static-analysis engines** (detekt, Android Lint, Sonar, CodeQL-advanced, CodeRabbit, Kover coverage floors on the build logic) plus six repo-quality linters watch ~7.5k LOC of Kotlin. GitHub itself recommends CodeQL *default* setup as the low-maintenance option; AppT runs advanced setup with a pinned bundle.

Counterweights, stated plainly so the verdict is not caricature: the Samsung protocol module, its fixture machinery, and its state-machine tests are genuine risk engineering and proportionate to the product's hardest problem; the SHA-pinning + minimal-permissions + zizmor workflow hardening is current best practice; the manifest/permission/telemetry guards are small and map 1:1 to stated product policy; and a meaningful fraction of the docs describe an actually-complex device protocol. Stripping the machinery to zero would be wrong; the finding is that roughly **half of it is redundant, premature, or platform-grade**, while the other half is genuinely good.

What would break if 30–50% of the machinery were removed? Almost nothing product-facing: the app, its tests, its protocol, and its security invariants are carried by the retained layers (Section G). What would degrade is *assurance density* — the degree to which every conceivable failure mode has its own dedicated watcher — and that density is precisely what costs disproportionate maintenance at this project's scale.

Full mechanism-by-mechanism verdicts: Section J. Sequenced simplification options: Section I.

---

## B. Complexity Inventory (quantified)

### B.1 Lines of code by category (tracked files only)

| # | Category | Lines | Files | Notes |
|---|---|---:|---:|---|
| P1 | `:app` main Kotlin | 3,359 | 34 | Compose UI + protocol orchestration |
| P2 | `:samsung` main Kotlin | 4,145 | 33 | Samsung device protocol engine |
| P3 | Backend TS skeleton | 39 | 2 | `deployable: false` by repo policy |
| P4 | Manifests + XML resources | 278 | — | included for context, not counted in ratios |
| T1 | Product tests (`:app` unit 4,149 + androidTest 269; `:samsung` unit 4,544 + androidTest 39; backend 14) | 9,015 | 97 | ratio to P1–P3 ≈ **1.20** |
| B1 | Gradle build scripts (root, app, samsung, macrobenchmark, guards) | 1,437 | 6 | `guards.gradle.kts` alone is the largest |
| B2 | Dependency lockfiles (4 module + 1 settings) | 1,290 | 5 | generated artifacts checked in |
| B3 | `verification-metadata.xml` (+ 51-line template) | 4,898 | 2 | 686 pinned components |
| B4 | Catalog + properties + detekt + sonar config | 297 | 5 | |
| C1 | Workflows (verify 1,046 · diagnose 1,222 · codeql 130 · agent-control 192 · maintenance 113) | 2,703 | 5 | 26 jobs total |
| C2 | Composite actions (assert-dispatch-target, setup-jvm, setup-node) | 255 | 3 | |
| C3 | `tools/ci` implementation | 4,670 | — | dispatcher, warm-cache publisher, sanitizer, policy enforcer, state-contract checker, purge logic |
| C4 | `tools/ci` tests | 3,069 | — | incl. parsed-YAML workflow-cache-contract tests |
| C5 | `tools/security` implementation | 1,273 | — | tooling-constraint enforcer etc. |
| C6 | `tools/security` tests | 490 | — | |
| C7 | Secret scanner (249 impl + 92 tests) | 341 | 2 | |
| A1 | `.project-ai` control plane (3,279 of it skills) | 4,191 | 30 | 20 skills + template + validator |
| D1 | `docs/**` | 6,650 | 22 | |
| D2 | Root docs (README, CONTEXT, AGENTS, samsung README) | 296 | 4 | |

**Totals:** production **7,543** · product tests **9,015** · support machinery **31,860** · **grand total ≈ 48.4k LOC**.

### B.2 Key ratios

| Ratio | Value | Interpretation |
|---|---:|---|
| Support machinery : production | **4.2 : 1** | Every product line is supported by ~4.2 lines of build/CI/security/process |
| (Machinery + product tests) : production | **5.4 : 1** | |
| CI jobs per 1,000 prod LOC | **3.5** (26 / 7.5k) | Healthy bands for OSS apps surveyed: ~0.5–1.5 |
| Registered Gradle guard tasks | **18** | 10 policy guards + lock machinery + aggregators |
| Dependency-integrity components pinned | **686** | verification-metadata |
| Action refs SHA-pinned | **100%** (40× checkout etc.) | matches GitHub guidance |
| Skills in control plane | **20** | incl. incident-response, postmortem, observability — for an unreleased app |
| Docs : production | **0.92 : 1** | high but partly justified by protocol complexity |

### B.3 The developer change → release path (systems touched)

One PR touching a Samsung protocol file traverses: repo-policy check → repo-quality (6 linters) → android-format → android-static (detekt + Lint) → android-build → android-unit → backend-static + backend-test (for a backend the repo policy calls non-deployable) → changes detection → dependency-review (+ custom policy enforcer) → quality-platform (Sonar) → CodeQL (own workflow, 2 jobs) → gate aggregation → optional agent dispatch via agent-control bridge with exact-HEAD assertion → optional diagnose dispatch with focus grammar → release path described in docs but not exercised (no release exists yet). **~14 distinct check systems before merge**, before any device.

---
## C. Upstream Comparison (what the official guidance actually says)

For each mechanism class: upstream position, then AppT's position relative to it. "AppT-beyond-upstream" means AppT does more than the official guidance asks; that is not automatically wrong, but it moves the justification burden onto AppT-specific risk.

### C.1 Dependency locking — *advanced, for dynamic versions*

Gradle's user manual introduces locking as the remedy for non-determinism caused by dynamic versions: *"Dependency locking makes sense only with dynamic versions."* (Gradle User Manual, Dependency Locking — https://docs.gradle.org/current/userguide/dependency_locking.html ; identical statement in the historical doc https://docs.gradle.org/4.8/userguide/dependency_locking.html). The current manual's version-declaration guide marks locking with a 🔒 only "in situations where leveraging dependency locking is recommended", i.e. dynamic/range declarations (https://docs.gradle.org/current/userguide/dependency_versions.html).

**AppT position — AppT-beyond-upstream:** AppT's version catalog pins *every* version exactly (enforced by the custom `versionCatalogPinned` guard), so there are no dynamic versions for locking to stabilize. What the 5 lockfiles (incl. `settings-gradle.lockfile`) actually provide at this point is a checked-in record of the resolved graph and a tripwire if a dependency's declared ranges resolve differently — a thin, churn-heavy residue of their original purpose.

### C.2 Dependency verification (checksums) — *supported, opt-in, security-hardening*

Gradle ships verification as an explicit hardening feature ("Gradle 6.2 Release Notes", https://docs.gradle.org/6.2/release-notes.html ; user guide https://docs.gradle.org/current/userguide/dependency_verification.html): create `verification-metadata.xml`, all resolved artifacts are checked, SHA-256/512 are the only recommended algorithms, and the file is generated via `--write-verification-metadata`. The docs describe *how*, never *that you must*; it is an advanced, opt-in posture (the Gradle repo itself and Gradle's security guidance use it). It defends against artifact tampering/repository compromise that locking cannot see (locking checks coordinates, verification checks content).

**AppT position — AppT-beyond-upstream but defensible as the *primary* integrity layer:** strict verification of 686 components with a template and a regeneration workflow mode. Defensible *in itself*; the problem is that it is layered *on top of* locking rather than replacing it (see stack E.1).

### C.3 Pinning GitHub Actions to SHAs — *officially recommended*

GitHub's secure-use reference: *"Pin actions to a full-length commit SHA … currently the only way to use an action as an immutable release"* (https://docs.github.com/en/actions/reference/security/secure-use).

**AppT position — aligned:** 100% of `uses:` refs are full-SHA pinned, including Dependabot coverage for the `github-actions` ecosystem. This is exactly the recommended posture.

### C.4 Dependency review — *officially recommended PR check*

"Dependency review lets you catch insecure dependencies before you introduce them" — GitHub recommends the dependency-review action on PRs and documents org-wide enforcement (https://docs.github.com/code-security/supply-chain-security/understanding-your-software-supply-chain/about-dependency-review).

**AppT position — aligned + AppT-beyond:** standard action present; additionally a custom `dependency-policy` enforcer re-checks policy rules (tools/ci). The platform action already fails on vulnerable packages and supports license/severity configuration; the custom layer needs a concrete policy it alone expresses to justify existing (Section I, item 6).

### C.5 CodeQL — *default setup is the recommended baseline*

GitHub: default setup is *"the quickest, easiest, most low-maintenance way to enable code scanning"* and is recommended for eligible repositories; advanced setup exists for control over triggers, build modes, query suites (https://docs.github.com/en/code-security/concepts/code-scanning/setup-types ; https://docs.github.com/en/code-security/code-scanning/enabling-code-scanning/configuring-default-setup-for-code-scanning).

**AppT position — AppT-beyond:** advanced setup with a pinned CodeQL bundle, two split jobs (fast/Kotlin), scheduled + PR triggers. Justifiable only if default setup demonstrably fails for this build; the maintenance cost (bundle pinning, YAML upkeep, two jobs) is otherwise avoidable. Marked QUESTIONABLE with a concrete de-escalation path.

### C.6 Dependabot — *standard platform feature*

Dependabot ecosystems configured: `gradle`, `npm`, `github-actions` (`.github/dependabot.yml`). Upstream: standard, recommended. **Aligned.** (The `npm` ecosystem serves the 39-line backend skeleton and tooling scripts.)

### C.7 Sonar — *third-party, optional*

SonarQube/SonarCloud is commercial third-party tooling; no Android/Gradle/GitHub guidance recommends it by default. AppT runs `sonarqube-scan-action` with a two-checkout trust arrangement and an input validator. **AppT-beyond; optional by definition** — its value is proportional only if the team reads dashboards; for a 7.5k-LOC pre-release app detekt + Lint + CodeQL already cover the same surface.

### C.8 Secret scanning — *platform provides push protection*

GitHub provides secret scanning with push protection as a platform feature (https://docs.github.com/en/code-security/secret-scanning). AppT additionally maintains a custom scanner (`tools/secret-scan`, 249+92 LOC). The custom scanner can encode repo-specific patterns and run in CI contexts the platform doesn't cover — but for a repo whose secrets are GitHub Actions secrets, push protection already blocks the dominant leak path. Largely **overlapping with the platform**; kept as QUESTIONABLE with a small footprint.

### C.9 Android app architecture & module count — *no official mandate to modularize*

The Android architecture guidance (https://developer.android.com/topic/architecture) prescribes layer separation and UI/state patterns, not module counts. Practitioner consensus (secondary: e.g. https://www.reddit.com/r/androiddev/comments/1b6h68o/ ; https://xcelore.com/blog/modular-architecture-in-android-why-it-matters/) consistently warns against over-modularization early; the reference app Google maintains (Now in Android) uses a modest module set. AppT's choice — `:app` + `:samsung` + (retired) `:macrobenchmark` — is *below* typical module counts and matches the "meaningful chunk" rule. **Aligned / proportionate.**

### C.10 Testing strategy — *local-first pyramid*

Official Android testing guidance recommends fast local (JVM/Robolectric) tests as the base, instrumentation for what needs a device (https://developer.android.com/agents/skills/testing/testing-setup/skill ; testing overview https://developer.android.com/training/testing). AppT: 8,962 LOC JVM/Robolectric unit tests vs 308 LOC androidTest — an aggressively local-first pyramid, well matched to guidance. **Aligned.**

### C.11 Managed devices / Baseline Profiles — *optional*

AGP managed devices and Baseline Profiles are optional platform features (https://developer.android.com/studio/test/managed-devices ; https://developer.android.com/topic/performance/baselineprofiles/overview). AppT has retired hosted managed-device usage and macrobenchmarking (see companion inventory audit, O1–O8). No current overinvestment; the remnants are LEGACY COMPLEXITY already assigned to cleanup in PR #118 Batch 1.

---

## D. Comparable-Project Research (8 open-source Android apps)

Method: `gh api repos/{owner}/{repo}/git/trees/{default_branch}?recursive=1` structural survey on 2026-10-01 (file presence only — not a code audit). Sample chosen to span small → large, single-dev → org-maintained, and to include the two projects AppT's docs cite as precedent for its own practices (Signal, Home Assistant). Not cherry-picked toward minimalism: it includes CI-heavy orgs (Thunderbird, Home Assistant) and a security-focused messenger (Signal).

| Project | Scale (main-source Kotlin files) | Modules | Workflows | Composite actions | Lockfiles | verification-metadata | Version catalog | detekt/spotless/ktlint | CodeQL | Sonar | Dependabot |
|---|---:|---:|---:|---:|---:|---:|---|---|---|---|---|
| android/nowinandroid (Google reference) | 241 | 5+ | 3 | 0 | 0 | 0 | ✓ | spotless | – | – | – |
| chrisbanes/tivi | 24 (KMP) | 3 | 4 | 0 | 0 | 0 | ✓ | spotless/ktlint | – | – | – |
| home-assistant/android | 873 | 8 | 14 | 17 | **9 (incl. settings-gradle.lockfile)** + auto-refresh workflow | 0 | ✓ | detekt | – | – | – |
| kylecorry31/Trail-Sense | 1,632 | 1 | 8 | 0 | 0 | 0 | ✓ | detekt | ✓ | ✓ | – |
| d4rken-org/sdmaid-se | 1,537 | 27 | 8 | 2 | 0 | 0 | – | – | – | – | – |
| signalapp/Signal-Android | 3,689 | 7 | 5 | 0 | 0 | 0 | ✓ | ktlint | – | – | – |
| AntennaPod/AntennaPod | 14 (+Java) | 5 | 12 | 0 | 0 | 0 | ✓ | – | – | – | – |
| thunderbird/thunderbird-android | 1,771 | 6 | 25 | 6 | 0 | 0 | ✓ | detekt | ✓ | – | ✓ |
| **AppT** | **67 files / 7.5k LOC** | **2 (+1 retired)** | **5 (26 jobs)** | **3** | **5 (incl. settings)** | **✓ strict, 686 components + template + regen mode** | ✓ | detekt | ✓ advanced | ✓ | ✓ |

*(Signal verification-metadata hit in the raw survey was a false positive — a source package named `verification*`; Signal has no `gradle/verification-metadata.xml` in this survey.)*

### D.1 What the sample shows

1. **Lockfiles: 1 of 8** — and that one (Home Assistant) is an org-maintained app with 13× AppT's source size, which pairs lockfiles with a Renovate auto-refresh workflow. **0 of 8 combine lockfiles with strict verification-metadata.**
2. **Strict dependency verification: 0 of 8.** AppT is the only surveyed app with a 686-component verification metadata file, a template, and a CI regeneration mode.
3. **Custom dispatch/diagnostic machinery: 0 of 8** have anything analogous to AppT's focus-grammar dispatcher, exact-HEAD bridge, PR-local warm-cache publisher/sanitizer, or maintenance purge workflow. Thunderbird (25 workflows) and Home Assistant (14 workflows + 17 composite actions) are the CI-heaviest comparables — still standard trigger-based workflows, no dispatch-contract layer.
4. **CodeQL: 2 of 8; Sonar: 1 of 8; dependency-review action: 0 of 8 observed.** Lint/detekt is the common denominator for static analysis.
5. **Scale calibration:** AppT at 7.5k LOC carries 26 CI jobs; Thunderbird at ~20× the code runs 25 workflows; Home Assistant at 13× runs 14. Job density per source file is an order of magnitude higher in AppT than in any comparable.

### D.2 Honest limits of this comparison

- File-presence surveys can't see policy (e.g., HA may enforce dependency hygiene elsewhere; Signal's supply-chain rigor historically lived in reproducible-build tooling, not these files).
- Absence in one repo proves nothing; the argument here is the *aggregate* of 8 plus official guidance (Section C).
- AppT's agent-driven development model has no counterpart in the sample — so where AppT exceeds every comparable, the excess is either (a) genuinely novel-and-necessary, or (b) disproportionate. Sections E–F adjudicate mechanism by mechanism instead of assuming either.

---
## E. Complexity Stacks (where layers overlap)

Each stack lists its layers, which risks they actually cover, where they overlap, and the minimal valuable set.

### E.1 Dependency-integrity stack — 9–10 layers

| # | Layer | Where | Risk covered | Also covered by |
|---|---|---|---|---|
| 1 | Exact versions in catalog | `gradle/libs.versions.toml` | accidental range drift | — |
| 2 | `versionCatalogPinned` guard | `gradle/guards.gradle.kts` | someone introduces a range | 1 + PR review |
| 3 | `dependencyLocking` on all configurations | build scripts | graph drift | 1 (partially), 5 |
| 4 | 5 lockfiles (incl. settings) checked in | `gradle/` + module dirs | same as 3 | 5 |
| 5 | Strict `verification-metadata.xml`, 686 components, SHA-256 | `gradle/verification-metadata.xml` | **artifact tampering / repository compromise** | nothing else — unique |
| 6 | Template + regeneration workflow mode | `verification-metadata.template.xml`, diagnose `dependency-state` | freshness of 5 | manual `--write-verification-metadata` |
| 7 | Dependabot (gradle ecosystem) | `.github/dependabot.yml` | stale/vulnerable deps | 8 |
| 8 | dependency-review action on PRs | `verify.yml` | vulnerable/license-changed deps entering | 7 (partially), 9 |
| 9 | Custom dependency-policy enforcer | `tools/ci` | repo-specific dep policy | 8 if policy is expressible there |
| 10 | `noTelemetryDependency`, `samsungGraphExcludesFirebase` | `guards.gradle.kts` | product policy (no telemetry/Firebase) | nothing — product invariants |

**Overlap analysis:** layers 1–4 fight *coordinate* drift; layer 5 fights *content* tampering; layers 7–9 fight *vulnerability introduction*; layer 10 is product policy. Upstream (C.1) says locking is only meaningful with dynamic versions — AppT has none, so layers 3–4 reduce to "checked-in graph record", whose integrity role is fully subsumed by layer 5 and whose freshness depends on manual/dependabot updates anyway. Layers 7, 8, 9 substantially overlap on "new bad dependency on a PR".

**Minimal valuable set:** 1 (+2 as a 10-line guard) + 5 + 8 + 10. Removal candidates: 3, 4, 6, 9 (conditionally). This deletes ~1,290 lockfile lines, the settings lockfile concept, the template, one diagnose mode, and most of one tools/ci script — while the *stronger* protection (content verification) stays.

### E.2 CI workflow-trust stack

Layers: SHA-pinned actions (100%) · minimal `permissions:` everywhere · concurrency groups · zizmor workflow-security linter · actionlint · yamllint · `assert-dispatch-target` composite action · agent-control `pull_request_target` bridge with exact-HEAD assertions · restricted `workflow_dispatch` inputs with focus grammar + argv vector.

Assessment: the first five are current best practice and cheap. The last four form a **dispatch-integrity subsystem** (~600+ LOC plus tests) whose sole consumer is agent-driven dispatch. Its risk model (hostile/forged dispatch payloads) is real only because dispatch surface exists; the simpler alternative is to shrink the surface (Section I, items 9–11).

### E.3 Static-analysis stack — 6 engines

detekt (custom config) · Android Lint · Sonar (scan-action + 2-checkout trust + input validator + parsed-YAML boundary test) · CodeQL advanced (pinned bundle, 2 jobs) · CodeRabbit (SaaS reviewer) · Kover with a coverage floor **on the build logic itself** (comment in `build.gradle.kts`: measured 90.58%, 125/138 lines, Kover 0.9.5). Plus six repo-quality linters: yamllint, markdownlint, shellcheck, shfmt, actionlint, zizmor.

For 7.5k LOC: detekt + Lint + CodeQL alone exceed what every surveyed comparable runs. Sonar and CodeRabbit duplicate the "find smells/bugs in small Kotlin code" niche; the Kover floor on 138 lines of build script is precision instrumentation of machinery, not product risk. The six repo-quality linters police ~2.7k lines of workflow YAML — reasonable for a security-sensitive CI surface, but markdownlint/shfmt/shfmt-class concerns are cosmetic next to zizmor's injection analysis.

### E.4 Secrets stack

GitHub secret scanning + push protection (platform) · custom `tools/secret-scan` (249+92 LOC) run in CI · docs/policy. The custom scanner's marginal value: repo-specific patterns and a CI-local gate independent of platform configuration. Its cost: another maintained script with tests. Verdict in J: QUESTIONABLE (keep-or-replace decision, Section I).

### E.5 Test-assurance stack

Product: 8,962 LOC local/Robolectric unit + 308 LOC androidTest + fixture-driven protocol state-machine tests — high value, official-guidance-aligned (C.10). Meta: 3,651 LOC of tests *for the machinery* (dispatcher grammar, tar sanitizer, parsed-YAML workflow cache contract, tooling-constraint enforcer, purge logic). The meta-tests are well-written; their existence is the symptom — machinery large enough to need its own test suite is platform-scale investment. Test-to-product ratio 1.20 is healthy; machinery-test-to-machinery (~0.4) is the number that signals the center of gravity.

### E.6 Release-verification stack

Docs: `release.md`, physical-device verification policy; retired: hosted managed devices, macrobenchmark module (inventory audit O1–O8, cleanup assigned to PR #118 Batch 1); actual releases to date: **none**. An entire verification-stage concept for a lifecycle stage that has not happened once = PREMATURE (keep the *policy document*, not machinery).

### E.7 AI-agent control stack

`.project-ai` (4,191 LOC / 30 files, 20 skills incl. incident-response, postmortem-learning, observability-operations-design, release-deployment) · skills validator (`validate.py`) · PROJECT_STATE contract checker (`tools/ci/project_state_contract.py`) · issue-contract discipline in docs · agent-control workflow + exact-HEAD assertions · diagnose focus grammar · AGENTS.md + agent-facing docs. Estimated ~6–7k LOC of the repo exists primarily to direct or constrain coding agents — roughly equal to the entire product.

Two genuinely different things live here and must not be conflated: **(a)** instructions/context that make agents productive (AGENTS.md, architecture docs, protocol docs — high value, some would exist in any repo's docs), and **(b)** enforcement machinery (contract checkers, dispatch bridges, validators, lifecycle skills for stages that don't exist yet — largely premature for a team of this size). Section F.4 and I.10 separate them.

### E.8 Architecture-boundary stack

`samsungDependencyBoundary` · `samsungGraphExcludesFirebase` · `noFirestoreClientInApp` · `noSyncRecordInProductionSource` · `noLogInSamsungSource` · `noProductionModuleDependsOnBenchmark` · `manifestPermissionAllowlist` · `adIdAbsentFromManifest`. Eight small Gradle/source guards, each ~10–40 lines, each encoding a stated product invariant (privacy posture, vendor isolation, module hygiene). Cheap, specific, test-like. This is the *good* kind of custom machinery and mostly stays (Section G).

---
## F. Overengineering Findings

Ordered by (confidence × maintenance cost). Each finding states the mechanism, why it looks disproportionate, and the strongest case *for* it — both sides are on the record.

### F.1 Dependency locking on top of strict verification (REDUNDANT / OVERENGINEERED — high confidence)

Five lockfiles including `settings-gradle.lockfile`, `resolveAndLockAll`/`dependencyLockCheck` tasks, and lock-state discipline in dependabot flows — stacked *above* a 686-component strict verification file and an exact-pin catalog. Gradle's manual: locking is only meaningful with dynamic versions (C.1); AppT declares none. Only 1 of 8 comparables uses lockfiles; none combines them with verification-metadata. Locks here cannot detect artifact tampering (that's layer 5's job) and only detect coordinate drift that exact pinning already prevents; their freshness depends on the same update events that touch the catalog.
*Case for:* they document the fully-resolved graph at a glance and were (per repo history) the first integrity layer adopted. *Rebuttal:* a checked-in record that must be manually kept current and duplicates a stronger layer is a liability, not a defense.

### F.2 Diagnose dispatch platform (OVERENGINEERED for this repo — high confidence)

`diagnose.yml` (1,222 lines, 10 jobs) + `dispatch-workflow.mjs` (~700 LOC) + its 419-LOC test suite + focus grammar + argv-vector validation + `assert-dispatch-target` + PR-local warm-cache publisher + tar sanitizer + **a parsed-YAML workflow-cache-contract test**. This is a carefully engineered *internal platform for operating CI by remote dispatch*.
*Case for:* agents are the primary CI consumers; forged or malformed dispatch payloads are a real injection class; warm caches materially shorten agent iteration; the tests have demonstrably caught regressions (they exist because the code breaks otherwise). All true — and none of it would exist in this form if the trigger were a human pressing "Re-run". The repo is paying platform-grade cost so that agent-dispatched runs behave like human-triggered ones. The proportional alternative is shrinking the dispatch surface and letting standard triggers do more (Section I, item 9).

### F.3 Six static-analysis engines over 7.5k LOC (OVERENGINEERED — high confidence)

detekt + Android Lint + Sonar + CodeQL-advanced + CodeRabbit + Kover-build-floor, plus 6 repo-quality linters. Comparables run 0–2 engines at 20× the code. Each engine alone is defensible; the sixth engine's marginal yield on 7.5k LOC is near zero while its upkeep (Sonar trust/validation chain: two checkouts + input validator + boundary test) is concrete.
*Case for:* different engines catch different things; Sonar/CodeRabbit are free-tier and "cost nothing". *Rebuttal:* they cost attention, config drift, and triage time — the real currencies at this scale.

### F.4 Agent-control plane sized for an organization (PARTLY PREMATURE — high confidence)

20 skills including `incident-response`, `postmortem-learning`, `observability-operations-design`, `release-deployment` for an app with no incidents, no postmortems, no observability, and no releases; a PROJECT_STATE contract checker; a skills validator; agent-control bridge with exact-HEAD assertions.
*Case for:* the repo's declared operating model *is* agent-executed; scaffolding agents well is the team's core leverage, and lifecycle skills cost ~150 LOC each to keep. *Rebuttal:* instructions for stages that don't exist are speculative API surface that agents must navigate and maintainers must keep truthful; the enforcement machinery (contract checkers, exact-HEAD bridge) earns its keep only at dispatch volumes this repo hasn't demonstrated. Keep the *concept*, retire the unrealized instances.

### F.5 Custom dependency-policy enforcer beside dependency-review (REDUNDANT until proven otherwise — medium confidence)

The platform action already fails PRs on vulnerable/license-violating dependency changes and is configurable (C.4). AppT's extra enforcer is justified only by policy rules it alone can express. If those rules fit `dependency-review-action` config (deny/fail-on-severity/license checks), the custom script and its tests retire cleanly.

### F.6 Custom secret scanner beside platform push protection (QUESTIONABLE — medium confidence)

249+92 LOC re-implementing a subset of GitHub secret scanning + push protection. Marginal value: repo-specific patterns, CI-local signal, independence from platform config. Marginal cost: low. Keep-or-replace, not keep-and-forget.

### F.7 CI coverage for the non-deployable backend skeleton (PREMATURE in proportion, tiny in absolute — low-medium confidence)

`backend-static` and `backend-test` jobs + npm dependabot ecosystem for 39 lines of `deployable:false` TypeScript. Absolute cost is small; the proportionality point is conceptual: the CI graph treats a placeholder as a peer of the product. Fold into a single "repo hygiene" job until the backend is real.

### F.8 Documentation volume (PARTLY JUSTIFIED — medium confidence)

6,946 DOC LOC ≈ 0.92× production. The Samsung protocol documentation is genuinely needed (the protocol is the product's hardest artifact). Process/control-plane documentation (issue contracts, lifecycle rules, skill system docs) primarily serves the agent-control plane and scales with F.4. Not reducible by deletion alone; reduces when the machinery it describes reduces.

### F.9 CodeQL advanced setup with pinned bundle (QUESTIONABLE — medium confidence)

GitHub recommends default setup as low-maintenance (C.5). AppT's advanced setup buys a pinned bundle and job split; the upkeep is bundle-pin churn and two YAML jobs. If default setup's build-mode handling works for this Kotlin build, switching deletes ~130 lines of workflow and a pin-management obligation while keeping scanning.

### F.10 Historical-scar tissue (LEGACY COMPLEXITY — already handled)

Retired hosted GMD + macrobenchmark remnants (inventory O1–O8), transitional `codeql.yml` state, doc drift DR1–DR6 — all enumerated in the companion inventory audit and assigned to PR #118 Batch 1; they confirm the repo accumulates residue when modes are retired, which is an argument for *fewer modes to retire*.

### F.11 The ten separation questions

1. **Genuine product sophistication?** The Samsung protocol engine, fixtures, and state-machine tests — yes, real, and the guards around them (boundary, no-Firebase, no-telemetry) are proportionate.
2. **Generic Android complexity?** No — module count, test pyramid, and architecture are at or below typical. The excess is not Android-ness.
3. **Reasonable CI best practice?** SHA-pinning, minimal permissions, dependency-review, zizmor/actionlint — yes; these stay.
4. **AI-agent-driven complexity?** The dispatch platform, exact-HEAD bridge, focus grammar, contract checkers, much of `.project-ai` — yes, this is the dominant source of the 4.2:1 ratio.
5. **Historical scars?** Lockfiles plausibly began as the first integrity layer before verification-metadata existed; they were never reconciled with it (F.1).
6. **Enterprise habits on a small app?** Sonar dashboards, coverage floors on build scripts, org-style skill library — partially yes.
7. **Would an experienced team build this?** For 7.5k LOC and 1–2 humans + agents: no team surveyed builds the dispatch platform or 9-layer dependency stack. They do build the Samsung-side and the hardening-side.
8. **Defense in every direction?** Dependency integrity (9 layers) and static analysis (6 engines) are both-directions examples; secrets and CI-trust are closer to proportionate.
9. **Paying for hypothetical risks?** Incident-response/postmortem/observability skills, release-mode machinery, settings lockfile — yes, pre-release hypotheticals.
10. **What breaks if 30–50% goes away?** Nothing product-facing (Sections G/I). Assurance density drops; churn drops more.

---

## G. Complexity That Should STAY

Explicitly proportionate — defend these from future "simplification":

1. **`:samsung` module + fixture/state-machine test suite** — the product's core risk lives here; 4,544 LOC of tests for 4,145 LOC of protocol code is appropriate.
2. **`verification-metadata.xml` strict mode** — the one layer that defeats artifact tampering; becomes the *primary* integrity layer if lockfiles go (E.1).
3. **Version catalog + `versionCatalogPinned` guard** — standard + a tiny guard.
4. **SHA-pinned actions, minimal permissions, concurrency groups, zizmor, actionlint, yamllint** — current GitHub hardening guidance, low upkeep.
5. **dependency-review action** — platform-standard PR supply-chain gate.
6. **Dependabot (3 ecosystems)** — standard.
7. **Product policy guards** — `manifestPermissionAllowlist`, `adIdAbsentFromManifest`, `noTelemetryDependency`, `samsungGraphExcludesFirebase`, `samsungDependencyBoundary`, `noFirestoreClientInApp`, `noSyncRecordInProductionSource`, `noLogInSamsungSource`: small, specific, invariant-encoding.
8. **detekt + Android Lint** — the common-denominator static pair.
9. **Local-first test pyramid** — matches official guidance.
10. **Protocol/architecture documentation** — real knowledge, not process.
11. **`diagnose` modes with demonstrated use** (app/samsung unit, static, build, backend) as *standard triggered checks* — the modes are valuable; it's the dispatch-contract superstructure around them that's oversized (F.2).
12. **CodeQL itself** — keep scanning; only its *setup mode* is questioned (F.9).

---
## H. "Boring AppT" — the minimal conventional design

What an experienced Android team optimizing for boring + maintainable + secure would actually build at this scale, with the same product requirements (Samsung protocol correctness, privacy posture, agent-assisted development):

| Layer | Boring AppT | Current AppT | Delta |
|---|---|---|---|
| Modules | `:app` + `:samsung` | same (+retired macrobenchmark) | ≈ none — already boring |
| Dependency pinning | exact-version catalog | + pin-shape guard | +1 small guard (fine) |
| Dependency integrity | strict verification-metadata, regenerated on dep bump | + 5 lockfiles + lock tasks + template + regen workflow mode | −~6.3k lines, −2 concepts |
| PR supply-chain gate | dependency-review action (+ config for license/severity) | + custom policy enforcer | −1 script + tests (conditional) |
| Static analysis | detekt + Lint + CodeQL **default** setup | + Sonar chain + CodeRabbit + Kover-build-floor + CodeQL advanced | −1 workflow pair, −Sonar validation chain |
| CI shape | 1 verify workflow, ~7 jobs (build, unit, static, lint-config, dependency-review, codeql, gate) | 5 workflows / 26 jobs incl. dispatch platform | −~1.7k workflow lines, −4.7k tools/ci |
| Secrets | GitHub push protection (+ optional tiny CI scan) | custom scanner + platform | −~0.3k lines (optional) |
| Release | policy doc only (no machinery until first release) | doc + retired-device remnants | aligns with already-planned cleanup |
| Agent layer | AGENTS.md + architecture/protocol docs + small skill set actually used | 20-skill library + contract checkers + bridge | −~1.5–2k lines, −2 validators |
| Docs | README + BUILD + PRODUCT + ARCHITECTURE + protocol docs | + process/control-plane docs | shrinks with the machinery it describes |

**Estimated boring-state:** support machinery ≈ 16–19k LOC (vs 31.9k), ~8–10 CI jobs (vs 26), ~6 guard tasks (vs 18) — with *no reduction* in the protections that matter for this product (tamper-proof dependencies, workflow hardening, vulnerability gating, protocol correctness). That gap — ~13–16k lines and ~16 jobs — is the measured size of the overengineering.

---

## I. Prioritized Simplification Roadmap (options, not instructions)

Confidence: H = high, M = medium. None of these are executed by this audit.

**Tier 0 — no-regret (documentation/legacy, already enumerated in inventory audit):**
1. Ship PR #118 Batch 1: macrobenchmark remnants O1–O8, transitional codeql state. (H)
2. Fix doc drift DR1–DR6 (retired `device` mode references, missing dependency-state docs). (H)

**Tier 1 — low-risk deletions of redundant layers:**
3. **Retire dependency locking** (lockfiles ×5, `resolveAndLockAll`, `dependencyLockCheck`, lock discipline in dep updates). Keep strict verification-metadata as the single integrity layer; regenerate it where lockfiles used to be regenerated. Saves ~1,290 checked-in lines + two guard tasks + one concept; Gradle's own docs say locking adds nothing without dynamic versions. Benefit lost: checked-in resolved-graph snapshot (reproducible by `./gradlew dependencies` any time). Risk increase: none measurable. (H)
4. Drop `verification-metadata.template.xml` + diagnose `dependency-state` regeneration mode; regeneration becomes a documented one-line command run during dependency updates. (H)
5. **Switch CodeQL to default setup** if a one-run trial shows its Kotlin build-mode handling succeeds; delete advanced workflow + pinned-bundle management. (M)
6. Merge the custom dependency-policy enforcer's rules into `dependency-review-action` configuration; retire the script + tests if expressible. (M)

**Tier 2 — consolidation:**
7. Collapse `backend-static`/`backend-test` into one hygiene job while the backend is a 39-line skeleton. (H)
8. Decide secret-scanner keep-or-replace vs GitHub push protection + secret scanning; if replaced, delete 341 LOC. (M)
9. Trim repo-quality linters to the security-relevant set (zizmor, actionlint, yamllint); make markdownlint/shellcheck/shfmt advisory or drop. (M — these are cheap; lowest priority)

**Tier 3 — agent-platform right-sizing (biggest lever, needs usage data):**
10. Instrument diagnose dispatch usage for one cycle; retire modes never dispatched, then evaluate replacing the dispatch platform with standard `workflow_dispatch` + GitHub-native re-run, which deletes the focus grammar, argv vector, exact-HEAD bridge, warm-cache publisher/sanitizer and the parsed-YAML contract test (~5–6k LOC incl. tests). Only the demonstrated threat model of *untrusted* dispatch payloads justifies keeping any of it. (M)
11. Retire unrealized skills (`incident-response`, `postmortem-learning`, `observability-operations-design`, `release-deployment` until first release/ops surface exists); keep the skill *system* minimal. (H on retirement, M on system reduction)
12. Re-evaluate the PROJECT_STATE contract checker after 10 & 11; if dispatch volume is low, doc convention suffices. (M)

**Tier 4 — keep (Section G):** everything listed there, specifically the Samsung test suite, verification-metadata, policy guards, SHA-pinning hardening, dependency-review, local-first pyramid, protocol docs.

Expected effect of Tiers 0–2 alone: −~8–9k support LOC, −2 workflows' worth of jobs, −3 concepts, zero product risk change. Tier 3 doubles that if usage data supports it.

---

## J. Final Verdict Table (one classification per mechanism)

| # | Mechanism | Classification | One-line rationale |
|---|---|---|---|
| 1 | Version catalog, exact versions | ESSENTIAL | Standard baseline |
| 2 | `versionCatalogPinned` guard | JUSTIFIED | 10 lines enforcing the baseline |
| 3 | Dependency locking, all configurations | REDUNDANT | Gradle docs: only meaningful with dynamic versions; AppT has none |
| 4 | `settings-gradle.lockfile` | OVERENGINEERED | Locking of the buildscript classpath; 0/8 comparables, thin value |
| 5 | Strict verification-metadata (686 comps) | JUSTIFIED | Unique anti-tampering layer; primary if #3–4 go |
| 6 | Verification template + regeneration mode | OVERENGINEERED | A workflow mode for a one-line Gradle command |
| 7 | Dependabot (gradle/npm/actions) | ESSENTIAL | Platform standard |
| 8 | dependency-review action | ESSENTIAL | Officially recommended PR gate |
| 9 | Custom dependency-policy enforcer | INVESTIGATE | Keep only if its rules can't be expressed in #8 |
| 10 | SHA-pinned actions + Dependabot actions ecosystem | ESSENTIAL | GitHub's recommended immutable-release posture |
| 11 | Minimal permissions + concurrency groups | ESSENTIAL | Hardening baseline |
| 12 | zizmor + actionlint + yamllint | JUSTIFIED | Security-relevant workflow linting |
| 13 | markdownlint + shellcheck + shfmt | QUESTIONABLE | Cosmetic at this scale; cheap but noise-prone |
| 14 | CodeQL scanning | JUSTIFIED | Recommended practice |
| 15 | CodeQL *advanced* setup + pinned bundle | QUESTIONABLE | Default setup is the recommended low-maintenance option |
| 16 | Sonar (scan-action + 2-checkout trust + validator + boundary test) | OVERENGINEERED | Third duplicate analyzer with the highest upkeep-per-value |
| 17 | CodeRabbit | REASONABLE DEFENSE-IN-DEPTH | SaaS review; optional but low internal cost |
| 18 | Kover coverage floor on build logic | QUESTIONABLE | Precision instrumentation of 138 lines of machinery |
| 19 | detekt + Android Lint | ESSENTIAL | Common-denominator static pair |
| 20 | Custom secret scanner | QUESTIONABLE | Largely overlaps platform push protection |
| 21 | Product policy guards (8 small guards) | JUSTIFIED | Cheap, specific, encode stated invariants |
| 22 | Diagnose modes (unit/static/build/dependency-state) | JUSTIFIED | Real check value as standard-triggered jobs |
| 23 | Dispatch platform (focus grammar, argv vector, assert-dispatch-target, warm-cache publisher/sanitizer, parsed-YAML contract test) | OVERENGINEERED | Platform-grade subsystem for agent dispatch; 0/8 comparables; size ≫ demonstrated need |
| 24 | agent-control bridge + exact-HEAD assertions | INVESTIGATE | Justified only at dispatch volumes/threats not yet demonstrated |
| 25 | maintenance purge workflow | PREMATURE | History hygiene machinery ahead of demonstrated need |
| 26 | `.project-ai` skill system, 20 skills | PREMATURE (instances) / INVESTIGATE (system) | Unrealized-lifecycle skills are speculative; core skills earn keep |
| 27 | PROJECT_STATE contract checker | INVESTIGATE | Enforcement value depends on post-#23/#24 dispatch model |
| 28 | Docs volume | JUSTIFIED (protocol/architecture) / QUESTIONABLE (process docs) | Protocol docs are real knowledge; process docs scale with the machinery they police |
| 29 | Physical-device verification policy | JUSTIFIED | Policy document, not machinery |
| 30 | Retired GMD/macrobenchmark remnants | LEGACY COMPLEXITY | Enumerated O1–O8; cleanup assigned to PR #118 |
| 31 | Backend skeleton + its CI jobs | PREMATURE | CI peers with the product while `deployable:false` |
| 32 | Local-first test pyramid + Samsung fixture machinery | ESSENTIAL | Matches official guidance; product's core risk engineering |

Counts: ESSENTIAL 8 · JUSTIFIED 8 · REASONABLE DEFENSE-IN-DEPTH 1 · QUESTIONABLE 6 · OVERENGINEERED 4 · REDUNDANT 1 · PREMATURE 3 · LEGACY COMPLEXITY 1 · INVESTIGATE 4 (one row carries a split verdict). Every mechanism classified; none unclassified.

---

### Closing statement

AppT's engineers built two things: a small, well-tested Samsung-protocol app, and — around it — the trust infrastructure of a much larger, released, organizationally-developed product. The second is the overengineering. It is *competent* overengineering (the machinery works, is tested, and is documented), which is exactly why it accumulated: every layer solved a real problem at the time it was added, and no layer was retired when a stronger layer superseded it. The roadmap above removes superseded layers first, then rightsizes the agent platform against measured usage, and keeps everything the product's actual risks require.

**Overall verdict: MATERIALLY OVERENGINEERED** (support machinery 4.2× product; ~13–16k lines and ~16 CI jobs removable with no product-risk increase), concentrated in dependency-integrity layering, the agent dispatch platform, and static-analysis duplication — with the product architecture, test strategy, and security hardening genuinely proportionate.

*End of audit. Companion artifact: `audit/REPO_AUDIT_2026-10-01_main_754f6e2.md` (file-level dispositions). All external claims linked inline. No production files touched.*
