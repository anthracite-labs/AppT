# Building AppT

## Requirements

- JDK 17
- Android SDK with platform 36 and the build tools AGP 8.9.1 requires
- Node.js 22 (backend package)

## Gradle wrapper

The repository pins the Gradle distribution in
`gradle/wrapper/gradle-wrapper.properties` (Gradle 8.13, an exact version).

The wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) is
**committed**, together with `distributionSha256Sum`, so the toolchain itself is
pinned by checksum. CI does not generate it: `.github/workflows/ci.yml` fails if
it is missing.

These files were produced once by `.github/workflows/supply-chain-bootstrap.yml`
(manual dispatch), which generates the wrapper in an empty scratch build,
records the published distribution checksum, generates the lockfiles and the
SHA-256 verification metadata, proves the result passes strict verification, and
commits it. To regenerate locally with a Gradle 8.13 installation:

```bash
gradle wrapper --gradle-version 8.13 --distribution-type bin
```

## Commands

Android floor, from the repository root:

```bash
./gradlew assembleDebug lintDebug testDebugUnitTest dependencyLockCheck
./gradlew appTGuards
./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency firestore
./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency crashlytics
./gradlew :macrobenchmark:assembleBenchmark
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
  assembleDebug lintDebug testDebugUnitTest dependencyLockCheck \
  :macrobenchmark:assembleBenchmark appTGuards
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
npm run typecheck --prefix backend
npm run lint --prefix backend
npm test --prefix backend
```

## Where the Android verification runs

The implementing sandbox has no JDK, no Android SDK, and no egress to
`dl.google.com`, `repo1.maven.org` or `services.gradle.org`, so Gradle cannot
resolve or run there. GitHub Actions is therefore the authoritative execution
environment for every Android command in this file, and the CI run on a pull
request is the evidence that they pass.

The backend package and the secret scanner do run locally
(`npm ci/typecheck/lint/format:check/test --prefix backend`,
`node --test "tools/secret-scan/test/*.test.mjs"`).

Installing the debug build on a device or emulator is the one S01 criterion
that neither the sandbox nor the current CI job performs.
