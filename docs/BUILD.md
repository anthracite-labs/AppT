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

Android/Kotlin verification interface, from the repository root:

```bash
# Root Android/Kotlin verification lifecycle interface aggregating strict
# compiler diagnostics (-Werror), Spotless + ktfmt, debug assembly, unit/Robolectric
# tests, Android Lint, detekt, Kover coverage verification, dependency locks,
# appTGuards, and macrobenchmark compilation:
./gradlew --no-daemon --dependency-verification=strict ciCheck

# Spotless formatting:
./gradlew spotlessApply
./gradlew spotlessCheck

# Installed-app runtime acceptance on Gradle Managed Device (API 29):
./gradlew --no-daemon --dependency-verification=strict \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  :app:pixel2api29DebugAndroidTest
```

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

Backend, always package-prefixed from the repository root
(docs/architecture/release.md):

```bash
npm ci --prefix backend
# Backend verification interface aggregating typecheck, typed ESLint,
# Prettier, Knip dead-code/export/dependency analysis, Jest, and LCOV coverage:
npm run verify --prefix backend
```

## Where the Android verification runs

The implementing sandbox has no JDK, no Android SDK, and no egress to
`dl.google.com`, `repo1.maven.org` or `services.gradle.org`, so Gradle cannot
resolve or run there. GitHub Actions is therefore the authoritative execution
environment for every Android command in this file, and the CI run on a pull
request (`.github/workflows/verify.yml` with required status `verify / gate`) is
the evidence that they pass.

The backend package, the secret scanner, yamllint, markdownlint, and ShellCheck
run locally:
- `npm ci/run verify --prefix backend`
- `node --test "tools/secret-scan/test/secret-scan.test.mjs" && node tools/secret-scan/secret-scan.mjs`
- `yamllint -c .yamllint.yml .`
- `tools/security/run.sh`

Installed-app acceptance executes on GitHub Actions via Gradle Managed Devices
(API 29) with KVM acceleration.
