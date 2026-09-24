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
# Backend verification interface aggregating typecheck, typed ESLint,
# Prettier, Knip dead-code/export/dependency analysis, Jest, and LCOV coverage:
npm run verify --prefix backend
```

## Where the Android verification runs

The implementing sandbox has no JDK, no Android SDK, and no egress to
`dl.google.com`, `repo1.maven.org` or `services.gradle.org`, so Gradle cannot
resolve or run there. GitHub Actions is therefore the authoritative execution
environment for every Android command in this file. During active implementation,
the human-triggered full `.github/workflows/verify.yml` run is the evidence that
the long Android verification passes; ordinary pull-request synchronization runs
only the quick check.

The backend package, the secret scanner, yamllint, markdownlint, and ShellCheck
run locally:

- `npm ci --prefix backend`
- `npm run verify --prefix backend`
- `node --test "tools/secret-scan/test/secret-scan.test.mjs" && node tools/secret-scan/secret-scan.mjs`
- `yamllint -c .yamllint.yml .`
- `tools/security/run.sh`

Installed-app acceptance executes on GitHub Actions via Gradle Managed Devices
(API 29) with KVM acceleration.

## Verification cadence

Pull-request synchronization is intentionally cheap. `.github/workflows/verify.yml`
runs only the fast repository checks on ordinary PR updates: whitespace/diff
validation, repository security-policy self-tests, secret scanning, and tooling
constraint checks. It does not build Android, run backend verification, start a
managed device, run SonarQube, regenerate dependency state, or execute `ciCheck`.

The human decides when to pay for full verification during implementation. Use
the `verify` workflow's **Run workflow** action on the branch that needs the full
suite. Pushes to `main` continue to run the full suite automatically.

Do not regenerate Gradle locks or verification metadata as an iteration step.
Regenerate them only after an actual reviewed dependency change requires it.
## CodeQL static analysis

CodeQL static analysis keeps ordinary pull-request synchronization build-free:
PR updates scan GitHub Actions and JavaScript/TypeScript only. Java/Kotlin CodeQL
runs when explicitly dispatched, on pushes to `main`, and on the scheduled
security run. The CI-policy migration PR used one temporary minimal Kotlin
compile solely to satisfy the pre-existing code-scanning merge rule while this
policy was being introduced.

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
