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
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktfmt().kotlinlangStyle()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktfmt().kotlinlangStyle()
    }
}

dependencies {
    kover(project(":app"))
    kover(project(":samsung"))
}

kover {
    reports {
        verify {
            rule {
                // Reviewed baseline floor from current main.
                minBound(50)
            }
        }
    }
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

// ---------------------------------------------------------------------------
// ciCheck — root Android/Kotlin verification lifecycle task (Issue #56)
// ---------------------------------------------------------------------------
tasks.register("ciCheck") {
    group = "verification"
    description =
        "Root Android/Kotlin verification interface aggregating the complete verification floor."
    dependsOn(
        tasks.named("spotlessCheck"),
        tasks.named("assembleDebug"),
        tasks.named("testDebugUnitTest"),
        tasks.named("lintDebug"),
        tasks.named("detekt"),
        tasks.named("dependencyLockCheck"),
        tasks.named("appTGuards"),
        project(":macrobenchmark").tasks.named("assembleBenchmark"),
    )
}

gradle.projectsEvaluated {
    val koverXml = tasks.findByName("koverXmlReport")
        ?: project(":app").tasks.findByName("koverXmlReport")
        ?: tasks.findByName("koverXmlReportDebug")
        ?: project(":app").tasks.findByName("koverXmlReportDebug")
    val koverVerify = tasks.findByName("koverVerify")
        ?: project(":app").tasks.findByName("koverVerify")
        ?: tasks.findByName("koverVerifyDebug")
        ?: project(":app").tasks.findByName("koverVerifyDebug")

    tasks.named("ciCheck") {
        koverXml?.let { dependsOn(it) }
        koverVerify?.let { dependsOn(it) }
    }
}

