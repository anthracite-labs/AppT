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
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.anthracite.appt.macrobenchmark"
    compileSdk = 36

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
