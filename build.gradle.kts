// Root build for AppT.
//
// This file owns the repository-level supply-chain and privacy floor that
// docs/architecture/release.md and docs/architecture/diagnostics.md require
// CI to enforce from S01 onward. The individual guards live in
// gradle/guards.gradle.kts so this file stays a readable index of them.

// Issue #54 final security closure: AGP 9.4.1 is the newest stable plugin,
// but its plugin/buildscript classpath still declares older vulnerable
// transitives. Gradle's dependency-submission guidance explicitly supports
// constraining plugin-classpath transitives through the buildscript classpath
// when the owning plugin cannot be upgraded further. These are constraints,
// never resolutionStrategy.force, and match the patched versions already
// proven on AppT's project graphs.
buildscript {
    dependencies {
        constraints {
            classpath("org.bouncycastle:bcprov-jdk18on:1.86")
            classpath("org.bouncycastle:bcpkix-jdk18on:1.86")
            classpath("org.apache.commons:commons-lang3:3.20.0")
            classpath("org.apache.httpcomponents:httpclient:4.5.14")
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    // No kotlin-android plugin: AGP 9's built-in Kotlin compiles Kotlin in
    // every module that applies AGP (docs/BUILD.md, Issue #54).
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // Declared at the root so the version comes from the catalog exactly once,
    // and applied only in the production Kotlin modules (:app, :samsung).
    // :macrobenchmark is a test-only com.android.test module, not production
    // Kotlin, so Issue #36's detekt scope does not reach it.
    alias(libs.plugins.detekt) apply false
}

// Dependency locking, enabled repository-wide (release.md#gradle).
// Lockfiles are committed; CI runs `dependencyLockCheck` in the validating
// mode so a drifted or missing lock state fails the build rather than
// silently re-resolving.
allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

apply(from = rootProject.file("gradle/guards.gradle.kts"))

// ---------------------------------------------------------------------------
// ciCheck — the AppT verification floor as ONE root entry point (Issue #56)
// ---------------------------------------------------------------------------
//
// docs/architecture/release.md#repository-verification-workflow owns the
// architecture: Gradle owns Android verification behind a single lifecycle
// task that both CI (`.github/workflows/verify.yml`, the `android` group) and
// local strict runs invoke identically:
//
//   ./gradlew --no-daemon --dependency-verification=strict ciCheck
//
// Coverage, exactly the task set the former CI task-name selectors produced
// (paths are explicit because a root `dependsOn("name")` resolves against the
// root project only, while `gradlew <name>` matched every subproject):
//
//   * assembleDebug and testDebugUnitTest in :app and :samsung — assembly
//     plus the JVM/Robolectric test floor;
//   * lintDebug in :app and :samsung — Android Lint with the repository's
//     `warningsAsErrors` configuration;
//   * detekt in :app and :samsung — the single reviewed config/detekt/detekt.yml
//     (no baseline, never `--auto-correct`); this replaces the former
//     standalone detekt job, so a Kotlin finding still fails CI, it just does
//     so from inside the one Android leg;
//   * dependencyLockCheck — the root aggregate over every committed lockfile
//     (it itself pins versionCatalogPinned);
//   * appTGuards — every accepted guard: version-catalog pinning, no
//     telemetry/crash-reporting/Firestore artifact, the :samsung dependency
//     boundary, no production dependency on :macrobenchmark, no Log in
//     :samsung, no sync-record in production source, adIdAbsentFromManifest and
//     manifestPermissionAllowlist. The guards that read the resolved graph
//     (noFirestoreClientInApp, noCrashReportingInApp) are the durable form of
//     the former workflow's two one-off `dependencyInsight` greps.
//   * :macrobenchmark:assembleBenchmark — the benchmark-only module compiles
//     on every verification run (emulator timing is NOT collected anywhere in
//     the PR workflow; see release.md).
//
// Both flags on the command line are part of the contract: `--no-daemon` and
// `--dependency-verification=strict`. Nothing in ciCheck is
// `continue-on-error`, and nothing here regenerates a supply-chain artifact.
tasks.register("ciCheck") {
    group = "verification"
    description =
        "The AppT verification floor: assembly, JVM tests, Lint, detekt, lock check, appTGuards and macrobenchmark compile."
    dependsOn(
        ":app:assembleDebug",
        ":samsung:assembleDebug",
        ":app:testDebugUnitTest",
        ":samsung:testDebugUnitTest",
        ":app:lintDebug",
        ":samsung:lintDebug",
        ":app:detekt",
        ":samsung:detekt",
        "dependencyLockCheck",
        "appTGuards",
        ":macrobenchmark:assembleBenchmark",
    )
}

// Regenerates every committed lockfile in one resolution pass. Run as
// `./gradlew resolveAndLockAll --write-locks` after a reviewed dependency
// change (docs/BUILD.md). Without `--write-locks` it is a no-op resolution.
//
// Gradle 9 (Issue #54): a task may only resolve configurations owned by its
// own project, so this task is registered per subproject and the bare command
// name runs every instance — same coverage the single root task had, without
// the cross-project resolution Gradle 9 rejects.
subprojects {
    tasks.register("resolveAndLockAll") {
        group = "verification"
        description =
            "Resolves this module's locked configurations so --write-locks can refresh the lockfiles."
        notCompatibleWithConfigurationCache("Writes lockfiles during resolution")
        doFirst {
            require(gradle.startParameter.isWriteDependencyLocks) {
                "resolveAndLockAll must be run with --write-locks"
            }
        }
        doLast {
            // Resolve the dependency GRAPH, not the artifact files. Dependency
            // locking records the resolved graph, and `resolve()`/`files` additionally
            // forces artifact selection, which is ambiguous for configurations such
            // as `:app:debugAndroidTestCompileClasspath` that see several variants of
            // `:app` itself and would need an `artifactType` to disambiguate. Graph
            // resolution is exactly what locking needs and is what AGP supports here.
            configurations
                .filter { it.isCanBeResolved }
                .forEach { configuration ->
                    configuration.incoming.resolutionResult.root
                }
        }
    }
}
