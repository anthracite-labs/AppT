// Root build for AppT.
//
// This file owns the repository-level supply-chain and privacy floor that
// docs/architecture/release.md and docs/architecture/diagnostics.md require
// CI to enforce from S01 onward. The individual guards live in
// gradle/guards.gradle.kts so this file stays a readable index of them.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
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

// Regenerates every committed lockfile in one resolution pass. Run as
// `./gradlew resolveAndLockAll --write-locks` after a reviewed dependency
// change (docs/BUILD.md). Without `--write-locks` it is a no-op resolution.
tasks.register("resolveAndLockAll") {
    group = "verification"
    description = "Resolves every locked configuration so --write-locks can refresh the lockfiles."
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
        subprojects.forEach { subproject ->
            subproject.configurations
                .filter { it.isCanBeResolved }
                .forEach { configuration ->
                    configuration.incoming.resolutionResult.root
                }
        }
    }
}
