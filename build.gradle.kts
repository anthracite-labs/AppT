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
//
// Issue #68 (Dependabot alerts #15, #17, #58): the same reasoning covers the
// three coordinates that remained. None of them is declared by any AppT module,
// so they appear in no `*.gradle.lockfile` and nowhere in
// `gradle/libs.versions.toml`; they reach the dependency graph only as
// transitives of the plugins this file applies. The buildscript classpath is
// therefore the only seam that owns them, and it is also the only seam
// Dependabot's Gradle updater can read and mutate: its file parser harvests
// literal `group:name:version` declarations, so a coordinate that is not
// written down here is invisible to it and any security update for it dies with
// `dependency_not_found`. A constraint is a floor in Gradle conflict
// resolution, never a downgrade, so these stay correct when a later plugin bump
// moves the same transitive further forward.
//
// Owners, verified against the repository dependency graph:
//   org.bitbucket.b_c:jose4j        <- com.android.tools.build:bundletool:1.18.3
//                                      (AGP 9.4.1); 0.9.5 is vulnerable to
//                                      GHSA-3677-xxcr-wjqv / CVE-2024-29371,
//                                      patched in 0.9.6.
//   org.eclipse.jgit:org.eclipse.jgit <- com.diffplug.spotless:spotless-lib-extra:3.0.2
//                                      (Spotless 7.0.2); 6.10.0.202406032230-r
//                                      is vulnerable to GHSA-vrpq-qp53-qv56 /
//                                      CVE-2025-4949, patched in the 6.10 line
//                                      at 6.10.1.202505221210-r.
//   org.jdom:jdom2                  <-
// com.android.tools.build.jetifier:jetifier-processor:1.0.0-beta10
//                                      (AGP 9.4.1); 2.0.6 is vulnerable to
//                                      GHSA-2363-cqg2-863c / CVE-2021-33813,
//                                      patched in 2.0.6.1.
//
// `tools/security/enforce-gradle-tooling-constraints.mjs` keeps this block and
// the regenerated verification metadata honest.
buildscript {
    repositories { mavenCentral() }
    dependencies {
        constraints {
            classpath("org.bouncycastle:bcprov-jdk18on:1.86")
            classpath("org.bouncycastle:bcpkix-jdk18on:1.86")
            classpath("org.apache.commons:commons-lang3:3.20.0")
            classpath("org.apache.httpcomponents:httpclient:4.5.14")
            classpath("org.bitbucket.b_c:jose4j:0.9.6")
            classpath("org.eclipse.jgit:org.eclipse.jgit:6.10.1.202505221210-r")
            classpath("org.jdom:jdom2:2.0.6.1")
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
                // Reviewed baseline floor: measured 90.58% (125/138 lines) in Kover 0.9.5.
                minBound(80)
            }
        }
    }
}

// Dependency locking, enabled repository-wide (release.md#gradle).
// Lockfiles are committed; CI runs `dependencyLockCheck` in the validating
// mode so a drifted or missing lock state fails the build rather than
// silently re-resolving.
allprojects { dependencyLocking { lockAllConfigurations() } }

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
                .forEach { configuration -> configuration.incoming.resolutionResult.root }
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
        "spotlessCheck",
        ":app:assembleDebug",
        ":app:testDebugUnitTest",
        ":app:lintDebug",
        ":samsung:lintDebug",
        ":app:detekt",
        ":samsung:detekt",
        ":macrobenchmark:assembleBenchmark",
        "appTGuards",
        "dependencyLockCheck",
    )
}

gradle.projectsEvaluated {
    val koverXml =
        tasks.findByName("koverXmlReport")
            ?: project(":app").tasks.findByName("koverXmlReport")
            ?: tasks.findByName("koverXmlReportDebug")
            ?: project(":app").tasks.findByName("koverXmlReportDebug")
    val koverVerify =
        tasks.findByName("koverVerify")
            ?: project(":app").tasks.findByName("koverVerify")
            ?: tasks.findByName("koverVerifyDebug")
            ?: project(":app").tasks.findByName("koverVerifyDebug")

    tasks.named("ciCheck") {
        koverXml?.let { dependsOn(it) }
        koverVerify?.let { dependsOn(it) }
    }
}
