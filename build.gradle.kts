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
// so they appear nowhere in `gradle/libs.versions.toml`; they reach the
// dependency graph only as
// transitives of the plugins this file applies. The buildscript classpath is
// therefore the only seam that owns them, and it is also the only seam
// Dependabot's Gradle updater can read and mutate: its file parser harvests
// literal `group:name:version` declarations, so a coordinate that is not
// written down here is invisible to it and any security update for it dies with
// `dependency_not_found`. A constraint is a floor in Gradle conflict
// resolution, never a downgrade, so these stay correct when a later plugin bump
// moves the same transitive further forward.
//
// Owners, read off the repository dependency graph, which records exactly these
// DEPENDS_ON edges:
//   jose4j      <- com.android.tools.build:bundletool:1.18.3 (AGP 9.4.1)
//                  0.9.5 -> GHSA-3677-xxcr-wjqv / CVE-2024-29371, fixed in 0.9.6
//   jgit        <- com.diffplug.spotless:spotless-lib-extra:3.0.2 (Spotless 7.0.2)
//                  6.10.0.202406032230-r -> GHSA-vrpq-qp53-qv56 / CVE-2025-4949,
//                  fixed in the 6.10 line at 6.10.1.202505221210-r
//   jdom2       <- jetifier-processor:1.0.0-beta10 (AGP 9.4.1), that is
//                  com.android.tools.build.jetifier:jetifier-processor
//                  2.0.6 -> GHSA-2363-cqg2-863c / CVE-2021-33813, fixed in 2.0.6.1
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

apply(from = rootProject.file("gradle/guards.gradle.kts"))

// Dependency integrity is owned by ONE committed artifact:
// gradle/verification-metadata.xml under --dependency-verification=strict.
// Every declared version is exact (version catalog + `versionCatalogPinned`
// guard), so Gradle dependency locking had no dynamic versions to stabilize
// and was retired as a redundant second integrity layer.

// ---------------------------------------------------------------------------
// CI-owned Android failure domains (Issue #88)
// ---------------------------------------------------------------------------
// `verify.yml` runs each of these as an independent parallel job, so one
// failing domain cannot hide the evidence of another: formatting, static
// analysis, the build, and unit/Robolectric tests each report on their own and
// the `verify / gate` job alone decides acceptance. They are aggregating
// lifecycle tasks — the work itself stays owned by the module tasks, exactly as
// `ciCheck` owned it before — and they exist so the failure domains visible in
// CI are named here rather than only in workflow YAML.
//
// Spotless + ktfmt is ONE formatting responsibility, not two checks: it is
// exposed as the single `androidFormat` domain and is never split into a
// separate ktfmt job.
tasks.register("androidFormat") {
    group = "verification"
    description = "Android formatting responsibility: deterministic Spotless + ktfmt checking."
    dependsOn("spotlessCheck")
}

tasks.register("androidStatic") {
    group = "verification"
    description = "Android static and policy verification: Android Lint, detekt, appTGuards."
    dependsOn(
        ":app:lintDebug",
        ":samsung:lintDebug",
        ":app:detekt",
        ":samsung:detekt",
        "appTGuards",
    )
}

tasks.register("androidBuild") {
    group = "verification"
    description =
        "Android build: debug assembly (which runs the merged-manifest guards) and macrobenchmark compilation."
    dependsOn(":app:assembleDebug", ":macrobenchmark:assembleBenchmark")
}

// Samsung unit tests are authoritative verification evidence, not diagnostics:
// they are part of the same failure domain as :app's unit/Robolectric tests so
// a full verification run always produces both.
tasks.register("androidUnit") {
    group = "verification"
    description =
        "Android unit/Robolectric verification for :app and :samsung, plus Kover XML coverage reporting."
    dependsOn(":app:testDebugUnitTest", ":samsung:test")
}

// ---------------------------------------------------------------------------
// ciCheck — local umbrella over every Android failure domain (Issue #56)
// ---------------------------------------------------------------------------
// Preserved as the convenient local aggregate command. CI invokes the narrower
// domains above instead, because one monolithic invocation cannot report a
// formatting failure and a unit-test failure independently.
tasks.register("ciCheck") {
    group = "verification"
    description =
        "Root Android/Kotlin verification interface aggregating the complete verification floor."
    dependsOn("androidFormat", "androidStatic", "androidBuild", "androidUnit")
}

gradle.projectsEvaluated {
    val koverXml =
        tasks.findByName("koverXmlReport")
            ?: project(":app").tasks.findByName("koverXmlReport")
            ?: tasks.findByName("koverXmlReportDebug")
            ?: project(":app").tasks.findByName("koverXmlReportDebug")

    // Product coverage is useful input to the Sonar new-code quality gate.
    // There is deliberately no percentage floor on build/CI implementation.
    tasks.named("androidUnit") {
        koverXml?.let { dependsOn(it) }
    }
}
