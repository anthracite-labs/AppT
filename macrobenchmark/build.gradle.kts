// :macrobenchmark — test-only com.android.test module targeting :app
// (docs/architecture/modules.md#shape).
//
// Why it exists although the production shape is two modules: Android's
// Macrobenchmark API must run from a separate `com.android.test` module that
// targets the app under test, so it cannot live inside :app or :samsung.
//
// A `com.android.test` module produces no publishable artifact and is never a
// dependency of an application or library module, which is what keeps benchmark
// code out of every shipped artifact. `noProductionModuleDependsOnBenchmark`
// asserts both halves of that.

plugins {
    alias(libs.plugins.android.test)
    // No kotlin-android plugin: AGP 9's built-in Kotlin compiles this
    // module's Kotlin sources (docs/BUILD.md, Issue #54).
}

android {
    namespace = "dev.anthracite.appt.macrobenchmark"
    // Matches :app's compileSdk (Issue #54 toolchain bump); minSdk stays 29.
    compileSdk = 37

    defaultConfig {
        // Macrobenchmark requires API 29+, which matches the AppT baseline.
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The direction is benchmark -> app, never app -> benchmark.
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        // S01 introduces no build variants in :app beyond AGP's stock
        // debug/release and no signing identities (those are S07's), so the
        // benchmark variant resolves :app's debug variant. Measuring a
        // profileable non-debuggable build is a reliability concern that lands
        // with the harness in S15, not a skeleton concern.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("debug")
        }
    }
}

// Project-level Kotlin configuration. `jvmTarget` must match the Java
// `compileOptions` above, or AGP fails the build on mismatched bytecode.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.junit)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    beforeVariants(selector().all()) {
        // Only the benchmark variant is enabled, so no benchmark code is built
        // into a debug or release pipeline.
        it.enable = it.buildType == "benchmark"
    }
}

// Issue #54 — narrow security constraints (never resolutionStrategy.force),
// applied only on the configurations that resolve each family:
//   * androidLintTool — AGP 9.4.1's lint tooling classpath; same
//     advisory-fixed set as :app (see app/build.gradle.kts comment block):
//     bcprov/bcpkix 1.86, commons-lang3 3.20.0, httpclient 4.5.14.
//   * wire-runtime 6.4.7 — androidx.benchmark (latest 1.5.0) transitively
//     resolves Wire 6.4.0, which carries GHSA-9rm7-3qhh-h2mc (patched in
//     6.4.5); 6.4.7 is the latest stable 6.x. `implementationDependenciesMetadata`
//     resolves the same family outside the variant classpaths, so it is
//     constrained to keep a single Wire version across the module.
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
            cfg.contains("benchmark") || cfg == "implementationDependenciesMetadata" ->
                listOf("com.squareup.wire:wire-runtime:6.4.7")
            else -> emptyList<String>()
        }
    notations.forEach { notation ->
        project.dependencies.constraints {
            add(cfg, notation)
        }
    }
}
