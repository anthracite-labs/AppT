// :app — the production Android application (docs/architecture/modules.md#shape).
//
// S01 scope: one activity, a Navigation Compose graph containing only
// WelcomeRoute, and the design-token package. No Room, DataStore, Hilt,
// Firebase, billing or networking dependency is declared here: each belongs
// to the slice that first makes it real.

import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.anthracite.appt"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.anthracite.appt"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // S01 introduces no dev/internal/production variants and no signing
        // identities; those are S07's. Only AGP's stock debug/release exist.
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        // No BuildConfig fields are needed and none are generated, so no
        // environment identifier can leak into a build in this slice.
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // The Welcome surface is not yet localized beyond the default locale;
        // translation completeness becomes real when store locales are chosen.
        disable += setOf("MissingTranslation")
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
    // app -> samsung is the only production module edge (modules.md).
    implementation(project(":samsung"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(platform(libs.androidx.compose.bom))

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// ---------------------------------------------------------------------------
// Manifest guards (docs/architecture/release.md#manifest-allowlist)
// ---------------------------------------------------------------------------

// The allowlist is owned verbatim by release.md. It is an allowlist, not an
// instruction to declare these permissions: S01 declares none of them.
val manifestPermissionAllowlist = setOf(
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_WIFI_MULTICAST_STATE",
    "com.android.vending.BILLING",
)

abstract class MergedManifestGuard : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val allowlist: SetProperty<String>

    @get:Input
    abstract val variantName: Property<String>

    @TaskAction
    fun check() {
        val text = mergedManifest.get().asFile.readText()
        val declared = Regex("""<uses-permission[^>]*android:name\s*=\s*"([^"]+)"""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toSortedSet()

        val adIdOffenders = declared.filter { it.endsWith("AD_ID") }
        if (adIdOffenders.isNotEmpty()) {
            throw GradleException(
                "adIdAbsentFromManifest failed for ${variantName.get()}: AD_ID must never appear " +
                    "in the merged manifest (docs/architecture/diagnostics.md).\n" +
                    adIdOffenders.joinToString("\n") { "  $it" },
            )
        }

        val unapproved = declared - allowlist.get()
        if (unapproved.isNotEmpty()) {
            throw GradleException(
                "manifestPermissionAllowlist failed for ${variantName.get()}: the merged manifest " +
                    "declares permissions outside the allowlist owned by " +
                    "docs/architecture/release.md#manifest-allowlist.\n" +
                    unapproved.joinToString("\n") { "  $it" } +
                    "\nAllowlist:\n" + allowlist.get().sorted().joinToString("\n") { "  $it" },
            )
        }

        logger.lifecycle(
            "manifestPermissionAllowlist + adIdAbsentFromManifest: OK for ${variantName.get()} " +
                "(declared permissions: ${if (declared.isEmpty()) "none" else declared.joinToString(", ")}).",
        )
    }
}

androidComponents.onVariants { variant ->
    val capitalized = variant.name.replaceFirstChar { it.titlecase(Locale.ROOT) }
    val guard = tasks.register<MergedManifestGuard>("checkMergedManifest$capitalized") {
        group = "verification"
        description = "Checks the merged $capitalized manifest against the release.md permission allowlist."
        mergedManifest.set(
            variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST),
        )
        allowlist.set(manifestPermissionAllowlist)
        variantName.set(variant.name)
    }
    // Manifest guards are part of assembling, so a local build cannot produce
    // an APK whose permissions were never checked.
    //
    // `tasks.matching { ... }.configureEach` rather than `tasks.named(...)`:
    // this callback runs while AGP is still configuring variants, and the
    // variant lifecycle tasks (`assembleDebug`, `assembleRelease`) do not exist
    // yet. `named` resolved them eagerly and failed configuration with
    // "Task with name 'assembleDebug' not found in project ':app'"; `matching`
    // is lazy and wires the dependency when AGP creates the task.
    tasks.matching { it.name == "assemble$capitalized" }.configureEach {
        dependsOn(guard)
    }
}

// Accepted names, aggregating every variant's merged manifest.
tasks.register("adIdAbsentFromManifest") {
    group = "verification"
    description = "Fails if AD_ID appears in any merged manifest."
    dependsOn(tasks.withType<MergedManifestGuard>())
}

tasks.register("manifestPermissionAllowlist") {
    group = "verification"
    description = "Fails if any merged manifest declares a permission outside the release.md allowlist."
    dependsOn(tasks.withType<MergedManifestGuard>())
}
