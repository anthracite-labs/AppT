# AppT Repository Audit — 2026-10-01

**Audit scope:** AUDIT ONLY. No repository files were deleted, moved, renamed, refactored, or modified by this audit. No PROJECT_STATE update, no cleanup PR, no fixes. The only new artifact is this report (`audit/` directory), written on the Arena working branch as instructed.

---

## 1. Executive summary

### Exact repository state audited

| Item | Value |
|---|---|
| Repository | `anthracite-labs/AppT` |
| Branch audited | `main` (checkout `arena/01a0f840-appt` was created from `main` and points at the same commit) |
| Exact commit SHA | `754f6e23ed24447fa131722b242b671258b0308c` |
| Identity of revision | Tip of `origin/main`: the merge of **PR #135** "Retire benchmark and hosted-device plumbing" (`control/retire-hosted-device-plumbing`), merged 2026-10-01T15:06:47Z |
| Checkout classification | Neither a PR branch nor a feature branch: this audit is of **exact current `main`**. The working branch `arena/01a0f840-appt` contains zero commits beyond `main`. |
| Active Issue/PR context | **Issue #117** (S05 — Capability-driven Remote surface, Settings shell, and diagnostic recorder), open; **PR #118** (S05 implementation), OPEN/DRAFT, head `arena/01a0f304-appt` (not audited here — see below) |
| `.project-ai/PROJECT_STATE.md` objective | "Complete S05 — Capability-driven Remote surface, Settings shell, and diagnostic recorder — under the accepted Issue #117 contract on the existing implementation PR #118." |

**Which revision this audit covers:** the user's request allows auditing either `main` or the active S05 branch/PR #118. This audit covers **`main` at `754f6e2`** (the arena working branch carries no additional commits). Findings are therefore stated against main. Where a finding is about work assigned to PR #118 by PROJECT_STATE ("Next Authorized Action"), that is called out explicitly; no evidence from PR #118's head was mixed in.

Mandatory repo-local instructions loaded before auditing: `.project-ai/PROJECT_STATE.md`, `.project-ai/routing/route.md`, `.project-ai/routing/capabilities.md`, `.project-ai/bootstrap/project.md`, `.project-ai/execution/arena-dispatch.md`, `.project-ai/execution/verification.md`, `.project-ai/hosts/chatgpt-project.md`, `AGENTS.md`, `CONTEXT.md`, `docs/BUILD.md`, `docs/ARCHITECTURE.md`, `docs/architecture/*`, `README.md`, plus all build files, workflows, and tooling.

### Counts

| Metric | Count |
|---|---|
| Tracked files | **301** |
| Directories in the tracked tree (incl. repo root; incl. intermediate package path segments) | **149** (108 contain files directly) |

### Classification counts (files)

| Classification | Files |
|---|---|
| KEEP | 226 |
| KEEP — CLEANUP | 9 |
| CONSOLIDATE | 1 |
| RELOCATE | 0 |
| GENERATED / DERIVED — KEEP | 10 |
| TRANSITIONAL | 1 |
| HISTORICAL / DOCUMENTATION — KEEP | 49 |
| REDUNDANT | 1 |
| DEAD | 0 |
| OBSOLETE / IRRELEVANT | 4 (the whole content of `macrobenchmark/`; retired-dependency *entries* inside KEEP—CLEANUP files are itemized separately) |
| INVESTIGATE | 0 |
| **Total** | **301** |

Note on method: several retired items are *entries inside* files that are otherwise KEEP/GENERATED (e.g. benchmark library aliases in `gradle/libs.versions.toml`, stale `<component>` blocks in `gradle/verification-metadata.xml`, the `:macrobenchmark` include in `settings.gradle.kts`). Those files get one primary classification each (per-file taxonomy requirement); the retired entries inside them are itemized in §5, §6, §10 and §11. The `macrobenchmark/` module is the only retired mechanism with its own files still tracked, and all four of its files are OBSOLETE / IRRELEVANT.

### Major architectural findings

1. **The code/architecture module shape is mid-S05 and coherent.** Production shape is exactly two modules (`:app` → `:samsung`) with manual composition in `AppTApplication` (Hilt is specified in `docs/architecture/modules.md` but deliberately deferred; the deferral is documented in the composition root). S01–S04 surfaces (Welcome, LocalNetwork gate, Discovery, Pairing, Remote, saved pairing, Room/DataStore) are present; S05 surfaces (Settings, diagnostics recorder, physical verifier) are not on `main` yet — they belong to PR #118. No future-slice code was pulled forward.
2. **Architecture boundaries hold.** No `KEY_*` wire vocabulary in `:app` production source (only an unrelated `KEY_ACKNOWLEDGED` preference constant); no `samsung.internal` references outside `samsung`; no Firebase/Play/telemetry artifacts anywhere; `ModuleBoundaryTest`, `ForbiddenVocabularyTest`, the Gradle guard floor (`appTGuards`), merged-manifest guards, and the secret scanner all enforce these boundaries executably.
3. **The accepted S05 debug-only physical verifier and its release-exclusion boundary are not present on `main`** (they are PR #118 content). Nothing on `main` endangers that boundary; the preserved stock-debug instrumentation surface is the existing `androidTest` source sets, which remain valid.

### Major dead/obsolete findings (S05 Contract Revision 5 focus)

Contract Revision 5 (recorded in `PROJECT_STATE.md`) retired: Baseline Profile, `:macrobenchmark`, hosted Gradle Managed Devices, profile-handoff/regeneration, and `ci:device`. PR #135 (the audited head) removed the **CI/workflow plumbing** for these. The following **unjustified remnants of the retired infrastructure remain on `main`**, and PROJECT_STATE's "Next Authorized Action" explicitly assigns their removal to PR #118:

| # | Remnant | Evidence of retirement | Where it still lives |
|---|---|---|---|
| R1 | Entire `:macrobenchmark` module (4 files) | PROJECT_STATE Rev 5; `docs/architecture/modules.md` now says "Two production Gradle modules. No benchmark/test-only application module" | `macrobenchmark/**`, `settings.gradle.kts` (`include(":macrobenchmark")` + "Exactly three Gradle modules" comment), `build.gradle.kts` (`androidBuild` depends on `:macrobenchmark:assembleBenchmark`; detekt-scope comment) |
| R2 | `noProductionModuleDependsOnBenchmark` guard (exists only to police the retired module; hard-codes `project(":macrobenchmark")`) | Rev 5 | `gradle/guards.gradle.kts`, wired into `appTGuards` |
| R3 | Benchmark-only catalog entries: versions `androidx-benchmark`, `androidx-test-uiautomator`; libraries `androidx-benchmark-macro-junit4`, `androidx-test-uiautomator`; plugin alias `android-test` | Rev 5; only consumer is `macrobenchmark/build.gradle.kts` | `gradle/libs.versions.toml` |
| R4 | Retired dependency lock state | Rev 5; `diagnose.yml` already treats `macrobenchmark/gradle.lockfile` as delete-only (`retired` array) | `macrobenchmark/gradle.lockfile` |
| R5 | Retired verification-metadata entries (androidx.benchmark ×5, uiautomator ×3, wire-runtime ×2, profileinstaller 1.4.1) | Only resolved through `:macrobenchmark` graphs; `app`'s own `profileinstaller:1.4.0` entries are legitimate AndroidX transitives and must stay | `gradle/verification-metadata.xml` |
| R6 | GMD (managed-device) definitions `managedDevices { localDevices { pixel2api29 } }` in `:app` and `pixel2api29`/`pixel2api35` in `:samsung` | Rev 5 retires hosted GMD; PROJECT_STATE next action: remove "managed-device build definitions"; no CI consumer remains after PR #135 | `app/build.gradle.kts`, `samsung/build.gradle.kts` |
| R7 | Regeneration command still naming `:macrobenchmark:assembleBenchmark` + a stale bootstrap-era sentence | Rev 5 | `gradle/verification-metadata.template.xml` |
| R8 | Documentation still describing the retired `device` diagnose mode (twice) and omitting the `dependency-state` mode / `ci:dependency-state` label | PR #135 removed the mode from `diagnose.yml`, `diagnose-focus.mjs`, and `agent-control.yml` but left these passages | `docs/BUILD.md` (lines ~184, ~449, label list ~255), `docs/architecture/release.md` (lines ~153, ~390) |

No remnants were found of: Baseline Profile generation rules, `profileinstaller` *generation* tooling, benchmark-only synthetic app composition roots, `BenchmarkReadySamsungTvs` (absent from `main`), benchmark journeys, KVM provisioning, `ci:device` labels, managed-device report plumbing, or profile artifact handoff. PR #135 removed those cleanly. The `retired=("macrobenchmark/gradle.lockfile")` array in `diagnose.yml` is a **deliberate transitional bridge** for the pending deletion, not a remnant.

### Major duplication findings

| # | Duplication | Files | Verdict |
|---|---|---|---|
| D1 | Identical markdownlint rule set declared twice | `.markdownlint.jsonc` ≡ `.markdownlint-cli2.jsonc` (`config` block) | Canonical owner: `.markdownlint-cli2.jsonc` (CI passes it explicitly). `.markdownlint.jsonc` is REDUNDANT (see §6 — keep-or-remove has an editor-behavior nuance) |
| D2 | Markdown ignore state split across two files with *different* contents (`.project-ai/**` ignored only in the cli2 config) | `.markdownlintignore`, `.markdownlint-cli2.jsonc` | Consolidate into one owner |
| D3 | Identical `androidLintTool` Bouncy Castle/commons/httpclient constraint lists copied into `app`, `samsung`, `macrobenchmark` build files (plus root buildscript classpath constraints) | `app/build.gradle.kts`, `samsung/build.gradle.kts`, `macrobenchmark/build.gradle.kts`, `build.gradle.kts` | **Intentional** (each seam must constrain its own classpath; comments document this and `enforce-gradle-tooling-constraints.mjs` polices parity). Keep |
| D4 | Identical `kotlin { compilerOptions }` and detekt blocks across modules | `app`, `samsung`, `macrobenchmark` build files | Intentional boilerplate (`jvmTarget` must match each module's `compileOptions`; detekt blocks are "Identical to :app on purpose"). Keep |
| D5 | `backend/.gitignore` repeats root `.gitignore` backend entries | `backend/.gitignore`, `.gitignore` | Intentional package-local hygiene; harmless. Keep, note only |

No duplicated scripts, CI logic, domain models, validation logic, or secret/device-identity boundaries were found: each `tools/**` module has a distinct responsibility, its own test file, and a distinct CI wiring; guard deduplication already happened in Issue #88 (documented in `gradle/guards.gradle.kts`).

### Highest-risk cleanup opportunities (sequenced in §11)

1. **Batch 1 (accepted, assigned to PR #118):** retire `:macrobenchmark` + its lockfile + catalog/plugin entries + guard + `androidBuild` wiring + GMD blocks + stale metadata/template entries — one bounded, behavior-neutral-for-production cleanup whose only intended behavior change is that `androidBuild` no longer compiles the retired module.
2. **Batch 2:** documentation drift fixes in `docs/BUILD.md` and `docs/architecture/release.md` (retired `device` mode, missing `dependency-state`/backend modes, missing `ci:dependency-state` label).
3. **Batch 3:** markdownlint config consolidation (`.markdownlint.jsonc` vs `.markdownlint-cli2.jsonc`, split ignore lists).

Everything else in the repository is necessary, current, and correctly located. **No DEAD files were found.** The dead-code safety test (§5) was applied to every candidate before classification; no candidate survived as deletable beyond the explicitly retired set above.

---

## 2. Repository tree (complete tracked tree, `main` @ `754f6e2`)

```text
.
├── .coderabbit.yaml
├── .gitignore
├── .markdownlint-cli2.jsonc
├── .markdownlint.jsonc
├── .markdownlintignore
├── .yamllint.yml
├── AGENTS.md
├── CONTEXT.md
├── LICENSE
├── README.md
├── build.gradle.kts
├── gradle.lockfile
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings-gradle.lockfile
├── settings.gradle.kts
├── sonar-project.properties
├── .github/
│   ├── dependabot.yml
│   ├── actions/
│   │   ├── assert-dispatch-target/action.yml
│   │   ├── setup-jvm/action.yml
│   │   └── setup-node/action.yml
│   └── workflows/
│       ├── agent-control.yml
│       ├── codeql.yml
│       ├── diagnose.yml
│       ├── maintenance.yml
│       └── verify.yml
├── .project-ai/
│   ├── PROJECT_STATE.md
│   ├── bootstrap/project.md
│   ├── execution/arena-dispatch.md
│   ├── execution/verification.md
│   ├── hosts/chatgpt-project.md
│   ├── routing/capabilities.md
│   ├── routing/route.md
│   └── skills/
│       ├── SKILL_TEMPLATE.md
│       ├── SOURCES.md
│       ├── validate.py
│       ├── architecture-interface-design/SKILL.md
│       ├── ci-cd-automation/SKILL.md
│       ├── code-review/SKILL.md
│       ├── debugging-recovery/SKILL.md
│       ├── documentation-adrs/SKILL.md
│       ├── implementation-planning/SKILL.md
│       ├── incident-response/SKILL.md
│       ├── incremental-implementation/SKILL.md
│       ├── maintenance-migration-retirement/SKILL.md
│       ├── observability-operations-design/SKILL.md
│       ├── postmortem-learning/SKILL.md
│       ├── pr-integration-correction/SKILL.md
│       ├── project-bootstrap/SKILL.md
│       ├── project-discovery/SKILL.md
│       ├── release-deployment/SKILL.md
│       ├── requirements-specification/SKILL.md
│       ├── research-feasibility/SKILL.md
│       ├── security-engineering/SKILL.md
│       ├── test-driven-development/SKILL.md
│       └── verification-before-completion/SKILL.md
├── app/
│   ├── build.gradle.kts
│   ├── gradle.lockfile
│   ├── schemas/dev.anthracite.appt.data.AppTDatabase/1.json
│   └── src/
│       ├── androidTest/java/dev/anthracite/appt/
│       │   ├── DiscoveryWalkthroughTest.kt
│       │   ├── WelcomeLaunchTest.kt
│       │   └── samsung/SamsungKeystoreContractTest.kt
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/dev/anthracite/appt/
│       │   │   ├── AppSettingsLauncher.kt
│       │   │   ├── AppTApplication.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/{AppTDatabase.kt, TvProfile.kt, TvProfileDao.kt, TvProfiles.kt}
│       │   │   ├── discovery/{DiscoveryScreen.kt, DiscoveryTestTags.kt, DiscoveryUiState.kt,
│       │   │   │              DiscoveryViewModel.kt, DisplayLabel.kt}
│       │   │   ├── gate/{DiscoveryPermissions.kt, LocalNetworkPermissionGate.kt, PermissionGate.kt}
│       │   │   ├── localnetwork/{LocalNetworkScreen.kt, LocalNetworkTestTags.kt, LocalNetworkViewModel.kt}
│       │   │   ├── navigation/{AppTNavGraph.kt, Routes.kt}
│       │   │   ├── pairing/{PairingScreen.kt, PairingTestTags.kt, PairingUiState.kt, PairingViewModel.kt}
│       │   │   ├── preferences/PreferenceStore.kt
│       │   │   ├── remote/{ActiveRemoteHost.kt, RemoteScreen.kt, RemoteTestTags.kt,
│       │   │   │           RemoteUiState.kt, RemoteViewModel.kt}
│       │   │   ├── tokens/{Theme.kt, Tokens.kt}
│       │   │   └── welcome/{WelcomeScreen.kt, WelcomeTestTags.kt}
│       │   └── res/
│       │       ├── drawable/{ic_launcher_foreground.xml, ic_launcher_monochrome.xml}
│       │       ├── mipmap-anydpi/{ic_launcher.xml, ic_launcher_round.xml}
│       │       ├── values/{colors.xml, strings.xml, themes.xml}
│       │       └── xml/{backup_rules.xml, data_extraction_rules.xml}
│       └── test/
│           ├── java/dev/anthracite/appt/
│           │   ├── ModuleBoundaryTest.kt
│           │   ├── backup/BackupGuardTest.kt
│           │   ├── data/{FakeTvProfileDao.kt, TvProfileDaoTest.kt, TvProfileSchemaTest.kt, TvProfilesTest.kt}
│           │   ├── discovery/{DiscoveryScreenTest.kt, DiscoveryViewModelTest.kt, DisplayLabelTest.kt}
│           │   ├── flow/PairingToFirstControlFlowTest.kt
│           │   ├── gate/LocalNetworkPermissionGateTest.kt
│           │   ├── localnetwork/{ForbiddenVocabularyTest.kt, LocalNetworkScreenTest.kt, LocalNetworkViewModelTest.kt}
│           │   ├── navigation/AppTNavGraphTest.kt
│           │   ├── pairing/{PairingScreenTest.kt, PairingViewModelTest.kt}
│           │   ├── preferences/PreferenceStoreTest.kt
│           │   ├── remote/{ActiveRemoteHostTest.kt, RemoteScreenTest.kt, RemoteViewModelTest.kt}
│           │   ├── savedpairing/SavedPairingUiTest.kt
│           │   ├── testing/{FakePermissionGate.kt, FakeSamsungTvs.kt, MainDispatcherRule.kt,
│           │   │            SemanticsAssertions.kt, TestFlows.kt, TestPreferences.kt, TestScopeSettleTest.kt}
│           │   ├── tokens/{Contrast.kt, TokensTest.kt}
│           │   └── welcome/WelcomeScreenTest.kt
│           └── resources/robolectric.properties
├── backend/
│   ├── .gitignore
│   ├── .prettierignore
│   ├── .prettierrc.json
│   ├── eslint.config.mjs
│   ├── jest.config.js
│   ├── package-lock.json
│   ├── package.json
│   ├── src/{index.ts, package-identity.ts}
│   ├── test/package-identity.test.ts
│   ├── ts-jest-transformer.js
│   ├── tsconfig.json
│   └── tsconfig.test.json
├── config/detekt/detekt.yml
├── docs/
│   ├── ARCHITECTURE.md
│   ├── BUILD.md
│   ├── PRODUCT.md
│   └── architecture/
│       ├── README.md
│       ├── account-entitlement.md
│       ├── commands.md
│       ├── connection.md
│       ├── data.md
│       ├── diagnostics.md
│       ├── discovery.md
│       ├── flows.md
│       ├── lifecycle.md
│       ├── modules.md
│       ├── presentation.md
│       ├── protocol.md
│       ├── release.md
│       ├── reliability.md
│       ├── samsung-interface.md
│       ├── security.md
│       ├── slices.md
│       ├── testing.md
│       └── ui-ux.md
├── gradle/
│   ├── guards.gradle.kts
│   ├── libs.versions.toml
│   ├── verification-metadata.template.xml
│   ├── verification-metadata.xml
│   └── wrapper/{gradle-wrapper.jar, gradle-wrapper.properties}
├── macrobenchmark/                      # RETIRED (S05 Contract Revision 5) — see §5
│   ├── build.gradle.kts
│   ├── gradle.lockfile
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/dev/anthracite/appt/macrobenchmark/StartupBenchmark.kt
├── samsung/
│   ├── README.md
│   ├── build.gradle.kts
│   ├── gradle.lockfile
│   └── src/
│       ├── androidTest/java/dev/anthracite/appt/samsung/internal/NameScrubberAndroidTest.kt
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── java/dev/anthracite/appt/samsung/
│       │       ├── {DiscoveryTypes.kt, RemoteSession.kt, SamsungModule.kt, SamsungTvs.kt, SessionTypes.kt}
│       │       └── internal/{AirPlayTxt.kt, AndroidDiscoveryTransport.kt, AndroidKeystoreCipher.kt,
│       │           BlockingIo.kt, BoundedCallback.kt, BoundedHttp.kt, ConfirmedTelevision.kt,
│       │           DeviceInfo.kt, DiscoveryScan.kt, DiscoveryTransport.kt, KeystoreSamsungStore.kt,
│       │           LanPolicy.kt, LiveSession.kt, NameScrubber.kt, NsdAirPlayBrowser.kt,
│       │           OkHttpSessionTransport.kt, PairingSecret.kt, PlaintextWebSocketTransport.kt,
│       │           ProductionSessionTransport.kt, RemoteChannel.kt, SamsungDeviceRecord.kt,
│       │           SamsungTvsImpl.kt, SessionGeneration.kt, SessionTransport.kt, Ssdp.kt,
│       │           SsdpClient.kt, TvIdentity.kt, WebSocketFrames.kt}
│       └── test/
│           ├── java/dev/anthracite/appt/samsung/internal/
│           │   ├── {AirPlayTxtTest.kt, BoundedCallbackTest.kt, BoundedHttpResponseTest.kt,
│           │   │    DeviceInfoParserTest.kt, FixtureProvenanceTest.kt, FixtureTransport.kt, Fixtures.kt,
│           │   │    InMemorySamsungStore.kt, KeystoreSamsungStoreTest.kt, LanPolicyTest.kt,
│           │   │    LoopbackSocketsTest.kt, OkHttpSessionTransportTest.kt, PlaintextWebSocketTransportTest.kt,
│           │   │    ProductionSessionTransportTest.kt, RemoteChannelTest.kt, SamsungModuleGuardTest.kt,
│           │   │    SamsungTvsDiscoveryTest.kt, SamsungTvsSavedPairingTest.kt, SamsungTvsSessionTest.kt,
│           │   │    ScriptedSessionTransport.kt, SessionFixture.kt, SsdpTest.kt, TvIdentityTest.kt,
│           │   │    WebSocketFramesTest.kt}
│           └── resources/samsung/fixtures/   # 17 fixture cases × {provenance.json, trace.jsonl}
│               ├── airplay-samsung-tv/   ├── bluray-rcr/            ├── device-info-unreadable/
│               ├── dial-filter/          ├── duplicate-uuid/        ├── identity-mismatch/
│               ├── late-tv/              ├── malformed-frame/       ├── no-stable-uuid/
│               ├── rediscovery-address-change/  ├── same-name-two-tvs/  ├── soundbar-airplay/
│               ├── ssdp-tizen-tv/        ├── tls-approval-then-volume/  ├── token-resume/
│               ├── unauthorized-with-token/     └── unsupported-no-keys/
└── tools/
    ├── ci/
    │   ├── diagnose-focus.mjs
    │   ├── dispatch-workflow.mjs
    │   ├── project_state_contract.py
    │   ├── purge-actions.mjs
    │   ├── validate-sonar-inputs.py
    │   └── test/{assert-dispatch-target.test.mjs, diagnose-focus.test.mjs, dispatch-workflow.test.mjs,
    │             project-state-contract.test.py, purge-actions.test.mjs, sonar-boundary.test.py,
    │             workflow-cache-contract.test.py}
    ├── secret-scan/
    │   ├── secret-scan.mjs
    │   └── test/secret-scan.test.mjs
    └── security/
        ├── enforce-dependency-policy.mjs
        ├── enforce-gradle-tooling-constraints.mjs
        ├── run.sh
        └── test/{enforce-dependency-policy.test.mjs, enforce-gradle-tooling-constraints.test.mjs}
```

(File counts: root 18, `.github` 9, `.project-ai` 30, `app` 83, `backend` 13, `config` 1, `docs` 22, `gradle` 6, `macrobenchmark` 4, `samsung` 96, `tools` 19 — total 301; verified against `git ls-files | wc -l`.)
---

## 3. Directory inventory

Every directory in the tracked tree is listed (149 total, including intermediate Java-package path segments and the repo root). Classification abbreviations: **K** = KEEP, **K-C** = KEEP — CLEANUP, **G** = GENERATED/DERIVED — KEEP, **T** = TRANSITIONAL, **H** = HISTORICAL/DOCUMENTATION — KEEP, **O** = OBSOLETE/IRRELEVANT.

### 3.1 Root

| Path | Role / why it exists | Important contents | Boundary still sensible? | Obsolete material? | Disposition |
|---|---|---|---|---|---|
| `.` | Repository root; owns root build, module graph, root docs, license, control-plane entry | `build.gradle.kts`, `settings.gradle.kts`, `gradlew*`, lockfiles, `README.md`, `CONTEXT.md`, `LICENSE`, `AGENTS.md`, lint configs | Yes — project root belongs to the actual project (bootstrap policy) | None at directory level (retired entries live inside specific files, §5) | K |

### 3.2 `.github/**` — CI/CD and provider integration

| Path | Role / why it exists | Owns | Boundary sensible? | Obsolete? | Disposition |
|---|---|---|---|---|---|
| `.github` | GitHub-specific provider configuration root | workflows, composite actions, dependabot | Yes | No | K |
| `.github/workflows` | The five accepted workflows: `verify.yml` (authoritative gate), `diagnose.yml` (focused non-terminal diagnostics), `codeql.yml` (SAST), `maintenance.yml` (destructive purge), `agent-control.yml` (trusted dispatch bridge) | 5 workflow files | Yes — matches `release.md` workflow topology exactly; no retired/per-slice workflows present | No (retired device/GMD plumbing already removed by PR #135) | K |
| `.github/actions` | Container for repository-local composite actions | 3 action dirs | Yes | No | K |
| `.github/actions/assert-dispatch-target` | Proves a dispatched run targets the exact expected SHA/open PR before any target code is checked out (trust model of the bridge) | `action.yml` | Yes | No | K |
| `.github/actions/setup-jvm` | Pinned JDK + wrapper-based Gradle setup for Android jobs | `action.yml` | Yes | No | K |
| `.github/actions/setup-node` | Pinned Node setup for backend/repo-tooling jobs | `action.yml` | Yes | No | K |

### 3.3 `.project-ai/**` — committed internal AI control plane

| Path | Role / why it exists | Owns | Boundary sensible? | Obsolete? | Disposition |
|---|---|---|---|---|---|
| `.project-ai` | The committed internal AI control plane (bootstrap policy: intentionally version-controlled) | PROJECT_STATE, routing, execution, hosts, bootstrap, skills | Yes | No | K |
| `.project-ai/bootstrap` | Bootstrap/reconciliation policy | `project.md` | Yes | No | H |
| `.project-ai/execution` | Arena dispatch lifecycle + verification strategy | `arena-dispatch.md`, `verification.md` | Yes | No | H |
| `.project-ai/hosts` | Host boot sequence (ChatGPT Project) | `chatgpt-project.md` | Yes | No | H |
| `.project-ai/routing` | Capability classification + execution routing | `capabilities.md`, `route.md` | Yes | No | H |
| `.project-ai/skills` | Lifecycle skill library + structural validator | 20 `SKILL.md` dirs, `SKILL_TEMPLATE.md`, `SOURCES.md`, `validate.py` | Yes; `validate.py` is executed by `verify.yml` repo-policy | No | K (dir; per-file §4) |
| `.project-ai/skills/<each of the 20 skill dirs>` | One lifecycle method each (architecture-interface-design, ci-cd-automation, code-review, debugging-recovery, documentation-adrs, implementation-planning, incident-response, incremental-implementation, maintenance-migration-retirement, observability-operations-design, postmortem-learning, pr-integration-correction, project-bootstrap, project-discovery, release-deployment, requirements-specification, research-feasibility, security-engineering, test-driven-development, verification-before-completion) | exactly one `SKILL.md` each | Yes — all 20 are routed from `capabilities.md` mandatory-routing table and pass `validate.py` | No | H |

### 3.4 Android modules

| Path | Role / why it exists | Owns | Boundary sensible? | Obsolete? | Disposition |
|---|---|---|---|---|---|
| `app` | Production Android application module | build file, lockfile, Room schema, 3 source sets | Yes (modules.md production module) | Retired `managedDevices` block inside build file only (R6) | K |
| `app/schemas` | Room exported-schema root (KSP `room.schemaLocation`) | DB schema dir | Yes — generated-but-committed contract consumed by `TvProfileSchemaTest` and future migration tests | No | K |
| `app/schemas/dev.anthracite.appt.data.AppTDatabase` | Versioned schemas for `appt.db` | `1.json` | Yes | No | G |
| `app/src` | Source-set root | main/test/androidTest | Yes | No | K |
| `app/src/main` | Production sources + manifest + resources | 40 Kotlin files, manifest, res | Yes | No | K |
| `app/src/main/java/dev/anthracite/appt` (+ 10 feature packages: `data`, `discovery`, `gate`, `localnetwork`, `navigation`, `pairing`, `preferences`, `remote`, `tokens`, `welcome`; see §3.7 for the path segments) | Application composition root + S01–S04 surfaces | AppTApplication, MainActivity, screens/VMs/state, Room/DataStore | Yes — packages match modules.md package layout where implemented; no Clean-Architecture layer stack | No | K |
| `app/src/main/res/drawable` | Launcher foreground/monochrome vectors | 2 XML | Yes | No | K |
| `app/src/main/res/mipmap-anydpi` | Adaptive icon descriptors | 2 XML | Yes | No | K |
| `app/src/main/res/values` | colors/strings/themes (strings are vocab-tested by `ForbiddenVocabularyTest`) | 3 XML | Yes | No | K |
| `app/src/main/res/xml` | Backup & data-extraction rules (secret-exclusion boundary, tested by `BackupGuardTest`) | 2 XML | Yes | No | K |
| `app/src/test` | JVM/Robolectric unit suite | 28 test/helper files, `resources/` | Yes | No | K |
| `app/src/test/java/dev/anthracite/appt` (+ 14 packages: root, `backup`, `data`, `discovery`, `flow`, `gate`, `localnetwork`, `navigation`, `pairing`, `preferences`, `remote`, `savedpairing`, `testing`, `tokens`, `welcome`) | Unit tests + shared test doubles (`testing/`) | see §4 | Yes — test code stays with the module whose behavior it verifies (modules.md) | No | K |
| `app/src/test/resources` | Robolectric config | `robolectric.properties` | Yes | No | K |
| `app/src/androidTest` | Instrumented source set | 2 app-level tests + samsung dir | Yes — instrumented row of testing.md (Keystore round trip, launch walkthrough); no CI device runner currently runs them; accepted future responsibility | No | K |
| `app/src/androidTest/java/dev/anthracite/appt` | Instrumented launch/discovery walkthroughs | 2 tests | Yes | No | K |
| `app/src/androidTest/java/dev/anthracite/appt/samsung` | Keystore contract test colocated with app because it needs the app package + instrumentation runner | `SamsungKeystoreContractTest.kt` | Acceptable exception: modules.md requires the Keystore round-trip as instrumented evidence; it lives in app's androidTest rather than samsung's to reuse the app APK context. Documented in testing.md | No | K |
| `samsung` | Production Samsung control module | README, build file, lockfile, 3 source sets | Yes (modules.md deep module) | Retired `managedDevices` block inside build file only (R6) | K |
| `samsung/src/main` | Production Samsung sources + manifest | 5 public + 28 internal Kotlin files | Yes — public/internal split matches samsung-interface.md | No | K |
| `samsung/src/main/java/dev/anthracite/appt/samsung` | Caller-facing API package | SamsungTvs, RemoteSession, types, SamsungModule | Yes | No | K |
| `samsung/src/main/java/dev/anthracite/appt/samsung/internal` | Implementation detail package (never imported by app; enforced by `ModuleBoundaryTest`) | discovery/session/secrets/transport internals | Yes | No | K |
| `samsung/src/test` | Samsung JVM suite + fixtures | 24 files + resources | Yes | No | K |
| `samsung/src/test/java/dev/anthracite/appt/samsung/internal` | Contract tests + fake transports (testing.md seam testing) | 24 files | Yes | No | K |
| `samsung/src/test/resources/samsung/fixtures` + 17 case dirs (`airplay-samsung-tv`, `bluray-rcr`, `device-info-unreadable`, `dial-filter`, `duplicate-uuid`, `identity-mismatch`, `late-tv`, `malformed-frame`, `no-stable-uuid`, `rediscovery-address-change`, `same-name-two-tvs`, `soundbar-airplay`, `ssdp-tizen-tv`, `tls-approval-then-volume`, `token-resume`, `unauthorized-with-token`, `unsupported-no-keys`) | Committed redacted protocol fixtures; location and provenance schema owned by testing.md#fixtures | `provenance.json` + `trace.jsonl` per case; consumed by `Fixtures.kt`/`FixtureTransport.kt`; provenance policed by `FixtureProvenanceTest` and secret-scan | Yes | No — missing cases from testing.md's minimum set belong to later slices (S06+), not drift | K |
| `samsung/src/androidTest` + `.../samsung/internal` | Instrumented scrubber check | `NameScrubberAndroidTest.kt` | Yes (instrumented row) | No | K |
| `macrobenchmark` | **Retired** test-only `com.android.test` module | 4 files | **No longer** — modules.md now: "Two production Gradle modules. No benchmark/test-only application module" | Entirely (R1) | O |
| `macrobenchmark/src/main` (+ `java/dev/anthracite/appt/macrobenchmark`; see §3.7) | Retired benchmark sources/manifest | `StartupBenchmark.kt`, manifest | No | Entirely | O |

### 3.5 Backend

| Path | Role / why it exists | Owns | Boundary sensible? | Obsolete? | Disposition |
|---|---|---|---|---|---|
| `backend` | Standalone TypeScript package: the future Entitlement Backend; S01 non-deploying skeleton with real toolchain wired into CI | package.json + lockfile, TS config, jest/eslint/prettier config, src, test | Yes — deliberately not a Gradle module; never enters Android graph | No | K |
| `backend/src` | Package sources | `index.ts`, `package-identity.ts` | Yes | No | K |
| `backend/test` | Jest suite | `package-identity.test.ts` | Yes | No | K |

### 3.6 Tooling, config, docs, gradle state

| Path | Role / why it exists | Owns | Boundary sensible? | Obsolete? | Disposition |
|---|---|---|---|---|---|
| `config` | Repository-level reviewed tool configuration root | detekt dir | Yes | No | K |
| `config/detekt` | Single reviewed detekt config for production Kotlin (Issue #36; no baseline by policy) | `detekt.yml` | Yes | No | K |
| `docs` | Product & architecture & build documentation root | PRODUCT, ARCHITECTURE, BUILD + `architecture/` | Yes — ownership map in architecture/README.md | Drift in BUILD.md (R8) | K |
| `docs/architecture` | Accepted detailed architecture map (19 documents) | see §4 | Yes — one owner per concern, read-by-branch index | Two stale `device`-mode passages in release.md (R8) | K |
| `gradle` | Gradle build state: catalog, guards, verification metadata, wrapper | libs.versions.toml, guards.gradle.kts, verification-metadata{,.template}.xml, wrapper/, | Yes | Retired catalog aliases + stale metadata entries inside files (R3/R5/R7) | K (dir; file-level §4) |
| `gradle/wrapper` | Committed checksum-pinned wrapper (release.md pins the toolchain) | jar + properties | Yes | No | G |
| `tools` | Repository-owned deterministic tooling root | ci/, secret-scan/, security/ | Yes — one concern per subdir, each with tests | No | K |
| `tools/ci` | CI control-plane modules: focus resolver, dispatch bridge logic, purge logic, project-state contract, sonar input validation | 5 modules | Yes | No | K |
| `tools/ci/test` | Tests for every `tools/ci` module; run by `verify.yml` repo-policy/repo-quality | 7 test files | Yes | No | K |
| `tools/secret-scan` | Repository secret scanner (`noCredentialFilesInRepo` invariant) | `secret-scan.mjs` | Yes | No | K |
| `tools/secret-scan/test` | Scanner self-tests (run by verify.yml) | 1 test file | Yes | No | K |
| `tools/security` | Dependency-policy enforcement + Gradle tooling-constraint enforcement + local security entrypoint | 2 mjs + `run.sh` | Yes | No | K |
| `tools/security/test` | Tests for both enforcers (run by verify.yml) | 2 test files | Yes | No | K |

### 3.7 Intermediate path-segment directories (Java package / source-set plumbing)

These exist solely as language/source-set path segments; each inherits the classification of the tree it belongs to. Listed exhaustively for mechanical completeness (all K unless noted):

- `app/src/main/java`, `app/src/main/java/dev`, `app/src/main/java/dev/anthracite` — package path to app root (K)
- `app/src/androidTest/java`, `app/src/androidTest/java/dev`, `app/src/androidTest/java/dev/anthracite` — package path (K)
- `app/src/test/java`, `app/src/test/java/dev`, `app/src/test/java/dev/anthracite` — package path (K)
- `samsung/src/main/java`, `samsung/src/main/java/dev`, `samsung/src/main/java/dev/anthracite` — package path (K)
- `samsung/src/test/java`, `samsung/src/test/java/dev`, `samsung/src/test/java/dev/anthracite` — package path (K)
- `samsung/src/test/resources`, `samsung/src/test/resources/samsung` — fixture root path (K)
- `samsung/src/androidTest/java`, `samsung/src/androidTest/java/dev`, `samsung/src/androidTest/java/dev/anthracite`, `samsung/src/androidTest/java/dev/anthracite/appt`, `samsung/src/androidTest/java/dev/anthracite/appt/samsung` — package path (K)
- `app/src/androidTest/java/dev/anthracite` (segment, K)
- `macrobenchmark/src`, `macrobenchmark/src/main/java`, `macrobenchmark/src/main/java/dev`, `macrobenchmark/src/main/java/dev/anthracite`, `macrobenchmark/src/main/java/dev/anthracite/appt` — package path of the retired module (**O**)

(149 directories total = 1 root + 148 nested; every entry above or in §3.1–§3.6 covers them.)
---

## 4. File inventory (all 301 tracked files)

Legend — classification abbreviations as in §3. Confidence: **H** high, **M** medium. "Used by / evidence" names the concrete consumers found by `git grep`, build inspection, workflow inspection, and convention (AGP/Gradle/npm/GitHub discovery).

### 4.1 Root files (18)

| Path | Domain | Purpose | Used by / evidence | Classification | Recommended action | Confidence |
|---|---|---|---|---|---|---|
| `.coderabbit.yaml` | CI/review config | CodeRabbit independent PR-review layer config (assertive profile, slop detection, 3 warning-mode pre-merge checks) | Consumed by CodeRabbit service; accepted by `release.md#agent-authored-change-assurance` | KEEP | Keep | H |
| `.gitignore` | Repo hygiene | Ignores build output, node_modules, IDE files, and all key/credential file shapes (secret-scanning invariant) | Git; mirrored by `tools/secret-scan/secret-scan.mjs` | KEEP | Keep | H |
| `.markdownlint-cli2.jsonc` | Lint config | markdownlint-cli2 config + repo ignores (`.project-ai/**`) | `verify.yml` repo-quality passes it as `config:` | KEEP | Keep; single owner for markdown ignore state (D2) | H |
| `.markdownlint.jsonc` | Lint config | Identical rule set to the `config` block of `.markdownlint-cli2.jsonc` | Auto-discovered by markdownlint-cli2/editors; CI already supplies the rules via the cli2 file | REDUNDANT | Consolidate onto `.markdownlint-cli2.jsonc` (remove only after confirming no editor workflow depends on the auto-discovered file) | M |
| `.markdownlintignore` | Lint config | Ignore list (`node_modules`, `backend/node_modules`, `.git`, `.agents`); diverges from cli2 ignores (no `.project-ai`) | markdownlint-cli2 auto-discovery | CONSOLIDATE | Merge ignore state into `.markdownlint-cli2.jsonc` (D2) | H |
| `.yamllint.yml` | Lint config | yamllint rules for repo YAML | `verify.yml` repo-quality `yamllint -c .yamllint.yml .` | KEEP | Keep | H |
| `AGENTS.md` | Control-plane entry | Compatibility entry point redirecting to `.project-ai/hosts/chatgpt-project.md` | Agents that look for root `AGENTS.md` | KEEP | Keep | H |
| `CONTEXT.md` | Domain language | Canonical AppT ubiquitous language (Television, Local Pairing, Customer Account, …) | Referenced by PROJECT_STATE authoritative refs; guides all docs/code naming | KEEP | Keep | H |
| `LICENSE` | Legal | MIT license text | README badge; release gate context | KEEP | Keep (final source-license decision remains a pre-public-release gate per PROJECT_STATE) | H |
| `README.md` | Project docs | Public project overview, build entry commands, repo map | GitHub; links docs/ | KEEP | Keep | H |
| `build.gradle.kts` | Build | Root build: buildscript security constraints (Issues #54/#68), Spotless, Kover, repo-wide locking, `resolveAndLockAll`, CI failure-domain tasks (`androidFormat/Static/Build/Unit`), `ciCheck` | Gradle root project; `verify.yml` android-* jobs invoke the lifecycle tasks | KEEP — CLEANUP | Remove `:macrobenchmark:assembleBenchmark` from `androidBuild` + the macrobenchmark detekt-scope comment when the module is retired (R1); keep everything else | H |
| `gradle.lockfile` | Dependency state | Generated lock of root configurations (spotless/ktfmt + kover agent classpaths) | Produced by `resolveAndLockAll --write-locks`; checked by `dependencyLockCheck` | GENERATED / DERIVED — KEEP | Keep; regenerate only via trusted route | H |
| `gradle.properties` | Build config | JVM args, parallel, build cache, config-cache-off rationale, AndroidX flags | Gradle | KEEP | Keep | H |
| `gradlew` | Toolchain | Unix wrapper script | Every Gradle invocation (CI + local) | GENERATED / DERIVED — KEEP | Keep (committed by design, checksum-pinned distribution) | H |
| `gradlew.bat` | Toolchain | Windows wrapper script | Windows Gradle invocations | GENERATED / DERIVED — KEEP | Keep | H |
| `settings-gradle.lockfile` | Dependency state | Generated lock for settings evaluation (version-catalog ingestion) | `dependencyLockCheck`; `verify.yml` change-detection treats it as dependency input | GENERATED / DERIVED — KEEP | Keep | H |
| `settings.gradle.kts` | Build | Module graph + repository policy (`FAIL_ON_PROJECT_REPOS`, google/mavenCentral only) | Gradle | KEEP — CLEANUP | Drop `include(":macrobenchmark")` and fix the "Exactly three Gradle modules" comment to match the accepted two-module shape (R1) | H |
| `sonar-project.properties` | Quality platform | SonarQube Cloud project key/org, source/test roots, coverage import paths, quality-gate wait | `verify.yml` quality-platform `-Dproject.settings`; `tools/ci/test/sonar-boundary.test.py`; `tools/ci/validate-sonar-inputs.py` | KEEP | Keep | H |

### 4.2 `.github/**` (9)

| Path | Domain | Purpose | Used by / evidence | Classification | Recommended action | Confidence |
|---|---|---|---|---|---|---|
| `.github/dependabot.yml` | Supply chain | Weekly grouped version updates for Gradle catalog, backend npm, GitHub Actions pins; no auto-merge; reviewer regeneration notes | Dependabot service | KEEP | Keep | H |
| `.github/actions/assert-dispatch-target/action.yml` | CI trust model | Proves dispatch target = expected SHA = open-PR head before target checkout | All verify/diagnose jobs | KEEP | Keep | H |
| `.github/actions/setup-jvm/action.yml` | CI toolchain | Pinned JDK + wrapper-authoritative Gradle setup | Android jobs in verify/diagnose/codeql | KEEP | Keep | H |
| `.github/actions/setup-node/action.yml` | CI toolchain | Pinned Node 24 setup | Node jobs in verify/diagnose | KEEP | Keep | H |
| `.github/workflows/agent-control.yml` | CI control plane | Trusted label→dispatch bridge (`ci:full`, `ci:app-unit`, …, `ci:dependency-state`); never executes PR code | `pull_request_target` labeled; dispatches verify/diagnose | KEEP | Keep | H |
| `.github/workflows/codeql.yml` | Security SAST | CodeQL: JS/TS+Actions on PRs, java-kotlin on main/dispatch/schedule; pinned bundle 2.27.1 | GitHub code scanning | TRANSITIONAL | Keep — exists under a documented sunset condition: delete once GitHub-managed Default Setup supports Kotlin 2.4.20 and passes; until then it is the mandatory SAST owner | H |
| `.github/workflows/diagnose.yml` | CI diagnostics | 8 focused non-terminal modes (app-unit, samsung-unit, android-static, android-build, backend-static, backend-test, backend, dependency-state) + PR-local Gradle warm-state publisher + feedback report | Manual dispatch + agent-control bridge | KEEP | Keep; the `retired=("macrobenchmark/gradle.lockfile")` array is a deliberate TRANSITIONAL bridge — remove it with the lockfile deletion (Batch 1) | H |
| `.github/workflows/maintenance.yml` | CI housekeeping | Manual confirmed (`PURGE`) destructive Actions cache/run purge | Manual dispatch; `tools/ci/purge-actions.mjs` | KEEP | Keep | H |
| `.github/workflows/verify.yml` | CI verification | Authoritative verification: repo-policy, repo-quality, 4 Android domains, 2 backend domains, change detection, dependency review, Sonar, single `gate` | Branch protection `verify / gate` | KEEP | Keep | H |

### 4.3 `.project-ai/**` (30)

| Path | Domain | Purpose | Used by / evidence | Classification | Recommended action | Confidence |
|---|---|---|---|---|---|---|
| `.project-ai/PROJECT_STATE.md` | Control plane / state | Bounded snapshot of accepted durable reality (phase, objective, accepted decisions incl. Rev 5, blockers, next action) | Read by hosts boot sequence; contract-checked by `tools/ci/project_state_contract.py` in `verify.yml` | HISTORICAL / DOCUMENTATION — KEEP | Keep (audit does not update it) | H |
| `.project-ai/bootstrap/project.md` | Control plane | Bootstrap/reconciliation policy | `project-bootstrap` skill; hosts boot | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/execution/arena-dispatch.md` | Control plane | Arena Issue contract, lifecycle, review outcomes | Arena dispatch/review flows | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/execution/verification.md` | Control plane | Verification strategy (feedback loop vs terminal) | Verification decisions | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/hosts/chatgpt-project.md` | Control plane | ChatGPT Project boot sequence + operating boundary | `AGENTS.md`; hosts | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/routing/capabilities.md` | Control plane | Capability taxonomy + mandatory lifecycle-skill routing table (20 triggers) | All control-plane routing; `validate.py` checks coverage | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/routing/route.md` | Control plane | Execution routing rules incl. hosted-fallback + dependency-regeneration routes | Routing decisions; updated by PR #135 | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/SKILL_TEMPLATE.md` | Control plane | Skill authoring template | Skill maintenance | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/SOURCES.md` | Control plane / provenance | Upstream skill-design sources + adaptation policy | Maintenance/provenance; explicitly outside runtime skills | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/validate.py` | Control plane tooling | Validates skill structure + routing coverage | `verify.yml` repo-policy executes it | KEEP | Keep | H |
| `.project-ai/skills/architecture-interface-design/SKILL.md` | Control plane | Architecture-design method | Routed from capabilities.md | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/ci-cd-automation/SKILL.md` | Control plane | CI/CD method (contains intentional history note about retired `ci.yml`) | Routed | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/code-review/SKILL.md` | Control plane | Contract-review method | Routed | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/debugging-recovery/SKILL.md` | Control plane | Diagnose-before-fix method | Routed (mandatory on failures) | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/documentation-adrs/SKILL.md` | Control plane | Documentation/ADR method | Routed | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/implementation-planning/SKILL.md` | Control plane | Issue/contract planning method | Routed | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/incident-response/SKILL.md` | Control plane | Incident stabilization method | Routed | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/incremental-implementation/SKILL.md` | Control plane | Evidence-first incremental implementation method | Routed; mandatory for Arena multi-step work | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/maintenance-migration-retirement/SKILL.md` | Control plane | Migration/retirement method | Routed (this audit's capability) | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/observability-operations-design/SKILL.md` | Control plane | Observability design method | Routed | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/postmortem-learning/SKILL.md` | Control plane | Postmortem learning method | Routed | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/pr-integration-correction/SKILL.md` | Control plane | Same-PR correction method | Routed | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/project-bootstrap/SKILL.md` | Control plane | Bootstrap execution method | Routed | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/project-discovery/SKILL.md` | Control plane | Discovery method | Routed | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/release-deployment/SKILL.md` | Control plane | Release method | Routed | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/requirements-specification/SKILL.md` | Control plane | Requirements method | Routed | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/research-feasibility/SKILL.md` | Control plane | Research method | Routed | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/security-engineering/SKILL.md` | Control plane | Security method | Routed | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/test-driven-development/SKILL.md` | Control plane | TDD method | Routed | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `.project-ai/skills/verification-before-completion/SKILL.md` | Control plane | Fresh-evidence completion gate | Routed (mandatory before "complete") | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |

### 4.4 `gradle/**`, `config/**` (7)

| Path | Domain | Purpose | Used by / evidence | Classification | Recommended action | Confidence |
|---|---|---|---|---|---|---|
| `gradle/guards.gradle.kts` | Build / security floor | Executable guard floor: telemetry absence, Firestore absence, Samsung boundary, Log ban, sync-record ban, catalog pinning, lock check, benchmark isolation; aggregates `appTGuards` | `androidStatic` → `appTGuards` (verify/diagnose) | KEEP — CLEANUP | Remove `noProductionModuleDependsOnBenchmark` (dead once module is gone; it hard-references `project(":macrobenchmark")`) with Batch 1 (R2) | H |
| `gradle/libs.versions.toml` | Dependency declarations | Exact version catalog (no ranges/snapshots); license/provenance notes | All module builds; Dependabot writes bumps here; `versionCatalogPinned` guard | KEEP — CLEANUP | Remove retired aliases with Batch 1: versions `androidx-benchmark`, `androidx-test-uiautomator`; libraries `androidx-benchmark-macro-junit4`, `androidx-test-uiautomator`; plugin `android-test` (R3). Also consider retiring the stale "S01 walking skeleton" header framing | H |
| `gradle/verification-metadata.template.xml` | Supply-chain policy | Reviewed policy header for dependency verification (verify-metadata on, no signature verification yet, no wildcards) + regeneration command | Referenced by docs/BUILD.md regeneration instructions | KEEP — CLEANUP | Update regeneration command to drop `:macrobenchmark:assembleBenchmark`; drop stale "CI does this automatically while the repository is not yet bootstrapped" sentence (R7) | H |
| `gradle/verification-metadata.xml` | Supply-chain state (generated) | SHA-256 checksums for every resolved component; strict verification in CI | Every `--dependency-verification=strict` Gradle run | GENERATED / DERIVED — KEEP | Keep; after Batch 1, regenerate and remove the stale blocks only reachable through `:macrobenchmark` graphs: `androidx.benchmark:*` (5), `androidx.test.uiautomator:*` (3), `com.squareup.wire:wire-runtime*` (2), `profileinstaller:1.4.1` (1). **Keep `profileinstaller:1.4.0`** — legitimate app-graph transitive (R5) | H |
| `gradle/wrapper/gradle-wrapper.jar` | Toolchain (generated) | Wrapper bootstrap jar | `gradlew`; produced by retired one-shot bootstrap workflow; checksum-recorded | GENERATED / DERIVED — KEEP | Keep | H |
| `gradle/wrapper/gradle-wrapper.properties` | Toolchain config | Pins Gradle 9.7.1 + `distributionSha256Sum` | `gradlew`; sole Gradle version authority | KEEP | Keep | H |
| `config/detekt/detekt.yml` | Static analysis config | Single reviewed detekt config, production-only scope, no baseline (Issue #36 policy) | `:app:detekt`, `:samsung:detekt` | KEEP | Keep | H |

### 4.5 `docs/**` (22)

| Path | Domain | Purpose | Used by / evidence | Classification | Recommended action | Confidence |
|---|---|---|---|---|---|---|
| `docs/PRODUCT.md` | Product | Approved product intent | PROJECT_STATE authoritative ref; all slice contracts | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `docs/ARCHITECTURE.md` | Architecture baseline | Accepted V1 decisions + invariants + evidence classes | Baseline owner; architecture map defers to it | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `docs/BUILD.md` | Build/ops docs | Requirements, commands, dependency verification, CI topology, agent invocation, CodeQL notes | Humans + agents; describes verify/diagnose routes | KEEP — CLEANUP | Fix drift (R8): drop the retired `device` mode (×2: hosted-fallback paragraph ~L184, focus example ~L449); list all 8 diagnose modes incl. `dependency-state` and the backend modes; add `ci:dependency-state` to the command-label list; clarify the "Automatic Dependency Submission (Gradle) workflow" wording refers to a repo-setting feature | H |
| `docs/architecture/README.md` | Architecture index | Read-by-branch map + ownership rules | Entry point to architecture map | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `docs/architecture/account-entitlement.md` | Architecture | Account/trial/entitlement/backend architecture (S08–S10 target) | Future slice contracts | HISTORICAL / DOCUMENTATION — KEEP | Keep (describes accepted future slices — not dead) | H |
| `docs/architecture/commands.md` | Architecture | Typed-command/capability rules + upstream provenance | S05 contracts; samsung implementation | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `docs/architecture/connection.md` | Architecture | Pairing/session/reconnect rules | S03–S06 contracts | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `docs/architecture/data.md` | Architecture | Room/DataStore/Keystore/backup rules | S03/S04 implementation + `BackupGuardTest`, `TvProfileSchemaTest` | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `docs/architecture/diagnostics.md` | Architecture | Bounded redacted local diagnostics; no-cloud-crash decision | S05/S13 contracts; guard vocabulary | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `docs/architecture/discovery.md` | Architecture | Bounded discovery + permission gate rules | S02 implementation; `LocalNetworkPermissionGate` | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `docs/architecture/flows.md` | Architecture | Cross-module sequences | Slice contracts | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `docs/architecture/lifecycle.md` | Architecture | Activity/process lifecycle rules | `MainActivity` no-configChanges design; `ActiveRemoteHost` | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `docs/architecture/modules.md` | Architecture | Module shape/ownership/seams ("Two production Gradle modules") | `settings.gradle.kts` header refs; guard ownership | HISTORICAL / DOCUMENTATION — KEEP | Keep (updated by PR #135; build files still lag it — R1) | H |
| `docs/architecture/presentation.md` | Architecture | Routes/screens/accessibility rules | Nav graph, screens, tokens | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `docs/architecture/protocol.md` | Architecture | Wire/generation knowledge owner (`samsung` only) | Samsung internal implementation | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `docs/architecture/release.md` | Architecture | CI/release/supply-chain architecture | `verify.yml` topology, guards, manifest allowlist, dependabot notes | KEEP — CLEANUP | Fix drift (R8): two diagnostic-mode lists still name the retired `device` mode and omit `dependency-state` (~L153, ~L390) | H |
| `docs/architecture/reliability.md` | Architecture | Reliability/performance targets + app-first evidence model | S05 verifier contract; guard vocabulary | HISTORICAL / DOCUMENTATION — KEEP | Keep (already reconciled: "no benchmark/profile pipeline", "no Baseline Profiles / Macrobenchmark/GMD pipeline") | H |
| `docs/architecture/samsung-interface.md` | Architecture | Caller-facing `SamsungTvs`/`RemoteSession` contract | `:app` usage; `ModuleBoundaryTest` | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `docs/architecture/security.md` | Architecture | Threat model/trust boundaries | Guard floor; CI trust model | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `docs/architecture/slices.md` | Architecture | S01–S17 route + per-slice acceptance | Issue compilation; PROJECT_STATE | HISTORICAL / DOCUMENTATION — KEEP | Keep (S05 section already reflects Rev 5 stock-debug verifier) | H |
| `docs/architecture/testing.md` | Architecture | Test seams, fixture schema, required contracts, physical matrix | Test-suite design; `FixtureProvenanceTest`; fixture layout | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
| `docs/architecture/ui-ux.md` | Architecture | Product-surface/interaction rules | S05 Remote/Settings contracts | HISTORICAL / DOCUMENTATION — KEEP | Keep | H |
### 4.6 `app/**` (83)

Build/lockfile/schema (3):

| Path | Domain | Purpose | Used by / evidence | Classification | Recommended action | Confidence |
|---|---|---|---|---|---|---|
| `app/build.gradle.kts` | Build | `:app` production build: AGP/Compose/KSP-Room config, detekt, dependencies, Issue #54 constraints, merged-manifest guards (`manifestPermissionAllowlist`, `adIdAbsentFromManifest`) | Gradle; guards consumed by `appTGuards`/`androidBuild` | KEEP — CLEANUP | Remove the retired `managedDevices { localDevices { pixel2api29 } }` block (R6); keep everything else | H |
| `app/gradle.lockfile` | Dependency state (generated) | Locked `:app` configurations | `dependencyLockCheck`; regeneration via trusted route | GENERATED / DERIVED — KEEP | Keep; `profileinstaller:1.4.0` entries here are legitimate AndroidX transitives, not benchmark remnants | H |
| `app/schemas/dev.anthracite.appt.data.AppTDatabase/1.json` | Generated contract | Room schema v1 export (identity hash, `tv_profiles` table) | KSP `room.schemaLocation`; read by `TvProfileSchemaTest` (forbidden-column check) and future migration tests | GENERATED / DERIVED — KEEP | Keep | H |

Production sources `app/src/main` (44):

| Path | Domain | Purpose | Used by / evidence | Classification | Recommended action | Confidence |
|---|---|---|---|---|---|---|
| `app/src/main/AndroidManifest.xml` | Manifest | 4 allowlisted install-time permissions, `AppTApplication`, single launcher activity, backup-exclusion wiring | AGP; checked by merged-manifest guards; `BackupGuardTest` reads the xml rules | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/AppSettingsLauncher.kt` | app/gate | fun-interface opening system app settings (Denied-gate path) | Constructed in `AppTApplication`; used by LocalNetwork surface + tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/AppTApplication.kt` | app/composition | Manual application-scope composition root (SamsungTvs, gates, Room, TvProfiles, PreferenceStore, ActiveRemoteHost); documents Hilt deferral | Manifest `android:name`; `MainActivity` | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/MainActivity.kt` | app/entry | Single activity; edge-to-edge; motion-scale provider; hosts nav graph; no `configChanges` (lifecycle.md) | Manifest intent-filter | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/data/AppTDatabase.kt` | app/data | Room database `appt.db` v1, no destructive fallback | `AppTApplication`; schema export | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/data/TvProfile.kt` | app/data | Room entity for the remembered television row | DAO; schema; tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/data/TvProfileDao.kt` | app/data | DAO contract (upsert/observe/markOpened) | Database; ViewModels via TvProfiles | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/data/TvProfiles.kt` | app/data | Store wrapper over DAO (+ name-source model) | Discovery/Pairing/Remote VMs; ActiveRemoteHost | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/discovery/DiscoveryScreen.kt` | app/discovery (S02) | Discovery Compose surface (cards, empty/error states, a11y) | Nav graph; DiscoveryScreenTest | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/discovery/DiscoveryTestTags.kt` | app/discovery | Stable test tags (no TV identity in tags) | Compose tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/discovery/DiscoveryUiState.kt` | app/discovery | Immutable discovery UI state | Screen + VM + tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/discovery/DiscoveryViewModel.kt` | app/discovery | Scan lifecycle/state holder over `SamsungTvs.discover()` | Nav graph wiring; VM tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/discovery/DisplayLabel.kt` | app/discovery | Last check before a TV name reaches a card | DiscoveryScreen; DisplayLabelTest | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/gate/DiscoveryPermissions.kt` | app/gate (S02) | Which runtime permissions V1 probes need (none at target 36) | LocalNetworkPermissionGate; gate tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/gate/LocalNetworkPermissionGate.kt` | app/gate | Production `PermissionGate`: acknowledge→Granted flow over SharedPreferences | AppTApplication; screens; tests | KEEP | Keep (`KEY_ACKNOWLEDGED` is a preference key, not protocol vocabulary) | H |
| `app/src/main/java/dev/anthracite/appt/gate/PermissionGate.kt` | app/gate | Gate interface + phases | App-wide gate contract | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/localnetwork/LocalNetworkScreen.kt` | app/localnetwork (S02) | Ordinary-language local-network explanation surface | Nav graph; screen tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/localnetwork/LocalNetworkTestTags.kt` | app/localnetwork | Stable test tags | Compose tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/localnetwork/LocalNetworkViewModel.kt` | app/localnetwork | Explanation state + gate acknowledgment | Screen; VM tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/navigation/AppTNavGraph.kt` | app/navigation | Route graph, back handling, launch routing over app-scoped dependencies | MainActivity; nav tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/navigation/Routes.kt` | app/navigation | Type-safe serializable routes | Nav graph | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/pairing/PairingScreen.kt` | app/pairing (S03) | TV-approval pairing surface (no protocol vocabulary) | Nav graph; screen tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/pairing/PairingTestTags.kt` | app/pairing | Stable test tags | Compose tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/pairing/PairingUiState.kt` | app/pairing | Pairing UI state | Screen + VM + tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/pairing/PairingViewModel.kt` | app/pairing | Pairing flow state holder | Nav wiring; VM tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/preferences/PreferenceStore.kt` | app/preferences | Typed DataStore preference keys | AppTApplication; Remote/settings flows; tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/remote/ActiveRemoteHost.kt` | app/remote | Application-scoped owner of the single live session; grace/retain semantics; `markOpened` on Ready | AppTApplication; Remote screens; ActiveRemoteHostTest | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/remote/RemoteScreen.kt` | app/remote (S03) | Remote Compose surface | Nav graph; screen tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/remote/RemoteTestTags.kt` | app/remote | Stable test tags (typed against `RemoteKey`) | Compose tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/remote/RemoteUiState.kt` | app/remote | Remote UI state incl. availability | Screen + VM + tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/remote/RemoteViewModel.kt` | app/remote | Command dispatch state holder | Nav wiring; VM tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/tokens/Theme.kt` | app/tokens (S01) | Material theme assembly from tokens | MainActivity | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/tokens/Tokens.kt` | app/tokens | Design tokens (color/type/space/size/motion, 48dp floor) | All surfaces; TokensTest | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/welcome/WelcomeScreen.kt` | app/welcome (S01) | Welcome surface | Nav graph; screen tests | KEEP | Keep | H |
| `app/src/main/java/dev/anthracite/appt/welcome/WelcomeTestTags.kt` | app/welcome | Stable test tags | Compose tests | KEEP | Keep | H |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | resources | Launcher foreground vector | `mipmap-anydpi/ic_launcher*.xml` | KEEP | Keep | H |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | resources | Themed-icon monochrome vector | Adaptive icon descriptor | KEEP | Keep | H |
| `app/src/main/res/mipmap-anydpi/ic_launcher.xml` | resources | Adaptive icon | Manifest `android:icon` | KEEP | Keep | H |
| `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml` | resources | Round adaptive icon | Manifest `android:roundIcon` | KEEP | Keep | H |
| `app/src/main/res/values/colors.xml` | resources | Base palette values | themes.xml | KEEP | Keep | H |
| `app/src/main/res/values/strings.xml` | resources | All user-facing copy (ordinary-language tested) | Manifest label; screens; `ForbiddenVocabularyTest` | KEEP | Keep | H |
| `app/src/main/res/values/themes.xml` | resources | `Theme.AppT` | Manifest theme refs | KEEP | Keep | H |
| `app/src/main/res/xml/backup_rules.xml` | resources | Full-backup exclusion rules (secrets never backed up) | Manifest `android:fullBackupContent`; `BackupGuardTest` | KEEP | Keep | H |
| `app/src/main/res/xml/data_extraction_rules.xml` | resources | Device-transfer exclusion rules | Manifest `android:dataExtractionRules`; `BackupGuardTest` | KEEP | Keep | H |

Instrumented tests `app/src/androidTest` (3):

| Path | Domain | Purpose | Used by / evidence | Classification | Recommended action | Confidence |
|---|---|---|---|---|---|---|
| `app/src/androidTest/java/dev/anthracite/appt/DiscoveryWalkthroughTest.kt` | instrumented | Installed-app discovery walkthrough evidence | `androidTest` source set (AGP); testing.md instrumented row; currently has no standing CI device runner | KEEP | Keep — accepted instrumented contract; not dead because no CI job runs it today (device evidence model changed in Rev 5, instrumented contracts remain) | H |
| `app/src/androidTest/java/dev/anthracite/appt/WelcomeLaunchTest.kt` | instrumented | Debug APK launches to Welcome (Issue #27 acceptance class) | Same as above | KEEP | Keep | H |
| `app/src/androidTest/java/dev/anthracite/appt/samsung/SamsungKeystoreContractTest.kt` | instrumented/security | Real-Keystore AES-GCM round trip for the secret store (S04 acceptance) | testing.md "Instrumented: Keystore round trip"; S04 physical/instrumented evidence | KEEP | Keep | H |

JVM tests + helpers `app/src/test` (33):

| Path | Domain | Purpose | Used by / evidence | Classification | Recommended action | Confidence |
|---|---|---|---|---|---|---|
| `app/src/test/java/dev/anthracite/appt/ModuleBoundaryTest.kt` | boundary test | Proves app sources never reference `samsung.internal` | `:app:testDebugUnitTest` | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/backup/BackupGuardTest.kt` | privacy test | Asserts backup/extraction XML excludes secret paths | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/data/FakeTvProfileDao.kt` | test double | In-memory DAO for VM tests | Discovery/Pairing/Remote/SavedPairing tests | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/data/TvProfileDaoTest.kt` | data test | Real Room DAO behavior | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/data/TvProfileSchemaTest.kt` | data/schema test | Exported schema contains no forbidden columns | Reads `app/schemas/.../1.json` | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/data/TvProfilesTest.kt` | data test | Store behavior | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/discovery/DiscoveryScreenTest.kt` | UI test | Discovery surface semantics/a11y | Robolectric Compose | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/discovery/DiscoveryViewModelTest.kt` | VM test | Discovery state machine | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/discovery/DisplayLabelTest.kt` | unit test | Name-display guard | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/flow/PairingToFirstControlFlowTest.kt` | flow test | Card → pairing → first control journey (Compose) | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/gate/LocalNetworkPermissionGateTest.kt` | gate test | Gate phases | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/localnetwork/ForbiddenVocabularyTest.kt` | privacy/vocab test | User copy has no protocol/mechanism vocabulary; KEY_* never in copy | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/localnetwork/LocalNetworkScreenTest.kt` | UI test | Explanation surface | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/localnetwork/LocalNetworkViewModelTest.kt` | VM test | Explanation state | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/navigation/AppTNavGraphTest.kt` | nav test | Route graph behavior | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/pairing/PairingScreenTest.kt` | UI test | Pairing surface | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/pairing/PairingViewModelTest.kt` | VM test | Pairing state | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/preferences/PreferenceStoreTest.kt` | data test | DataStore keys | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/remote/ActiveRemoteHostTest.kt` | host test | Session ownership/grace semantics | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/remote/RemoteScreenTest.kt` | UI test | Remote surface | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/remote/RemoteViewModelTest.kt` | VM test | Command dispatch state | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/savedpairing/SavedPairingUiTest.kt` | flow test | S04 saved-pairing UI behavior | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/testing/FakePermissionGate.kt` | test infra | Scripted gate | UI/flow tests | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/testing/FakeSamsungTvs.kt` | test infra | Fake `SamsungTvs` (no WebSocket types; testing.md) | All app UI/VM/flow tests | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/testing/MainDispatcherRule.kt` | test infra | Main-dispatcher test rule | VM tests | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/testing/SemanticsAssertions.kt` | test infra | Touch-target/a11y assertions | Compose tests | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/testing/TestFlows.kt` | test infra | Shared test-flow helpers | Flow tests | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/testing/TestPreferences.kt` | test infra | In-memory DataStore for tests | VM/screen tests | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/testing/TestScopeSettleTest.kt` | test-infra test | Proves the settle helper itself | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/tokens/Contrast.kt` | test helper | Contrast computation helper for token tests | `TokensTest.kt` (git grep confirms sole consumer) | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/tokens/TokensTest.kt` | token test | Token floors (48dp etc.) | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/java/dev/anthracite/appt/welcome/WelcomeScreenTest.kt` | UI test | Welcome surface | testDebugUnitTest | KEEP | Keep | H |
| `app/src/test/resources/robolectric.properties` | test config | Native graphics mode + pinned SDK 34 for Robolectric | Robolectric discovery | KEEP | Keep | H |

### 4.7 `samsung/**` (96)

Module files (3):

| Path | Domain | Purpose | Used by / evidence | Classification | Recommended action | Confidence |
|---|---|---|---|---|---|---|
| `samsung/README.md` | module docs | Module orientation | Humans/agents | KEEP | Keep | H |
| `samsung/build.gradle.kts` | Build | `:samsung` library build; dependency-boundary comments; detekt; Issue #54 constraints | Gradle; `samsungDependencyBoundary` guard | KEEP — CLEANUP | Remove retired `managedDevices { localDevices { pixel2api29, pixel2api35 } }` block (R6); keep everything else | H |
| `samsung/gradle.lockfile` | Dependency state (generated) | Locked `:samsung` configurations (clean: no benchmark/telemetry artifacts) | `dependencyLockCheck` | GENERATED / DERIVED — KEEP | Keep | H |

Instrumented (1): `samsung/src/androidTest/java/dev/anthracite/appt/samsung/internal/NameScrubberAndroidTest.kt` — instrumented redaction check (testing.md instrumented row) — **KEEP** (no standing CI runner today; accepted future instrumented evidence). H.

Production sources (34 = manifest + 5 public + 28 internal):

| Path | Purpose | Used by / evidence | Classification | Action | Conf |
|---|---|---|---|---|---|
| `samsung/src/main/AndroidManifest.xml` | Declares the four discovery permissions next to the code that needs them | Merged into app manifest; manifest guards | KEEP | Keep | H |
| `.../samsung/DiscoveryTypes.kt` | Caller-facing discovery types (`DiscoveredTv`, …) | `:app` discovery VM/screen | KEEP | Keep | H |
| `.../samsung/RemoteSession.kt` | Caller-facing session interface + `TvCommand`/`RemoteKey` typed surface | `:app` remote flows | KEEP | Keep | H |
| `.../samsung/SamsungModule.kt` | Production composition entry (`samsungTvs(context)`); DI-module-ready seam | `AppTApplication`; `SamsungModuleGuardTest` | KEEP | Keep | H |
| `.../samsung/SamsungTvs.kt` | The deep-module interface (discover/open/command/forget/states) | `:app` everywhere; all fakes | KEEP | Keep | H |
| `.../samsung/SessionTypes.kt` | Session state/result types | App + internal | KEEP | Keep | H |
| `.../internal/AirPlayTxt.kt` | AirPlay `.txt` capability parse (soundbar filtering evidence) | NsdAirPlayBrowser; AirPlayTxtTest | KEEP | Keep | H |
| `.../internal/AndroidDiscoveryTransport.kt` | Production discovery transport adapter | SamsungTvsImpl; scripted in tests | KEEP | Keep | H |
| `.../internal/AndroidKeystoreCipher.kt` | Keystore AES-GCM cipher for secret storage | KeystoreSamsungStore; contract tests | KEEP | Keep | H |
| `.../internal/BlockingIo.kt` | Bounded blocking-IO helpers | Transports; tests | KEEP | Keep | H |
| `.../internal/BoundedCallback.kt` | One-shot bounded callback | Discovery/session internals; tests | KEEP | Keep | H |
| `.../internal/BoundedHttp.kt` | Bounded HTTP read for device-info | DeviceInfo path; tests | KEEP | Keep | H |
| `.../internal/ConfirmedTelevision.kt` | Confirmed-TV correlation model | Discovery; tests | KEEP | Keep | H |
| `.../internal/DeviceInfo.kt` | Device-info fetch/parse (bounded) | Discovery scan; DeviceInfoParserTest | KEEP | Keep | H |
| `.../internal/DiscoveryScan.kt` | Scan orchestration with 10s bound | Discovery; tests | KEEP | Keep | H |
| `.../internal/DiscoveryTransport.kt` | Discovery transport seam | Impl + fakes | KEEP | Keep | H |
| `.../internal/KeystoreSamsungStore.kt` | Keystore-backed secret store (`noBackupFilesDir/samsung-secrets/`) | SamsungTvsImpl; KeystoreSamsungStoreTest; S04 acceptance | KEEP | Keep | H |
| `.../internal/LanPolicy.kt` | LAN-only address policy | Transports; LanPolicyTest | KEEP | Keep | H |
| `.../internal/LiveSession.kt` | Live session state machine | SamsungTvsImpl; session tests | KEEP | Keep | H |
| `.../internal/NameScrubber.kt` | Redacts names/identities from diagnostics | Diagnostics path; NameScrubberAndroidTest | KEEP | Keep | H |
| `.../internal/NsdAirPlayBrowser.kt` | NSD AirPlay browser (capability evidence) | Discovery; tests | KEEP | Keep | H |
| `.../internal/OkHttpSessionTransport.kt` | TLS WebSocket session transport (port 8002) via OkHttp | ProductionSessionTransport; tests | KEEP | Keep | H |
| `.../internal/PairingSecret.kt` | Pairing secret model (managed-device note references testing.md) | KeystoreSamsungStore; tests | KEEP | Keep | H |
| `.../internal/PlaintextWebSocketTransport.kt` | Bounded raw-socket WebSocket (port 8001) | Session path; tests | KEEP | Keep | H |
| `.../internal/ProductionSessionTransport.kt` | Selects TLS vs plaintext transport per TV | LiveSession; ProductionSessionTransportTest | KEEP | Keep | H |
| `.../internal/RemoteChannel.kt` | Command channel over session | Session; RemoteChannelTest | KEEP | Keep | H |
| `.../internal/SamsungDeviceRecord.kt` | Device record persistence model | Saved pairing; tests | KEEP | Keep | H |
| `.../internal/SamsungTvsImpl.kt` | `internal` implementation of `SamsungTvs` | SamsungModule; contract tests | KEEP | Keep | H |
| `.../internal/SessionGeneration.kt` | Generation classification | Protocol selection; tests | KEEP | Keep | H |
| `.../internal/SessionTransport.kt` | Session transport seam | Impl + fakes | KEEP | Keep | H |
| `.../internal/Ssdp.kt` | SSDP message model | SsdpClient; SsdpTest | KEEP | Keep | H |
| `.../internal/SsdpClient.kt` | Bounded SSDP client | DiscoveryScan; tests | KEEP | Keep | H |
| `.../internal/TvIdentity.kt` | Stable/minted identity logic | Discovery/rediscovery; TvIdentityTest | KEEP | Keep | H |
| `.../internal/WebSocketFrames.kt` | WebSocket frame codec (defensive) | Transports; WebSocketFramesTest | KEEP | Keep | H |

Test sources (24) — all **KEEP**, consumed by `:samsung:test` (authoritative verification evidence, `androidUnit` domain): `AirPlayTxtTest.kt`, `BoundedCallbackTest.kt`, `BoundedHttpResponseTest.kt`, `DeviceInfoParserTest.kt`, `FixtureProvenanceTest.kt` (also polices fixture hygiene), `FixtureTransport.kt` (replay transport), `Fixtures.kt` (fixture loader), `InMemorySamsungStore.kt` (fake secret store), `KeystoreSamsungStoreTest.kt`, `LanPolicyTest.kt`, `LoopbackSocketsTest.kt`, `OkHttpSessionTransportTest.kt`, `PlaintextWebSocketTransportTest.kt`, `ProductionSessionTransportTest.kt`, `RemoteChannelTest.kt`, `SamsungModuleGuardTest.kt` (guards module seam), `SamsungTvsDiscoveryTest.kt`, `SamsungTvsSavedPairingTest.kt`, `SamsungTvsSessionTest.kt`, `ScriptedSessionTransport.kt`, `SessionFixture.kt`, `SsdpTest.kt`, `TvIdentityTest.kt`, `WebSocketFramesTest.kt`. Confidence H for all.

Fixture data (34 files = 17 cases × `provenance.json` + `trace.jsonl`) under `samsung/src/test/resources/samsung/fixtures/`: `airplay-samsung-tv`, `bluray-rcr`, `device-info-unreadable`, `dial-filter`, `duplicate-uuid`, `identity-mismatch`, `late-tv`, `malformed-frame`, `no-stable-uuid`, `rediscovery-address-change`, `same-name-two-tvs`, `soundbar-airplay`, `ssdp-tizen-tv`, `tls-approval-then-volume`, `token-resume`, `unauthorized-with-token`, `unsupported-no-keys` — all **KEEP** (TEST-VECTOR/DATA; schema owned by testing.md#fixtures; consumed dynamically by `Fixtures.kt`; provenance policed by `FixtureProvenanceTest` + secret-scan). Confidence H.

### 4.8 `macrobenchmark/**` (4) — RETIRED (S05 Contract Revision 5)

| Path | Domain | Purpose (historical) | Used by / evidence | Classification | Recommended action | Confidence |
|---|---|---|---|---|---|---|
| `macrobenchmark/build.gradle.kts` | Retired benchmark build | `com.android.test` module targeting `:app`, benchmark variant, Wire constraint for androidx.benchmark transitive | `settings.gradle.kts` include; root `androidBuild`; guards | OBSOLETE / IRRELEVANT | Remove with Batch 1 (superseded by Rev 5 app-first performance model) | H |
| `macrobenchmark/gradle.lockfile` | Retired lock state | Locked benchmark configurations | `dependencyLockCheck` (while module exists); `diagnose.yml` already lists it as delete-only `retired` | OBSOLETE / IRRELEVANT | Delete with Batch 1; also drop the `retired` array in diagnose.yml then | H |
| `macrobenchmark/src/main/AndroidManifest.xml` | Retired benchmark manifest | Empty test-only manifest | AGP | OBSOLETE / IRRELEVANT | Delete with Batch 1 | H |
| `macrobenchmark/src/main/java/dev/anthracite/appt/macrobenchmark/StartupBenchmark.kt` | Retired benchmark test | Cold-start Macrobenchmark measurement (S01 skeleton proof) | `:macrobenchmark:assembleBenchmark` compile only; no CI executes it since PR #135 | OBSOLETE / IRRELEVANT | Delete with Batch 1; replaced by structural tests + slice-owned stock-debug physical verifier | H |

### 4.9 `backend/**` (13)

| Path | Domain | Purpose | Used by / evidence | Classification | Recommended action | Confidence |
|---|---|---|---|---|---|---|
| `backend/.gitignore` | Package hygiene | Ignores node_modules/lib/coverage/tsbuildinfo | Git; overlaps root `.gitignore` entries (D5) | KEEP | Keep (intentional package-local hygiene; overlap is harmless) | H |
| `backend/.prettierignore` | Formatter config | Excludes lib/coverage/lockfile from Prettier | `npm run format:check` | KEEP | Keep | H |
| `backend/.prettierrc.json` | Formatter config | Prettier style | verify:static | KEEP | Keep | H |
| `backend/eslint.config.mjs` | Lint config | Typed ESLint (no-console per diagnostics privacy) | `npm run lint` | KEEP | Keep | H |
| `backend/jest.config.js` | Test config | Jest + ts-jest + 100% coverage threshold | `npm test`, verify.yml backend-test | KEEP | Keep | H |
| `backend/package-lock.json` | Dependency state (generated) | npm lockfile; `npm ci` only | verify/diagnose backend jobs | GENERATED / DERIVED — KEEP | Keep | H |
| `backend/package.json` | Package manifest | Scripts + pinned devDependencies; `deployable:false` skeleton | npm; CI; Dependabot | KEEP | Keep | H |
| `backend/src/index.ts` | Backend entry | Exports package identity; documents deliberately-absent surfaces | Tests; future functions | KEEP | Keep | H |
| `backend/src/package-identity.ts` | Backend source | Typed identity with `deployable:false` tripwire | index.ts; tests | KEEP | Keep | H |
| `backend/test/package-identity.test.ts` | Backend test | Tripwire tests | Jest | KEEP | Keep | H |
| `backend/ts-jest-transformer.js` | Test tooling | Source-map path normalization so LCOV `SF:` paths are repo-relative for Sonar | jest.config.js transform; Sonar import depends on it | KEEP | Keep | H |
| `backend/tsconfig.json` | Compiler config | Strict TS config for src | typecheck/build | KEEP | Keep | H |
| `backend/tsconfig.test.json` | Compiler config | Test-including TS config | typecheck; eslint project ref | KEEP | Keep | H |

### 4.10 `tools/**` (19)

| Path | Domain | Purpose | Used by / evidence | Classification | Recommended action | Confidence |
|---|---|---|---|---|---|---|
| `tools/ci/diagnose-focus.mjs` | CI tooling | Per-mode focus resolver returning argv vectors; owns the 8-mode list; dependency-state regeneration command | `diagnose.yml` (every mode job); tested by `diagnose-focus.test.mjs` | KEEP | Keep | H |
| `tools/ci/dispatch-workflow.mjs` | CI tooling | Label→workflow dispatch logic for the bridge (default-branch dispatch, head resolution) | `agent-control.yml`; tested | KEEP | Keep | H |
| `tools/ci/project_state_contract.py` | Control-plane validator | Checks PROJECT_STATE.md structural contract | `verify.yml` repo-policy; tested | KEEP | Keep | H |
| `tools/ci/purge-actions.mjs` | CI tooling | Bounded destructive purge logic (cancel→settle→delete caches→delete runs→verify) | `maintenance.yml`; tested | KEEP | Keep | H |
| `tools/ci/validate-sonar-inputs.py` | CI security | Validates inert Sonar inputs before the secret-bearing scanner step | `verify.yml` quality-platform | KEEP | Keep | H |
| `tools/ci/test/assert-dispatch-target.test.mjs` | CI test | Proves the assertion's decision logic against fake `gh` | verify.yml repo-policy | KEEP | Keep | H |
| `tools/ci/test/diagnose-focus.test.mjs` | CI test | Focus grammar proofs (fail-closed cases) | verify.yml repo-policy | KEEP | Keep | H |
| `tools/ci/test/dispatch-workflow.test.mjs` | CI test | Dispatch request-shape proofs (rejects SHA-as-ref, mutable refs) | verify.yml repo-policy | KEEP | Keep | H |
| `tools/ci/test/project-state-contract.test.py` | CI test | Tests the state contract checker | verify.yml repo-policy | KEEP | Keep | H |
| `tools/ci/test/purge-actions.test.mjs` | CI test | Purge logic against fake API | verify.yml repo-policy | KEEP | Keep | H |
| `tools/ci/test/sonar-boundary.test.py` | CI test | Parsed-YAML proof of the Sonar trust boundary in verify.yml | verify.yml repo-quality | KEEP | Keep | H |
| `tools/ci/test/workflow-cache-contract.test.py` | CI test | Parsed-YAML cache-mode contract (incl. `cache-mode` exception for pinned actionlint) | verify.yml repo-quality | KEEP | Keep | H |
| `tools/secret-scan/secret-scan.mjs` | Security tooling | Repo secret/credential-file scanner (`noCredentialFilesInRepo`) | verify.yml repo-policy; BUILD.md local list | KEEP | Keep | H |
| `tools/secret-scan/test/secret-scan.test.mjs` | Security test | Scanner self-tests | verify.yml repo-policy | KEEP | Keep | H |
| `tools/security/enforce-dependency-policy.mjs` | Security tooling | Enforces strict dependency policy over Dependency Review output (vulnerable-changes) | verify.yml dependency-review job; tested | KEEP | Keep | H |
| `tools/security/enforce-gradle-tooling-constraints.mjs` | Security tooling | Keeps buildscript-classpath constraints and verification metadata honest for plugin-transitive advisories (Issues #54/#68) | verify.yml repo-policy; BUILD.md; tested | KEEP | Keep | H |
| `tools/security/run.sh` | Security entrypoint | One-command local security checks (`deps`, `build` CodeQL extraction repro); fails closed | BUILD.md documented local route; ShellCheck/shfmt-checked | KEEP | Keep | H |
| `tools/security/test/enforce-dependency-policy.test.mjs` | Security test | Policy enforcer tests | verify.yml repo-policy | KEEP | Keep | H |
| `tools/security/test/enforce-gradle-tooling-constraints.test.mjs` | Security test | Tooling-constraint enforcer tests | verify.yml repo-policy | KEEP | Keep | H |
---

## 5. Dead / obsolete candidates (full evidence record)

The dead-code safety test (10 questions) was applied to **every** candidate below. Nothing not listed here showed any deadness signal during the sweep (`git grep` for consumers of every public symbol family, workflow wiring, AGP/npm convention discovery, fixture dynamic use).

### 5.1 CONFIRMED OBSOLETE — retired by S05 Contract Revision 5, removal assigned to PR #118 by PROJECT_STATE "Next Authorized Action"

| # | Path(s) | Evidence of obsolescence | Why unnecessary now | Replacement | Removal risk | Verification required before deletion |
|---|---|---|---|---|---|---|
| O1 | `macrobenchmark/build.gradle.kts`, `macrobenchmark/gradle.lockfile`, `macrobenchmark/src/main/AndroidManifest.xml`, `macrobenchmark/src/main/java/.../StartupBenchmark.kt` (+ the `macrobenchmark/**` dirs) | PROJECT_STATE Rev 5 retires `:macrobenchmark` and Baseline Profile; modules.md now declares "Two production Gradle modules. No benchmark/test-only application module"; reliability.md: "AppT does not maintain committed Baseline Profiles or a repository-owned Macrobenchmark/GMD pipeline"; no workflow compiles/runs it except root `androidBuild`'s `assembleBenchmark` dependency | The module's only function (startup timing harness) is superseded by the app-first performance model; PROJECT_STATE: "do not repair the failed benchmark harness" | Structural/launch tests + S05 stock-debug physical verifier (PR #118) | Low–medium: requires simultaneous edits to settings/root build/guards/catalog/template and dependency-state regeneration via the trusted `ci:dependency-state` route | Full `verify` (android-build no longer includes the module; lockfiles + verification metadata regenerated and strict-checked); `noProductionModuleDependsOnBenchmark` must be removed in the same change (it hard-references the project) |
| O2 | `settings.gradle.kts`: `include(":macrobenchmark")` + "Exactly three Gradle modules" comment | Same decision | Module graph must match the accepted two-module shape | — | None if done with O1 | Same as O1 |
| O3 | `build.gradle.kts`: `androidBuild` dependency on `:macrobenchmark:assembleBenchmark` (+ task description text, detekt-scope comment) | Same decision | `androidBuild` should only assemble production debug APK | — | Intended behavior change: `androidBuild`/`ciCheck`/`ci:android-build` no longer compile the retired module | O1 verification |
| O4 | `gradle/guards.gradle.kts`: `noProductionModuleDependsOnBenchmark` guard (+ `appTGuards` wiring, header comment naming it) | Guard exists only to police the retired module; calls `project(":macrobenchmark")` and would fail configuration once the module is gone | No benchmark module ⇒ nothing to police; release.md's "no benchmark code in shipped artifacts" guarantee becomes vacuous-but-true with two production modules | — | Low; the architecture guarantee survives via module shape | O1 verification; keep any docs naming the check list in sync |
| O5 | `gradle/libs.versions.toml`: versions `androidx-benchmark`, `androidx-test-uiautomator`; libraries `androidx-benchmark-macro-junit4`, `androidx-test-uiautomator`; plugin alias `android-test` | Sole consumer is `macrobenchmark/build.gradle.kts` (verified by git grep: only `macrobenchmark/build.gradle.kts` + root `apply false` use them) | Retired dependencies must leave the declaration surface (release.md: catalog mirrors accepted graphs) | — | None with O1 | `versionCatalogPinned`, lock regeneration |
| O6 | `gradle/verification-metadata.xml`: stale components only reachable via `:macrobenchmark` graphs (`androidx.benchmark:benchmark-common/-macro/-macro-junit4/-traceprocessor/-traceprocessor-android` 1.5.0; `androidx.test.uiautomator:uiautomator/-shell/-shell-android` 2.4.0; `com.squareup.wire:wire-runtime/-jvm` 6.4.7; `androidx.profileinstaller:profileinstaller` **1.4.1**) | These coordinates appear in `macrobenchmark/gradle.lockfile` and in no other lockfile; `app/gradle.lockfile` only carries `profileinstaller:1.4.0` (legitimate AndroidX transitive on app runtime/lint classpaths) | Strict verification keeps stale blocks only until the consuming graph disappears | — | Low; strict verification fails loudly if a removed entry was still needed (self-checking, per BUILD.md) | Regenerate via `--write-verification-metadata sha256` on the post-removal graph; diff-review the `<components>` change |
| O7 | `app/build.gradle.kts` `managedDevices { localDevices { pixel2api29 } }`; `samsung/build.gradle.kts` `managedDevices { localDevices { pixel2api29, pixel2api35 } }` | Rev 5 retires hosted Gradle Managed Devices; PROJECT_STATE next action removes "managed-device build definitions"; after PR #135 no workflow invokes `*<device>*AndroidTest` GMD tasks; docs no longer describe them | GMD definitions only exist to feed hosted device runs that were retired | Instrumented contracts can run on any connected device / slice-owned stock-debug diagnostics | Low: removes unused AGP-generated tasks only | `androidStatic`/`androidBuild` green; no workflow references remain (git grep `pixel2api`) |
| O8 | `gradle/verification-metadata.template.xml`: regeneration command naming `:macrobenchmark:assembleBenchmark` + stale bootstrap-era sentence ("CI does this automatically while the repository is not yet bootstrapped") | Rev 5; bootstrap completed long ago | Template must describe the current regeneration command | — | None | Review-only |

### 5.2 TRANSITIONAL (explicitly bounded)

| Path | Condition for disappearance |
|---|---|
| `.github/workflows/diagnose.yml` — `retired=("macrobenchmark/gradle.lockfile")` array in the dependency-state boundary check | Disappears when the lockfile deletion lands (Batch 1). Until then it is the mechanism that *permits exactly the deletion and nothing else* — keep it. |
| `.github/workflows/codeql.yml` — the entire Advanced-Setup workflow (file-level classification TRANSITIONAL) | Disappears when GitHub-managed Default Setup bundles support Kotlin 2.4.20 and Default Setup passes (documented migration-back condition). Until then it is mandatory SAST — not dead. |

### 5.3 Evaluated and explicitly NOT dead

- **`app/src/androidTest/**` (3 files) and `samsung/src/androidTest/**` (1 file):** no CI job currently executes them since the device-mode retirement. They are nevertheless accepted instrumented contracts (testing.md "Instrumented" row; S04 Keystore acceptance evidence) with a concrete continuing responsibility; "not executed by CI today" does not satisfy the dead-code test (questions 4/8/9 answer yes). KEEP.
- **`backend/src/**`:** S01 non-deploying skeleton by design; the `deployable:false` tripwire test is its consumer. KEEP.
- **`docs/architecture/account-entitlement.md`, `flows.md`, `ui-ux.md`, etc.:** describe accepted future slices (S08–S17); future-slice ownership ≠ dead (constraint: "Do not confuse an accepted future responsibility with currently dead code"). KEEP.
- **`.project-ai/skills/*` not obviously active (incident-response, postmortem-learning, release-deployment, observability-operations-design):** routed conditionally by `capabilities.md`; structural validation runs in CI. KEEP.
- **Fixtures for cases not yet referenced by a named test (e.g. `bluray-rcr`, `late-tv`, `same-name-two-tvs`):** loaded dynamically by `Fixtures.kt` directory walk and asserted by discovery tests; fixture-set growth is slice-gated. KEEP.
- **`gradlew.bat`:** no Windows CI, but the wrapper is a committed toolchain artifact by policy (BUILD.md). KEEP (GENERATED/DERIVED).
- **`app/src/test/java/.../tokens/Contrast.kt`:** sole consumer `TokensTest.kt` confirmed by grep — a used helper, not dead. KEEP.

**Result: zero DEAD files at this revision.** The only deletions ever recommended are the Rev-5 retired set (O1–O8), and those are sanctioned by an accepted decision and an explicit in-flight assignment (PR #118), not by this audit.

---

## 6. Redundancy / duplication findings

| ID | Files involved | Duplicated responsibility | Intentional? | Canonical owner | Recommended consolidation |
|---|---|---|---|---|---|
| D1 | `.markdownlint.jsonc` ↔ `.markdownlint-cli2.jsonc` | Identical markdownlint rule set (MD013/024/033/041/058/060 disabled) declared twice | No — accretion | `.markdownlint-cli2.jsonc` (CI passes it explicitly as `config:`) | Remove `.markdownlint.jsonc` after confirming no local editor relies on its auto-discovery |
| D2 | `.markdownlintignore` ↔ `.markdownlint-cli2.jsonc` `ignores` | Ignore lists with *divergent* contents (`.project-ai/**` only in the cli2 config; `.agents` only in `.markdownlintignore`) | No | `.markdownlint-cli2.jsonc` | Merge into one ignore owner to eliminate drift risk |
| D3 | `app/build.gradle.kts` ↔ `samsung/build.gradle.kts` ↔ `macrobenchmark/build.gradle.kts` ↔ root `build.gradle.kts` buildscript block | Identical Bouncy Castle/commons-lang3/httpclient `androidLintTool` constraint lists (Issue #54) | **Yes** — each classpath seam must constrain itself; parity is machine-policed by `enforce-gradle-tooling-constraints.mjs` | Root `build.gradle.kts` comment block + the enforcer | Keep (remove the macrobenchmark instance with Batch 1) |
| D4 | `app` ↔ `samsung` ↔ `macrobenchmark` `kotlin { compilerOptions }` and detekt blocks | Identical jvmTarget/Werror/detekt wiring | **Yes** — jvmTarget must match each module's `compileOptions`; detekt blocks are documented "Identical to :app on purpose" | Per-module (Gradle requires it) | Keep |
| D5 | `backend/.gitignore` ↔ root `.gitignore` | node_modules/lib/coverage/tsbuildinfo ignores | Yes (package-local hygiene; standard npm practice) | Both | Keep; no action |
| D6 (checked, not a finding) | `verify.yml` repo-policy runs `project-state-contract.test.py` **and** `project_state_contract.py` | — | Not duplication: one tests the checker, the other executes the check | — | None |
| D7 (checked, not a finding) | Guard dedup candidates (`noTelemetryDependency`/`samsungDependencyBoundary`/`samsungGraphExcludesFirebase`) | — | Already deduplicated in Issue #88 with rationale comments; `samsungGraphExcludesFirebase` is a deliberate re-exposure of a testing.md contract name | `gradle/guards.gradle.kts` | None |

No duplicated scripts, CI logic, domain models, validation logic, secret/device-identity boundaries, or parallel interface implementations were found anywhere else. `FakeSamsungTvs` (app test) and `ScriptedSessionTransport`/`FixtureTransport` (samsung test) are deliberately different seams per testing.md, not duplicates.

---

## 7. Cleanup-but-keep findings (necessary files with in-file cleanup opportunities)

| File | Cleanup opportunity | Notes |
|---|---|---|
| `build.gradle.kts` | Drop `:macrobenchmark:assembleBenchmark` from `androidBuild` (+ description text); retire the `:macrobenchmark` detekt-scope comment | Batch 1; keep all constraint blocks and lifecycle tasks |
| `settings.gradle.kts` | Drop `include(":macrobenchmark")`; change the "Exactly three Gradle modules" comment to the accepted two-module shape (quote modules.md) | Batch 1 |
| `gradle/guards.gradle.kts` | Remove `noProductionModuleDependsOnBenchmark` + `appTGuards` entry + header check-name line | Batch 1 (only valid together with module deletion) |
| `gradle/libs.versions.toml` | Remove 2 versions + 2 libraries + 1 plugin alias (R3); optionally refresh the "S01 walking skeleton" header framing (catalog now spans S01–S04) | Batch 1 |
| `gradle/verification-metadata.template.xml` | Regeneration command without `:macrobenchmark:assembleBenchmark`; drop stale bootstrap sentence | Batch 1 |
| `app/build.gradle.kts`, `samsung/build.gradle.kts` | Remove retired `managedDevices/localDevices` blocks (comments reference the retired device-diagnostic use) | Batch 1 |
| `docs/BUILD.md` | Remove `device` mode references (×2); list all 8 diagnose modes; add `ci:dependency-state` to the label list; clarify the Automatic Dependency Submission wording | Batch 2 |
| `docs/architecture/release.md` | Update both diagnostic-mode lists (drop `device`, add `dependency-state`) | Batch 2 |
| `.markdownlint.jsonc` / `.markdownlintignore` | Consolidate per D1/D2 | Batch 3 |
| `gradle/verification-metadata.xml` | Prune stale benchmark/uiautomator/wire/profileinstaller-1.4.1 components during post-Batch-1 regeneration (never hand-edited outside regeneration) | Batch 1 follow-through |

Everything else in the KEEP set has no cleanup debt worth flagging: comments are unusually well-maintained, and where history is referenced (Issue numbers, dedup rationale) it is accurate against the git record.

---

## 8. Architecture violations or questionable boundaries

**No violations found.** Evidence per boundary class requested:

| Boundary | Evidence checked | Result |
|---|---|---|
| `app → samsung` direction (and no reverse) | `samsungDependencyBoundary` guard (configurations + project edges); git grep for `:app` deps in samsung build | Clean |
| Samsung internals leaking into Compose/UI | `ModuleBoundaryTest` (source scan) + git grep `samsung.internal` in `app/` | Zero hits |
| Protocol `KEY_*` vocabulary outside Samsung-owned implementation | git grep `KEY_` across `app/src/main`: only `KEY_ACKNOWLEDGED` (a SharedPreferences key in the gate, unrelated to wire protocol); `ForbiddenVocabularyTest.theRemoteCopyNeverNamesAKeyOnTheWire` polices user-facing copy | Clean |
| Build/test tooling leaking into production code | No Gradle/task types imported by app/samsung production sources; `appTGuards` runs as separate tasks | Clean |
| Diagnostics crossing privacy boundaries | No diagnostics recorder exists on main yet; backup/exclusion rules tested (`BackupGuardTest`); secret-scan + fixture provenance tests enforce no IP/MAC/token/email in committed fixtures | Clean |
| Test/fake implementations reachable from production | `FakeSamsungTvs`, `FakePermissionGate`, `ScriptedSessionTransport`, `InMemorySamsungStore` all live in test source sets; production composition only in `AppTApplication`/`SamsungModule` | Clean |
| Debug verifier reachable from release | Verifier not yet on `main` (PR #118 owns it with the stock-debug + release-exclusion boundary per PROJECT_STATE); nothing here preempts that boundary | Clean (watch item for PR #118 review) |
| Backend concerns leaking into Android | Backend is a separate npm package; no Firebase client artifacts on Android graphs (`noFirestoreClientInApp`, `noTelemetryDependency`) | Clean |
| Secret/device-identity boundaries duplicated/bypassed | One secret store (`KeystoreSamsungStore`); one identity model (`TvIdentity`); manifests exclude backup; no second identity mechanism found | Clean |
| Repo control-plane logic duplicated in target-controlled code | Control plane lives only in `.project-ai/` + `.github/` + `tools/`; workflows execute target code only under read-only permissions after dispatch assertion | Clean |

One **watch item** (not a violation at this revision): `app/src/androidTest/.../samsung/SamsungKeystoreContractTest.kt` lives in `:app`'s androidTest rather than `:samsung`'s. This is the accepted placement (instrumented Keystore round trip needs the app package context; testing.md assigns it to instrumented evidence). No action.

---

## 9. Documentation drift

| # | Document | Drift | Reality (executable truth) | Fix |
|---|---|---|---|---|
| DR1 | `docs/BUILD.md` (~L184) | Lists diagnose modes as "`app-unit`, `samsung-unit`, `android-static`, `android-build`, `dependency-state`, or `device`" | `diagnose.yml` declares 8 modes: app-unit, samsung-unit, android-static, android-build, backend-static, backend-test, backend, dependency-state; **no `device` mode** (retired by PR #135) | Batch 2 |
| DR2 | `docs/BUILD.md` (~L449) | Focus example `gh workflow run diagnose.yml -f mode=device -f focus=class:…` | Mode no longer exists | Batch 2 |
| DR3 | `docs/BUILD.md` (~L255) | Command-label list omits `ci:dependency-state` | `agent-control.yml` allowlist includes `ci:dependency-state` | Batch 2 |
| DR4 | `docs/architecture/release.md` (~L153, ~L390) | Both diagnostic-mode lists still contain `device` and omit `dependency-state` | Same as DR1 | Batch 2 |
| DR5 | `gradle/verification-metadata.template.xml` | Regeneration command still includes `:macrobenchmark:assembleBenchmark`; stale "not yet bootstrapped" sentence | Module retired; bootstrap complete | Batch 1 |
| DR6 | `settings.gradle.kts` comment ("Exactly three Gradle modules … `:macrobenchmark` test-only …") and `build.gradle.kts` comments | Describe the pre-Retirement shape | modules.md now owns the two-module shape | Batch 1 |

Checked and **not** drift: `docs/architecture/modules.md` (updated by PR #135), `reliability.md` (explicit no-benchmark/no-GMD statements), `slices.md` S05/S15 (stock-debug verifier model), `testing.md` (app-first performance row), README (PR #135 trimmed the stale line). `release.md`'s references to `deep.yml`/`release.yml` are explicitly future-conditional, not drift. `BUILD.md`'s "Automatic Dependency Submission (Gradle) workflow" phrasing describes a repository *setting*-level GitHub feature (no workflow file is expected in-repo); minor wording clarification suggested in Batch 2.

---

## 10. Dependency / build-state findings

| Area | Finding | Status |
|---|---|---|
| Unused catalog entries | `androidx-benchmark` (version), `androidx-test-uiautomator` (version), `androidx-benchmark-macro-junit4`, `androidx-test-uiautomator` (libraries), `android-test` (plugin) — sole consumer `:macrobenchmark` | Remove in Batch 1 (R3) |
| All other catalog entries | Cross-checked against module dependency blocks: every remaining version/library/plugin has a live consumer in `app`, `samsung`, or root plugins (agp, kotlin compose/serialization, ksp, detekt, spotless, kover) | Clean |
| Stale plugins | `android-test` plugin alias only; no stale applied plugins | Batch 1 |
| Lock state | Root, settings, app, samsung lockfiles match their graphs (enforced in CI by `dependencyLockCheck`); `macrobenchmark/gradle.lockfile` is retired state | Delete macrobenchmark lockfile in Batch 1; regenerate others only if the graph changes |
| Verification metadata | 11 stale components tied to `:macrobenchmark` (listed in O6); `profileinstaller:1.4.0` must stay; two historical `profileinstaller` versions (1.4.0 app graph / 1.4.1 benchmark graph) explain the apparent duplicate | Prune via regeneration in Batch 1 |
| Gradle tasks/configs | `resolveAndLockAll`, `androidFormat/Static/Build/Unit`, `ciCheck`, guard tasks all wired to CI or documented local use; the only dead task edge is the retiring `:macrobenchmark:assembleBenchmark` | Batch 1 |
| npm | All backend devDependencies map to wired scripts (tsc, eslint, prettier, jest, ts-jest, knip, rimraf); `knip` runs in verify:static; no unused scripts; lockfile used by `npm ci` | Clean |
| Dependabot config | Covers the three real ecosystems (gradle `/`, npm `/backend`, github-actions `/`); no ecosystem exists for retired surfaces | Clean |
| Kover/Sonar | Kover aggregates only `:app` + `:samsung`; Sonar sources/tests list matches reality; `ts-jest-transformer.js` keeps LCOV paths Sonar-consumable | Clean |
| Constraints (Issue #54/#68) | buildscript classpath + per-module `androidLintTool`/test-classpath constraints are consistent with verification metadata and policed by `enforce-gradle-tooling-constraints.mjs` | Clean (macrobenchmark instance removed in Batch 1) |

No generated dependency state was modified during this audit.

---

## 11. Proposed cleanup plan

All batches are **proposals only** — nothing here is executed by this audit. Every batch is independently verifiable and behavior-preserving for production code unless stated. Ownership note: Batch 1 is already the accepted "Next Authorized Action" of PR #118 per `PROJECT_STATE.md`; Batches 2–3 are new small findings this audit adds for the control plane to triage.

### Batch 1 — Retire the `:macrobenchmark` module and its dependency/GMD state (assigned to PR #118)

- **Files affected (delete):** `macrobenchmark/build.gradle.kts`, `macrobenchmark/gradle.lockfile`, `macrobenchmark/src/main/AndroidManifest.xml`, `macrobenchmark/src/main/java/dev/anthracite/appt/macrobenchmark/StartupBenchmark.kt`
- **Files affected (edit):** `settings.gradle.kts` (remove include + fix module-shape comment), `build.gradle.kts` (remove `:macrobenchmark:assembleBenchmark` from `androidBuild`, fix description/comment), `gradle/guards.gradle.kts` (remove `noProductionModuleDependsOnBenchmark` + `appTGuards` entry + header name), `gradle/libs.versions.toml` (remove `androidx-benchmark`, `androidx-test-uiautomator` versions; two library aliases; `android-test` plugin alias), `app/build.gradle.kts` + `samsung/build.gradle.kts` (remove `managedDevices/localDevices` blocks), `gradle/verification-metadata.template.xml` (regeneration command + stale sentence), `.github/workflows/diagnose.yml` (remove the `retired` array together with the lockfile deletion)
- **Generated state to regenerate via the trusted route (`diagnose.yml` `dependency-state` / PR #129 contract):** `gradle/verification-metadata.xml` (prune the 11 macrobenchmark-only components, keep `profileinstaller:1.4.0`), and any lockfile actually changed by the removed configurations
- **Reason:** S05 Contract Revision 5 retired this mechanism; PROJECT_STATE explicitly orders this purge and forbids repairing the harness
- **Expected behavior change:** none for `:app`/`:samsung`/backend; `androidBuild`/`ciCheck`/`ci:android-build` no longer compile the retired module (intended)
- **Tests/checks required:** full terminal `verify` (all domains incl. `dependencyLockCheck`, `appTGuards`, strict dependency verification); `git grep -ri macrobenchmark` returns only intentional history; `git grep pixel2api` empty
- **Risk level:** low–medium (wiring breadth; mitigated by fail-closed lock/verification checks)
- **Dependencies:** none; do not land the diagnose.yml `retired`-array removal separately from the lockfile deletion

### Batch 2 — Documentation drift (retired `device` mode)

- **Files affected:** `docs/BUILD.md` (3 edits: mode list ~L184, label list ~L255, focus example ~L449), `docs/architecture/release.md` (2 edits: ~L153, ~L390)
- **Reason:** executable truth (`diagnose.yml`, `diagnose-focus.mjs`, `agent-control.yml`) no longer has a `device` mode and does have `dependency-state` + backend modes; docs lag after PR #135
- **Expected behavior change:** none (documentation only)
- **Tests/checks required:** markdownlint; `git grep "mode=device\|ci:device"` over docs/ empty; cross-read against `diagnose.yml` mode list
- **Risk level:** low
- **Dependencies:** logically after Batch 1 (same retirement story) but independently mergeable

### Batch 3 — Markdown lint config consolidation

- **Files affected:** `.markdownlint.jsonc` (remove, after editor-behavior confirmation), `.markdownlintignore` (fold contents into `.markdownlint-cli2.jsonc` `ignores`, incl. the `.agents` entry; verify `.project-ai/**` stays ignored)
- **Reason:** D1/D2 — one rule set and one ignore list should have exactly one owner
- **Expected behavior change:** none for CI (repo-quality passes `.markdownlint-cli2.jsonc` explicitly); possible change for local editors that relied on `.markdownlint.jsonc` auto-discovery — verify first
- **Tests/checks required:** `markdownlint-cli2` run over the repo globs; yamllint unaffected; spot-check `.project-ai/**` remains excluded
- **Risk level:** low
- **Dependencies:** none

### Explicitly NOT proposed

- No deletion of `androidTest` suites, fixtures, backend skeleton, control plane, or any documentation.
- No weakening of guards, coverage floors, strict verification, or secret scanning to make cleanup easier.
- No PROJECT_STATE update (that belongs to the accepted-work reconciliation flow, not an audit).

---

## 12. Final disposition matrix

Every tracked file (301) and every directory (149) has exactly one final disposition. Files first (complete enumeration), then directories.

### 12.1 Files

Counts: KEEP 226 · KEEP — CLEANUP 9 · CONSOLIDATE 1 · TRANSITIONAL 1 · GENERATED/DERIVED — KEEP 10 · HISTORICAL/DOCUMENTATION — KEEP 49 · REDUNDANT 1 · OBSOLETE/IRRELEVANT 4 · RELOCATE 0 · DEAD 0 · INVESTIGATE 0 → **301 classified, 0 unclassified**.

| # | Path | Final disposition |
|---|---|---|
| 1 | `.coderabbit.yaml` | KEEP |
| 2 | `.github/actions/assert-dispatch-target/action.yml` | KEEP |
| 3 | `.github/actions/setup-jvm/action.yml` | KEEP |
| 4 | `.github/actions/setup-node/action.yml` | KEEP |
| 5 | `.github/dependabot.yml` | KEEP |
| 6 | `.github/workflows/agent-control.yml` | KEEP |
| 7 | `.github/workflows/codeql.yml` | TRANSITIONAL |
| 8 | `.github/workflows/diagnose.yml` | KEEP |
| 9 | `.github/workflows/maintenance.yml` | KEEP |
| 10 | `.github/workflows/verify.yml` | KEEP |
| 11 | `.gitignore` | KEEP |
| 12 | `.markdownlint-cli2.jsonc` | KEEP |
| 13 | `.markdownlint.jsonc` | REDUNDANT |
| 14 | `.markdownlintignore` | CONSOLIDATE |
| 15 | `.project-ai/PROJECT_STATE.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 16 | `.project-ai/bootstrap/project.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 17 | `.project-ai/execution/arena-dispatch.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 18 | `.project-ai/execution/verification.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 19 | `.project-ai/hosts/chatgpt-project.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 20 | `.project-ai/routing/capabilities.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 21 | `.project-ai/routing/route.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 22 | `.project-ai/skills/SKILL_TEMPLATE.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 23 | `.project-ai/skills/SOURCES.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 24 | `.project-ai/skills/architecture-interface-design/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 25 | `.project-ai/skills/ci-cd-automation/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 26 | `.project-ai/skills/code-review/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 27 | `.project-ai/skills/debugging-recovery/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 28 | `.project-ai/skills/documentation-adrs/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 29 | `.project-ai/skills/implementation-planning/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 30 | `.project-ai/skills/incident-response/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 31 | `.project-ai/skills/incremental-implementation/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 32 | `.project-ai/skills/maintenance-migration-retirement/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 33 | `.project-ai/skills/observability-operations-design/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 34 | `.project-ai/skills/postmortem-learning/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 35 | `.project-ai/skills/pr-integration-correction/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 36 | `.project-ai/skills/project-bootstrap/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 37 | `.project-ai/skills/project-discovery/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 38 | `.project-ai/skills/release-deployment/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 39 | `.project-ai/skills/requirements-specification/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 40 | `.project-ai/skills/research-feasibility/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 41 | `.project-ai/skills/security-engineering/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 42 | `.project-ai/skills/test-driven-development/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 43 | `.project-ai/skills/validate.py` | KEEP |
| 44 | `.project-ai/skills/verification-before-completion/SKILL.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 45 | `.yamllint.yml` | KEEP |
| 46 | `AGENTS.md` | KEEP |
| 47 | `CONTEXT.md` | KEEP |
| 48 | `LICENSE` | KEEP |
| 49 | `README.md` | KEEP |
| 50 | `app/build.gradle.kts` | KEEP — CLEANUP |
| 51 | `app/gradle.lockfile` | GENERATED / DERIVED — KEEP |
| 52 | `app/schemas/dev.anthracite.appt.data.AppTDatabase/1.json` | GENERATED / DERIVED — KEEP |
| 53 | `app/src/androidTest/java/dev/anthracite/appt/DiscoveryWalkthroughTest.kt` | KEEP |
| 54 | `app/src/androidTest/java/dev/anthracite/appt/WelcomeLaunchTest.kt` | KEEP |
| 55 | `app/src/androidTest/java/dev/anthracite/appt/samsung/SamsungKeystoreContractTest.kt` | KEEP |
| 56 | `app/src/main/AndroidManifest.xml` | KEEP |
| 57 | `app/src/main/java/dev/anthracite/appt/AppSettingsLauncher.kt` | KEEP |
| 58 | `app/src/main/java/dev/anthracite/appt/AppTApplication.kt` | KEEP |
| 59 | `app/src/main/java/dev/anthracite/appt/MainActivity.kt` | KEEP |
| 60 | `app/src/main/java/dev/anthracite/appt/data/AppTDatabase.kt` | KEEP |
| 61 | `app/src/main/java/dev/anthracite/appt/data/TvProfile.kt` | KEEP |
| 62 | `app/src/main/java/dev/anthracite/appt/data/TvProfileDao.kt` | KEEP |
| 63 | `app/src/main/java/dev/anthracite/appt/data/TvProfiles.kt` | KEEP |
| 64 | `app/src/main/java/dev/anthracite/appt/discovery/DiscoveryScreen.kt` | KEEP |
| 65 | `app/src/main/java/dev/anthracite/appt/discovery/DiscoveryTestTags.kt` | KEEP |
| 66 | `app/src/main/java/dev/anthracite/appt/discovery/DiscoveryUiState.kt` | KEEP |
| 67 | `app/src/main/java/dev/anthracite/appt/discovery/DiscoveryViewModel.kt` | KEEP |
| 68 | `app/src/main/java/dev/anthracite/appt/discovery/DisplayLabel.kt` | KEEP |
| 69 | `app/src/main/java/dev/anthracite/appt/gate/DiscoveryPermissions.kt` | KEEP |
| 70 | `app/src/main/java/dev/anthracite/appt/gate/LocalNetworkPermissionGate.kt` | KEEP |
| 71 | `app/src/main/java/dev/anthracite/appt/gate/PermissionGate.kt` | KEEP |
| 72 | `app/src/main/java/dev/anthracite/appt/localnetwork/LocalNetworkScreen.kt` | KEEP |
| 73 | `app/src/main/java/dev/anthracite/appt/localnetwork/LocalNetworkTestTags.kt` | KEEP |
| 74 | `app/src/main/java/dev/anthracite/appt/localnetwork/LocalNetworkViewModel.kt` | KEEP |
| 75 | `app/src/main/java/dev/anthracite/appt/navigation/AppTNavGraph.kt` | KEEP |
| 76 | `app/src/main/java/dev/anthracite/appt/navigation/Routes.kt` | KEEP |
| 77 | `app/src/main/java/dev/anthracite/appt/pairing/PairingScreen.kt` | KEEP |
| 78 | `app/src/main/java/dev/anthracite/appt/pairing/PairingTestTags.kt` | KEEP |
| 79 | `app/src/main/java/dev/anthracite/appt/pairing/PairingUiState.kt` | KEEP |
| 80 | `app/src/main/java/dev/anthracite/appt/pairing/PairingViewModel.kt` | KEEP |
| 81 | `app/src/main/java/dev/anthracite/appt/preferences/PreferenceStore.kt` | KEEP |
| 82 | `app/src/main/java/dev/anthracite/appt/remote/ActiveRemoteHost.kt` | KEEP |
| 83 | `app/src/main/java/dev/anthracite/appt/remote/RemoteScreen.kt` | KEEP |
| 84 | `app/src/main/java/dev/anthracite/appt/remote/RemoteTestTags.kt` | KEEP |
| 85 | `app/src/main/java/dev/anthracite/appt/remote/RemoteUiState.kt` | KEEP |
| 86 | `app/src/main/java/dev/anthracite/appt/remote/RemoteViewModel.kt` | KEEP |
| 87 | `app/src/main/java/dev/anthracite/appt/tokens/Theme.kt` | KEEP |
| 88 | `app/src/main/java/dev/anthracite/appt/tokens/Tokens.kt` | KEEP |
| 89 | `app/src/main/java/dev/anthracite/appt/welcome/WelcomeScreen.kt` | KEEP |
| 90 | `app/src/main/java/dev/anthracite/appt/welcome/WelcomeTestTags.kt` | KEEP |
| 91 | `app/src/main/res/drawable/ic_launcher_foreground.xml` | KEEP |
| 92 | `app/src/main/res/drawable/ic_launcher_monochrome.xml` | KEEP |
| 93 | `app/src/main/res/mipmap-anydpi/ic_launcher.xml` | KEEP |
| 94 | `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml` | KEEP |
| 95 | `app/src/main/res/values/colors.xml` | KEEP |
| 96 | `app/src/main/res/values/strings.xml` | KEEP |
| 97 | `app/src/main/res/values/themes.xml` | KEEP |
| 98 | `app/src/main/res/xml/backup_rules.xml` | KEEP |
| 99 | `app/src/main/res/xml/data_extraction_rules.xml` | KEEP |
| 100 | `app/src/test/java/dev/anthracite/appt/ModuleBoundaryTest.kt` | KEEP |
| 101 | `app/src/test/java/dev/anthracite/appt/backup/BackupGuardTest.kt` | KEEP |
| 102 | `app/src/test/java/dev/anthracite/appt/data/FakeTvProfileDao.kt` | KEEP |
| 103 | `app/src/test/java/dev/anthracite/appt/data/TvProfileDaoTest.kt` | KEEP |
| 104 | `app/src/test/java/dev/anthracite/appt/data/TvProfileSchemaTest.kt` | KEEP |
| 105 | `app/src/test/java/dev/anthracite/appt/data/TvProfilesTest.kt` | KEEP |
| 106 | `app/src/test/java/dev/anthracite/appt/discovery/DiscoveryScreenTest.kt` | KEEP |
| 107 | `app/src/test/java/dev/anthracite/appt/discovery/DiscoveryViewModelTest.kt` | KEEP |
| 108 | `app/src/test/java/dev/anthracite/appt/discovery/DisplayLabelTest.kt` | KEEP |
| 109 | `app/src/test/java/dev/anthracite/appt/flow/PairingToFirstControlFlowTest.kt` | KEEP |
| 110 | `app/src/test/java/dev/anthracite/appt/gate/LocalNetworkPermissionGateTest.kt` | KEEP |
| 111 | `app/src/test/java/dev/anthracite/appt/localnetwork/ForbiddenVocabularyTest.kt` | KEEP |
| 112 | `app/src/test/java/dev/anthracite/appt/localnetwork/LocalNetworkScreenTest.kt` | KEEP |
| 113 | `app/src/test/java/dev/anthracite/appt/localnetwork/LocalNetworkViewModelTest.kt` | KEEP |
| 114 | `app/src/test/java/dev/anthracite/appt/navigation/AppTNavGraphTest.kt` | KEEP |
| 115 | `app/src/test/java/dev/anthracite/appt/pairing/PairingScreenTest.kt` | KEEP |
| 116 | `app/src/test/java/dev/anthracite/appt/pairing/PairingViewModelTest.kt` | KEEP |
| 117 | `app/src/test/java/dev/anthracite/appt/preferences/PreferenceStoreTest.kt` | KEEP |
| 118 | `app/src/test/java/dev/anthracite/appt/remote/ActiveRemoteHostTest.kt` | KEEP |
| 119 | `app/src/test/java/dev/anthracite/appt/remote/RemoteScreenTest.kt` | KEEP |
| 120 | `app/src/test/java/dev/anthracite/appt/remote/RemoteViewModelTest.kt` | KEEP |
| 121 | `app/src/test/java/dev/anthracite/appt/savedpairing/SavedPairingUiTest.kt` | KEEP |
| 122 | `app/src/test/java/dev/anthracite/appt/testing/FakePermissionGate.kt` | KEEP |
| 123 | `app/src/test/java/dev/anthracite/appt/testing/FakeSamsungTvs.kt` | KEEP |
| 124 | `app/src/test/java/dev/anthracite/appt/testing/MainDispatcherRule.kt` | KEEP |
| 125 | `app/src/test/java/dev/anthracite/appt/testing/SemanticsAssertions.kt` | KEEP |
| 126 | `app/src/test/java/dev/anthracite/appt/testing/TestFlows.kt` | KEEP |
| 127 | `app/src/test/java/dev/anthracite/appt/testing/TestPreferences.kt` | KEEP |
| 128 | `app/src/test/java/dev/anthracite/appt/testing/TestScopeSettleTest.kt` | KEEP |
| 129 | `app/src/test/java/dev/anthracite/appt/tokens/Contrast.kt` | KEEP |
| 130 | `app/src/test/java/dev/anthracite/appt/tokens/TokensTest.kt` | KEEP |
| 131 | `app/src/test/java/dev/anthracite/appt/welcome/WelcomeScreenTest.kt` | KEEP |
| 132 | `app/src/test/resources/robolectric.properties` | KEEP |
| 133 | `backend/.gitignore` | KEEP |
| 134 | `backend/.prettierignore` | KEEP |
| 135 | `backend/.prettierrc.json` | KEEP |
| 136 | `backend/eslint.config.mjs` | KEEP |
| 137 | `backend/jest.config.js` | KEEP |
| 138 | `backend/package-lock.json` | GENERATED / DERIVED — KEEP |
| 139 | `backend/package.json` | KEEP |
| 140 | `backend/src/index.ts` | KEEP |
| 141 | `backend/src/package-identity.ts` | KEEP |
| 142 | `backend/test/package-identity.test.ts` | KEEP |
| 143 | `backend/ts-jest-transformer.js` | KEEP |
| 144 | `backend/tsconfig.json` | KEEP |
| 145 | `backend/tsconfig.test.json` | KEEP |
| 146 | `build.gradle.kts` | KEEP — CLEANUP |
| 147 | `config/detekt/detekt.yml` | KEEP |
| 148 | `docs/ARCHITECTURE.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 149 | `docs/BUILD.md` | KEEP — CLEANUP |
| 150 | `docs/PRODUCT.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 151 | `docs/architecture/README.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 152 | `docs/architecture/account-entitlement.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 153 | `docs/architecture/commands.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 154 | `docs/architecture/connection.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 155 | `docs/architecture/data.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 156 | `docs/architecture/diagnostics.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 157 | `docs/architecture/discovery.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 158 | `docs/architecture/flows.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 159 | `docs/architecture/lifecycle.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 160 | `docs/architecture/modules.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 161 | `docs/architecture/presentation.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 162 | `docs/architecture/protocol.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 163 | `docs/architecture/release.md` | KEEP — CLEANUP |
| 164 | `docs/architecture/reliability.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 165 | `docs/architecture/samsung-interface.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 166 | `docs/architecture/security.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 167 | `docs/architecture/slices.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 168 | `docs/architecture/testing.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 169 | `docs/architecture/ui-ux.md` | HISTORICAL / DOCUMENTATION — KEEP |
| 170 | `gradle.lockfile` | GENERATED / DERIVED — KEEP |
| 171 | `gradle.properties` | KEEP |
| 172 | `gradle/guards.gradle.kts` | KEEP — CLEANUP |
| 173 | `gradle/libs.versions.toml` | KEEP — CLEANUP |
| 174 | `gradle/verification-metadata.template.xml` | KEEP — CLEANUP |
| 175 | `gradle/verification-metadata.xml` | GENERATED / DERIVED — KEEP |
| 176 | `gradle/wrapper/gradle-wrapper.jar` | GENERATED / DERIVED — KEEP |
| 177 | `gradle/wrapper/gradle-wrapper.properties` | KEEP |
| 178 | `gradlew` | GENERATED / DERIVED — KEEP |
| 179 | `gradlew.bat` | GENERATED / DERIVED — KEEP |
| 180 | `macrobenchmark/build.gradle.kts` | OBSOLETE / IRRELEVANT |
| 181 | `macrobenchmark/gradle.lockfile` | OBSOLETE / IRRELEVANT |
| 182 | `macrobenchmark/src/main/AndroidManifest.xml` | OBSOLETE / IRRELEVANT |
| 183 | `macrobenchmark/src/main/java/dev/anthracite/appt/macrobenchmark/StartupBenchmark.kt` | OBSOLETE / IRRELEVANT |
| 184 | `samsung/README.md` | KEEP |
| 185 | `samsung/build.gradle.kts` | KEEP — CLEANUP |
| 186 | `samsung/gradle.lockfile` | GENERATED / DERIVED — KEEP |
| 187 | `samsung/src/androidTest/java/dev/anthracite/appt/samsung/internal/NameScrubberAndroidTest.kt` | KEEP |
| 188 | `samsung/src/main/AndroidManifest.xml` | KEEP |
| 189 | `samsung/src/main/java/dev/anthracite/appt/samsung/DiscoveryTypes.kt` | KEEP |
| 190 | `samsung/src/main/java/dev/anthracite/appt/samsung/RemoteSession.kt` | KEEP |
| 191 | `samsung/src/main/java/dev/anthracite/appt/samsung/SamsungModule.kt` | KEEP |
| 192 | `samsung/src/main/java/dev/anthracite/appt/samsung/SamsungTvs.kt` | KEEP |
| 193 | `samsung/src/main/java/dev/anthracite/appt/samsung/SessionTypes.kt` | KEEP |
| 194 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/AirPlayTxt.kt` | KEEP |
| 195 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/AndroidDiscoveryTransport.kt` | KEEP |
| 196 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/AndroidKeystoreCipher.kt` | KEEP |
| 197 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/BlockingIo.kt` | KEEP |
| 198 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/BoundedCallback.kt` | KEEP |
| 199 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/BoundedHttp.kt` | KEEP |
| 200 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/ConfirmedTelevision.kt` | KEEP |
| 201 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/DeviceInfo.kt` | KEEP |
| 202 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/DiscoveryScan.kt` | KEEP |
| 203 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/DiscoveryTransport.kt` | KEEP |
| 204 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/KeystoreSamsungStore.kt` | KEEP |
| 205 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/LanPolicy.kt` | KEEP |
| 206 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/LiveSession.kt` | KEEP |
| 207 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/NameScrubber.kt` | KEEP |
| 208 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/NsdAirPlayBrowser.kt` | KEEP |
| 209 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/OkHttpSessionTransport.kt` | KEEP |
| 210 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/PairingSecret.kt` | KEEP |
| 211 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/PlaintextWebSocketTransport.kt` | KEEP |
| 212 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/ProductionSessionTransport.kt` | KEEP |
| 213 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/RemoteChannel.kt` | KEEP |
| 214 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/SamsungDeviceRecord.kt` | KEEP |
| 215 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/SamsungTvsImpl.kt` | KEEP |
| 216 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/SessionGeneration.kt` | KEEP |
| 217 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/SessionTransport.kt` | KEEP |
| 218 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/Ssdp.kt` | KEEP |
| 219 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/SsdpClient.kt` | KEEP |
| 220 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/TvIdentity.kt` | KEEP |
| 221 | `samsung/src/main/java/dev/anthracite/appt/samsung/internal/WebSocketFrames.kt` | KEEP |
| 222 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/AirPlayTxtTest.kt` | KEEP |
| 223 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/BoundedCallbackTest.kt` | KEEP |
| 224 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/BoundedHttpResponseTest.kt` | KEEP |
| 225 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/DeviceInfoParserTest.kt` | KEEP |
| 226 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/FixtureProvenanceTest.kt` | KEEP |
| 227 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/FixtureTransport.kt` | KEEP |
| 228 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/Fixtures.kt` | KEEP |
| 229 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/InMemorySamsungStore.kt` | KEEP |
| 230 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/KeystoreSamsungStoreTest.kt` | KEEP |
| 231 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/LanPolicyTest.kt` | KEEP |
| 232 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/LoopbackSocketsTest.kt` | KEEP |
| 233 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/OkHttpSessionTransportTest.kt` | KEEP |
| 234 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/PlaintextWebSocketTransportTest.kt` | KEEP |
| 235 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/ProductionSessionTransportTest.kt` | KEEP |
| 236 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/RemoteChannelTest.kt` | KEEP |
| 237 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/SamsungModuleGuardTest.kt` | KEEP |
| 238 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/SamsungTvsDiscoveryTest.kt` | KEEP |
| 239 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/SamsungTvsSavedPairingTest.kt` | KEEP |
| 240 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/SamsungTvsSessionTest.kt` | KEEP |
| 241 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/ScriptedSessionTransport.kt` | KEEP |
| 242 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/SessionFixture.kt` | KEEP |
| 243 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/SsdpTest.kt` | KEEP |
| 244 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/TvIdentityTest.kt` | KEEP |
| 245 | `samsung/src/test/java/dev/anthracite/appt/samsung/internal/WebSocketFramesTest.kt` | KEEP |
| 246 | `samsung/src/test/resources/samsung/fixtures/airplay-samsung-tv/provenance.json` | KEEP |
| 247 | `samsung/src/test/resources/samsung/fixtures/airplay-samsung-tv/trace.jsonl` | KEEP |
| 248 | `samsung/src/test/resources/samsung/fixtures/bluray-rcr/provenance.json` | KEEP |
| 249 | `samsung/src/test/resources/samsung/fixtures/bluray-rcr/trace.jsonl` | KEEP |
| 250 | `samsung/src/test/resources/samsung/fixtures/device-info-unreadable/provenance.json` | KEEP |
| 251 | `samsung/src/test/resources/samsung/fixtures/device-info-unreadable/trace.jsonl` | KEEP |
| 252 | `samsung/src/test/resources/samsung/fixtures/dial-filter/provenance.json` | KEEP |
| 253 | `samsung/src/test/resources/samsung/fixtures/dial-filter/trace.jsonl` | KEEP |
| 254 | `samsung/src/test/resources/samsung/fixtures/duplicate-uuid/provenance.json` | KEEP |
| 255 | `samsung/src/test/resources/samsung/fixtures/duplicate-uuid/trace.jsonl` | KEEP |
| 256 | `samsung/src/test/resources/samsung/fixtures/identity-mismatch/provenance.json` | KEEP |
| 257 | `samsung/src/test/resources/samsung/fixtures/identity-mismatch/trace.jsonl` | KEEP |
| 258 | `samsung/src/test/resources/samsung/fixtures/late-tv/provenance.json` | KEEP |
| 259 | `samsung/src/test/resources/samsung/fixtures/late-tv/trace.jsonl` | KEEP |
| 260 | `samsung/src/test/resources/samsung/fixtures/malformed-frame/provenance.json` | KEEP |
| 261 | `samsung/src/test/resources/samsung/fixtures/malformed-frame/trace.jsonl` | KEEP |
| 262 | `samsung/src/test/resources/samsung/fixtures/no-stable-uuid/provenance.json` | KEEP |
| 263 | `samsung/src/test/resources/samsung/fixtures/no-stable-uuid/trace.jsonl` | KEEP |
| 264 | `samsung/src/test/resources/samsung/fixtures/rediscovery-address-change/provenance.json` | KEEP |
| 265 | `samsung/src/test/resources/samsung/fixtures/rediscovery-address-change/trace.jsonl` | KEEP |
| 266 | `samsung/src/test/resources/samsung/fixtures/same-name-two-tvs/provenance.json` | KEEP |
| 267 | `samsung/src/test/resources/samsung/fixtures/same-name-two-tvs/trace.jsonl` | KEEP |
| 268 | `samsung/src/test/resources/samsung/fixtures/soundbar-airplay/provenance.json` | KEEP |
| 269 | `samsung/src/test/resources/samsung/fixtures/soundbar-airplay/trace.jsonl` | KEEP |
| 270 | `samsung/src/test/resources/samsung/fixtures/ssdp-tizen-tv/provenance.json` | KEEP |
| 271 | `samsung/src/test/resources/samsung/fixtures/ssdp-tizen-tv/trace.jsonl` | KEEP |
| 272 | `samsung/src/test/resources/samsung/fixtures/tls-approval-then-volume/provenance.json` | KEEP |
| 273 | `samsung/src/test/resources/samsung/fixtures/tls-approval-then-volume/trace.jsonl` | KEEP |
| 274 | `samsung/src/test/resources/samsung/fixtures/token-resume/provenance.json` | KEEP |
| 275 | `samsung/src/test/resources/samsung/fixtures/token-resume/trace.jsonl` | KEEP |
| 276 | `samsung/src/test/resources/samsung/fixtures/unauthorized-with-token/provenance.json` | KEEP |
| 277 | `samsung/src/test/resources/samsung/fixtures/unauthorized-with-token/trace.jsonl` | KEEP |
| 278 | `samsung/src/test/resources/samsung/fixtures/unsupported-no-keys/provenance.json` | KEEP |
| 279 | `samsung/src/test/resources/samsung/fixtures/unsupported-no-keys/trace.jsonl` | KEEP |
| 280 | `settings-gradle.lockfile` | GENERATED / DERIVED — KEEP |
| 281 | `settings.gradle.kts` | KEEP — CLEANUP |
| 282 | `sonar-project.properties` | KEEP |
| 283 | `tools/ci/diagnose-focus.mjs` | KEEP |
| 284 | `tools/ci/dispatch-workflow.mjs` | KEEP |
| 285 | `tools/ci/project_state_contract.py` | KEEP |
| 286 | `tools/ci/purge-actions.mjs` | KEEP |
| 287 | `tools/ci/test/assert-dispatch-target.test.mjs` | KEEP |
| 288 | `tools/ci/test/diagnose-focus.test.mjs` | KEEP |
| 289 | `tools/ci/test/dispatch-workflow.test.mjs` | KEEP |
| 290 | `tools/ci/test/project-state-contract.test.py` | KEEP |
| 291 | `tools/ci/test/purge-actions.test.mjs` | KEEP |
| 292 | `tools/ci/test/sonar-boundary.test.py` | KEEP |
| 293 | `tools/ci/test/workflow-cache-contract.test.py` | KEEP |
| 294 | `tools/ci/validate-sonar-inputs.py` | KEEP |
| 295 | `tools/secret-scan/secret-scan.mjs` | KEEP |
| 296 | `tools/secret-scan/test/secret-scan.test.mjs` | KEEP |
| 297 | `tools/security/enforce-dependency-policy.mjs` | KEEP |
| 298 | `tools/security/enforce-gradle-tooling-constraints.mjs` | KEEP |
| 299 | `tools/security/run.sh` | KEEP |
| 300 | `tools/security/test/enforce-dependency-policy.test.mjs` | KEEP |
| 301 | `tools/security/test/enforce-gradle-tooling-constraints.test.mjs` | KEEP |

### 12.2 Directories

| Directory (or group) | Final disposition |
|---|---|
| `.` (root) | KEEP |
| `.github`, `.github/workflows`, `.github/actions`, `.github/actions/{assert-dispatch-target,setup-jvm,setup-node}` | KEEP |
| `.project-ai`, `.project-ai/{bootstrap,execution,hosts,routing}` | HISTORICAL / DOCUMENTATION — KEEP (container of live control-plane policy) |
| `.project-ai/skills` (+ 20 per-skill subdirectories) | KEEP for the directory hosting `validate.py`; HISTORICAL / DOCUMENTATION — KEEP for each per-skill dir |
| `app`, `app/src`, `app/src/{main,test,androidTest}` + all Java-package path segments, `app/src/main/res/{drawable,mipmap-anydpi,values,xml}`, `app/src/test/resources` | KEEP |
| `app/schemas`, `app/schemas/dev.anthracite.appt.data.AppTDatabase` | GENERATED / DERIVED — KEEP |
| `backend`, `backend/src`, `backend/test` | KEEP |
| `config`, `config/detekt` | KEEP |
| `docs`, `docs/architecture` | KEEP (contents: HISTORICAL / DOCUMENTATION — KEEP, plus the two KEEP — CLEANUP files) |
| `gradle` | KEEP (contents mixed as per §12.1) |
| `gradle/wrapper` | GENERATED / DERIVED — KEEP |
| `macrobenchmark`, `macrobenchmark/src`, `macrobenchmark/src/main`, `macrobenchmark/src/main/java{,/dev,/dev/anthracite,/dev/anthracite/appt}`, `macrobenchmark/src/main/java/dev/anthracite/appt/macrobenchmark` | OBSOLETE / IRRELEVANT (entire subtree) |
| `samsung`, `samsung/src`, `samsung/src/{main,test,androidTest}` + all package path segments, `samsung/src/test/resources{,/samsung}`, `samsung/src/test/resources/samsung/fixtures` + 17 case dirs | KEEP |
| `tools`, `tools/ci{,/test}`, `tools/secret-scan{,/test}`, `tools/security{,/test}` | KEEP |

---

## 13. Audit method and evidence note

- Revision isolation: all evidence was collected from a single clean checkout at `754f6e23` (= `origin/main`); `git status` clean; no PR-#118 head content inspected or mixed.
- Methods: `git ls-files` inventory; full-file reads of every build file, workflow, composite action, control-plane file, and root config; full reads of all key docs (PROJECT_STATE, BUILD, ARCHITECTURE, modules, testing, reliability, slices, release, diagnostics, commands, README map, PRODUCT skim); per-file header/structure sweep of all 80+ app/samsung Kotlin files; `git grep` usage tracing for every retirement-related keyword (`macrobenchmark`, `baseline`, `benchmark`, `profileinstaller`, `uiautomator`, `managedDevices`, `pixel2api`, `device` mode, `KEY_`, `samsung.internal`, `BenchmarkReady`, `GMD`, `KVM`, `ci:device`, `emulator`, `hosted`), for catalog consumers, and for tool-script CI wiring; lockfile/verification-metadata configuration mapping; GitHub API checks for PR #118/#135/Issue #117 state.
- Facts vs evidence vs uncertainty: findings marked **H** rest on direct file/workflow evidence; **M** (only `.markdownlint.jsonc` removal timing) rests on editor-behavior outside the repository. No uncertain conclusions were forced into a category — nothing required INVESTIGATE.

*End of audit. Question answered: every tracked file and folder in AppT has a documented reason to exist; the only set that should stop existing is the S05-Retired set (O1–O8), whose removal is already the accepted next action on PR #118.*
