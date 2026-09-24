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
pinned by checksum. CI does not generate it: `.github/workflows/verify.yml`
fails if it is missing.

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

## The AppT verification floors

Three package-/build-owned entry points, each runnable from the repository
root. CI (`.github/workflows/verify.yml`) invokes exactly these; nothing CI
runs is undocumented here, and nothing documented here is CI-only magic.

### Android and Gradle: `ciCheck`

```bash
./gradlew --no-daemon --dependency-verification=strict ciCheck
```

`ciCheck` (registered in the root `build.gradle.kts`) is the whole Android
verification floor as one lifecycle task, in dependency order:

- `assembleDebug` and `testDebugUnitTest` in `:app` and `:samsung` — debug
  assembly plus the JVM/Robolectric test floor;
- `lintDebug` in `:app` and `:samsung` — Android Lint with the repository's
  `warningsAsErrors` configuration;
- `detekt` in `:app` and `:samsung` — against the single reviewed
  `config/detekt/detekt.yml` (production Kotlin scope, no baseline, never
  `--auto-correct`);
- `dependencyLockCheck` — every committed lockfile must match a fresh
  resolution (it also pins `versionCatalogPinned`);
- `appTGuards` — every accepted AppT guard: version-catalog exactness, no
  telemetry/crash-reporting/Firestore artifact, the `:samsung` dependency
  boundary, no production dependency on `:macrobenchmark`, no `Log` in
  `:samsung` source, no sync-record in production source,
  `adIdAbsentFromManifest`, `manifestPermissionAllowlist`;
- `:macrobenchmark:assembleBenchmark` — the benchmark-only module compiles.

Both flags are part of the contract: `--no-daemon` and
`--dependency-verification=strict`.

### Backend: `npm run verify`

```bash
npm ci --prefix backend
npm run verify --prefix backend
```

`verify` is the package-owned interface aggregating, in order: `typecheck`
(tsc over the source and test tsconfigs), `lint` (ESLint), `format:check`
(Prettier), and `test` (Jest). `npm ci` is the only install mode. The backend
has no device/emulator surface in this slice (see `backend/jest.config.js`),
so no emulator step exists in this floor — Firebase emulator tests join the
same `verify` interface when the backend slice makes them real.

### Quality specialists: `tools/quality/run-quality.sh`

```bash
bash tools/quality/run-quality.sh
```

Runs the repository's narrow, non-overlapping quality tools:

- **actionlint 1.7.12** over every workflow in `.github/workflows`;
- **ShellCheck 0.11.0** over every tracked `*.sh` plus `gradlew`;
- **jscpd 5.3.2** — the repository's sole duplication detector,
  reporting-only (`tools/quality/jscpd.json` keeps the threshold at 100 until
  a reviewed baseline establishes a useful regression threshold).

Tool inputs are immutable and recorded in the script header: the two official
release assets are fetched with their pinned sha256 digests (fail closed on
mismatch), and jscpd is installed only by `npm ci --prefix tools/quality` from
the committed lockfile. The script needs network access to GitHub releases and
the npm registry; it runs on Linux x86_64 (the CI runners and ordinary
development hosts).

### Device acceptance: `:app:api29DebugAndroidTest`

```bash
./gradlew --no-daemon --dependency-verification=strict \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  :app:api29DebugAndroidTest
```

The Issue #27 acceptance criterion — the debug APK installs and opens to
Welcome — is evidenced by `WelcomeLaunchTest` on the Gradle Managed Device
`api29` declared in `app/build.gradle.kts` (API 29 is AppT's `minSdk`; AOSP
system image). This needs a KVM-capable Linux host. CI runs the identical
task as the `device` group of `.github/workflows/verify.yml`. Macrobenchmark
*timing* is not collected in pull-request verification at all: emulator
timing is not release performance evidence (release.md).

## Dependency verification

`gradle/verification-metadata.xml` is committed with SHA-256 checksums for
every resolved component, and `ciCheck` (and every other CI Gradle invocation)
runs under `--dependency-verification=strict`. An artifact whose checksum does
not match is rejected rather than used.

`gradle/verification-metadata.template.xml` records the reviewed *policy*
header (verify-metadata on, no trusted-artifact wildcards, and why signature
verification is not yet enabled) separately from the generated checksum body,
so the policy decision stays reviewable without being buried in thousands of
generated lines.

Regenerate the checksums after a reviewed dependency change (`ciCheck` is
exactly the task set CI resolves, so the regenerated metadata covers every
artifact verification will ask for):

```bash
./gradlew --no-daemon --write-verification-metadata sha256 ciCheck
```

Dependency lockfiles are committed. After a deliberate, reviewed dependency
change, refresh them with:

```bash
./gradlew resolveAndLockAll --write-locks
```

## Where verification runs

`.github/workflows/verify.yml` is the single ordinary pull-request workflow
(`docs/architecture/release.md#repository-verification-workflow` owns the
architecture). It runs six groups on **every** pull request — `quality`,
`android`, `backend`, `device`, `dependency-review` and `gate` — with no path
classifier. `verify / gate` is the one branch-protection status: it succeeds
only when every mandatory group succeeded.

The implementing sandbox has no JDK, no Android SDK, no KVM, and no egress to
`dl.google.com`, `repo1.maven.org` or `services.gradle.org`, so Gradle cannot
resolve or run there. GitHub Actions (canonical GitHub-hosted runners) is
therefore the authoritative execution environment for every Android, device
and CodeQL command, and the workflow run on a pull request is the evidence
that they pass.

The backend floor, the secret scanner, and the quality specialists (on a host
that can reach GitHub releases) all run locally:

```bash
npm ci --prefix backend && npm run verify --prefix backend
node --test "tools/secret-scan/test/*.test.mjs"
node tools/secret-scan/secret-scan.mjs [--base <ref>]
bash tools/quality/run-quality.sh
```

## Fixing Android lint

`tools/ci/report-lint.py` mirrors lint findings into GitHub Actions
annotations so a lint failure is readable in the checks UI without
downloading the artifact (it always exits 0; the Gradle task's own exit code
decides the check). To list errors locally:

```bash
./gradlew --no-daemon --dependency-verification=strict lintDebug
grep -r '<severity' --include 'lint-results-*.xml' */build/reports || true
```
