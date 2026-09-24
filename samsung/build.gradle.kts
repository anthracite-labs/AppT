// :samsung — the production Samsung control module
// (docs/architecture/modules.md#samsung-control-samsung).
//
// S01 establishes the module and its dependency boundary only. Discovery,
// pairing, the session, the protocol, secret storage and wake are S02–S04.
//
// Forbidden here, now and later, enforced by `samsungDependencyBoundary`:
// Firebase, Play services, Play Billing, Play Integrity, any telemetry SDK,
// Room, DataStore, WorkManager, licensing, and :app.

plugins {
    alias(libs.plugins.android.library)
    // No kotlin-android plugin: AGP 9's built-in Kotlin compiles this
    // module's Kotlin sources (docs/BUILD.md, Issue #54).
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

android {
    namespace = "dev.anthracite.appt.samsung"
    // Matches :app's compileSdk (Issue #54 toolchain bump); minSdk stays 29.
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true

        // Dependency-currency advisories are informational here, not failures.
        // docs/architecture/release.md#gradle: "Updates arrive as reviewed pull
        // requests. Silent upgrades are not allowed." A lint check that fails
        // the build the moment upstream publishes a release would either force
        // an unreviewed bump or block every unrelated change, which is the
        // opposite of that rule. Version currency is handled by reviewed
        // dependency-update pull requests; the pins themselves are still
        // enforced exactly by `versionCatalogPinned`.
        informational += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
        )
    }
}

// Project-level Kotlin configuration. `jvmTarget` must match the Java
// `compileOptions` above, or AGP fails the build on mismatched bytecode.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
        extraWarnings.set(true)
    }
}

// ---------------------------------------------------------------------------
// detekt — the Kotlin static-analysis floor (Issue #36)
// ---------------------------------------------------------------------------
//
// :samsung is a production module, so it is in detekt's scope even though the
// S01 skeleton has no Kotlin source in it yet: discovery, pairing, the session
// and the protocol arrive in S02–S04 and must land under the same rules from
// their first commit rather than being retrofitted.
//
// Identical to :app on purpose. See config/detekt/detekt.yml for the scope and
// the reason there is no baseline.
detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    autoCorrect = false
    source.setFrom("src/main/java", "src/main/kotlin")
}

dependencies {
    // Nothing beyond the Android framework and the Kotlin stdlib is needed by
    // the S01 skeleton. OkHttp and kotlinx-serialization arrive with the
    // session and protocol work that uses them.
    testImplementation(libs.junit)
}

// Issue #54 — same narrow lint-classpath security constraints as :app; see
// the comment block in app/build.gradle.kts for the advisory-by-advisory
// rationale. :samsung has no unit-test Bouncy Castle edge of its own, so only
// `androidLintTool` is constrained here.
configurations.configureEach {
    val cfg = name
    val notations =
        when {
            cfg == "androidLintTool" ->
                listOf(
                    "org.bouncycastle:bcprov-jdk18on:1.86",
                    "org.bouncycastle:bcpkix-jdk18on:1.86",
                    "org.apache.commons:commons-lang3:3.20.0",
                    "org.apache.httpcomponents:httpclient:4.5.14",
                )
            else -> emptyList<String>()
        }
    notations.forEach { notation ->
        project.dependencies.constraints {
            add(cfg, notation)
        }
    }
}
