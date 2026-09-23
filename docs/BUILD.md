# Building AppT

## Requirements

- JDK 17
- Android SDK with platform 36 and the build tools AGP 8.9.1 requires
- Node.js 22 (backend package)

## Gradle wrapper

The repository pins the Gradle distribution in
`gradle/wrapper/gradle-wrapper.properties` (Gradle 8.13, an exact version).

The binary `gradle-wrapper.jar` and the `gradlew` / `gradlew.bat` scripts are
**generated**, not hand-written. In an environment with access to
`services.gradle.org`, materialize them once with a Gradle 8.13 installation:

```bash
gradle wrapper --gradle-version 8.13 --distribution-type bin
```

Then record the distribution checksum in `gradle-wrapper.properties`
(`distributionSha256Sum`) from the checksum Gradle publishes beside the
distribution, and commit all four files together. CI generates the wrapper the
same way (see `.github/workflows/ci.yml`), so every documented `./gradlew`
command is exactly what CI runs.

## Commands

Android floor, from the repository root:

```bash
./gradlew assembleDebug lintDebug testDebugUnitTest dependencyLockCheck
./gradlew appTGuards
./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency firestore
./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency crashlytics
./gradlew :macrobenchmark:assembleBenchmark
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
