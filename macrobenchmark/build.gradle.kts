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
    alias(libs.plugins.androidx.baselineprofile)
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

    testOptions {
        managedDevices {
            localDevices {
                create("pixel2api29") {
                    device = "Pixel 2"
                    apiLevel = 29
                    systemImageSource = "aosp"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The direction is benchmark -> app, never app -> benchmark.
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        // The Baseline Profile consumer plugin creates :app's profileable
        // benchmarkRelease build type. Prefer it over stock debug so the hosted
        // macrobenchmark runs the deterministic fake variant rather than the real adapter.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("benchmarkRelease", "debug")
        }
    }
}

baselineProfile {
    // The hosted GMD is the producer route for both profile generation and connectedCheck.
    managedDevices += "pixel2api29"
    useConnectedDevices = false
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

dependencies {
    implementation(libs.junit)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    beforeVariants(selector().all()) {
        // Retain the benchmark test variant and expose the release/Baseline Profile producer
        // variants consumed by :app:releaseBaselineProfile. Keep debug producer variants disabled;
        // this remains a test-only module and does not add benchmark code to the shipped app.
        it.enable =
            it.buildType == "benchmark" ||
                it.buildType == "release" ||
                it.buildType.endsWith("Release")
    }
}

// Keep the contract's connectedCheck entry point on the existing GMD execution route: the aggregate
// includes the device task that runs macrobenchmarks against the deterministic benchmark target.
tasks.matching { it.name == "connectedCheck" }.configureEach {
    dependsOn("pixel2api29BenchmarkAndroidTest")
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
    notations.forEach { notation -> project.dependencies.constraints { add(cfg, notation) } }
}
