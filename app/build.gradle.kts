// :app — the production Android application (docs/architecture/modules.md#shape).
//
// S01 scope: one activity, a Navigation Compose graph, and the design-token
// package. S02 (Issue #73) adds the local-network explanation, the permission
// gate and Discovery. S03 (Issue #79) adds the device-local Room row for a
// selected television, the typed DataStore preference keys, the application-
// scoped ActiveRemoteHost, and the Pairing and Remote surfaces.
//
// No Hilt, Firebase, billing, WorkManager or networking library is declared
// here: the account, entitlement and deferred-work slices own those, and all
// network traffic lives in :samsung (modules.md).
//
// Room is processed by KSP: AGP 9's built-in Kotlin has no kapt support, so
// KSP is the only annotation-processing path available (docs/BUILD.md).

import java.util.Locale
import java.util.zip.ZipFile
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskState
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.android.application)
    // No kotlin-android plugin: AGP 9's built-in Kotlin compiles this
    // module's Kotlin sources (docs/BUILD.md, Issue #54).
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// TEMPORARY diagnostic instrumentation for the failing test compilation.
// Surface the compiler task's exception chain as check annotations; remove
// this block after the warning has been diagnosed.
val temporaryCompilerFailureDetails = mutableListOf<String>()
@Suppress("DEPRECATION")
gradle.taskGraph.addTaskExecutionListener(
    object : TaskExecutionListener {
        override fun beforeExecute(task: Task) = Unit

        override fun afterExecute(task: Task, state: TaskState) {
            if (task.path == ":app:compileDebugUnitTestKotlin") {
                var failure = state.failure
                var depth = 0
                while (failure != null && depth < 30) {
                    temporaryCompilerFailureDetails +=
                        "${failure.javaClass.name}: ${failure.message ?: "(no message)"}"
                    failure.suppressed.forEach { suppressed ->
                        temporaryCompilerFailureDetails +=
                            "${suppressed.javaClass.name}: ${suppressed.message ?: "(no message)"}"
                    }
                    failure = failure.cause
                    depth++
                }
            }
        }
    }
)
gradle.buildFinished {
    val details = temporaryCompilerFailureDetails.take(80)
    println("::notice title=Temporary Kotlin diagnostic::Captured ${details.size} exception details.")
    details.forEach { detail ->
        val safeDetail =
            detail.take(1500)
                .replace("%", "%25")
                .replace("\r", "%0D")
                .replace("\n", "%0A")
        println("::error title=Temporary Kotlin exception detail::$safeDetail")
    }
}

// The trusted hosted android-build route sets this from the dispatch-target assertion output.
// Local builds use an explicit sentinel; the physical verifier refuses to produce a report without
// a full source SHA.
val apptBuildSha =
    providers.environmentVariable("APPT_BUILD_SHA")
        .orElse(providers.gradleProperty("apptBuildSha"))
        .getOrElse("local")
require(apptBuildSha == "local" || apptBuildSha.matches(Regex("[0-9a-f]{40}"))) {
    "APPT_BUILD_SHA must be a full lowercase 40-character Git SHA or unset for a local build."
}

android {
    namespace = "dev.anthracite.appt"
    // compileSdk tracks the toolchain (Issue #54): the current AndroidX and
    // Compose releases require compileSdk 37. targetSdk deliberately stays at
    // 36 — raising the target opts into new runtime behavior and is gated on
    // the architecture map (docs/architecture/discovery.md), not on this
    // supply-chain modernization.
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.anthracite.appt"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "APPT_BUILD_SHA", "\"$apptBuildSha\"")
    }

    buildTypes {
        // S01 introduces no dev/internal/production variants and no signing
        // identities; those are S07's. Only AGP's stock debug/release exist.
        release { isMinifyEnabled = false }
    }

    buildFeatures {
        compose = true
        // The debug-only physical verifier shows the exact SHA injected by the trusted build route.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // Compose tests run on Robolectric so the accessibility assertions
            // execute on every CI run rather than only when a device is
            // attached, and that needs real Android resources.
            // Robolectric's native graphics mode (required for Compose to lay
            // out and measure the nodes the touch-target assertions read) is
            // set in app/src/test/resources/robolectric.properties.
            isIncludeAndroidResources = true
        }
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
        informational +=
            setOf(
                "AndroidGradlePluginVersion",
                "GradleDependency",
                "NewerVersionAvailable",
                // Issue #54: compileSdk now tracks the toolchain (37) while
                // targetSdk deliberately stays at 36 — bumping the target opts
                // into new runtime behavior and is gated on
                // docs/architecture/discovery.md (a target-37 bump must adopt
                // ACCESS_LOCAL_NETWORK through that gate first). OldTargetApi
                // would fail the build for NOT making that unreviewed bump, so
                // like the currency checks above it stays informational: the
                // signal remains visible, the architecture gate stays intact.
                "OldTargetApi",
            )
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
        allWarningsAsErrors.set(true)
        extraWarnings.set(true)
    }
}

// ---------------------------------------------------------------------------
// Room - the device-local application store (data.md#room)
// ---------------------------------------------------------------------------
//
// `appt.db`, version 1, no destructive fallback. The exported schema is
// committed so `schemaContainsNoForbiddenColumn` and every later migration test
// can read it; any build that compiles the Room entities regenerates it.
//
// Room's KSP processor emits Kotlin unless it is told otherwise here. That codegen writes an
// explicit `public` on every declaration it emits and leaves `var` properties unwritten, which
// `extraWarnings` above reports and `allWarningsAsErrors` turns into a build failure on code
// nobody wrote - and a generated file cannot carry a file-level `@Suppress`. Java codegen keeps
// the generated layer out of the Kotlin warning surface entirely. The exported schema and the DAO
// contract are identical either way; only the language of `*_Impl` differs.
ksp {
    arg("room.schemaLocation", "${projectDir}/schemas")
    arg("room.generateKotlin", "false")
}

// ---------------------------------------------------------------------------
// detekt — the Kotlin static-analysis floor (Issue #36)
// ---------------------------------------------------------------------------
//
// One committed, reviewed configuration for the whole repository, resolved from
// the root so :app and :samsung cannot drift onto different rule sets.
// `buildUponDefaultConfig = true` means every detekt default rule is active and
// this file only records the deltas, so an unmentioned rule is on.
//
// There is deliberately no `baseline` property. A generated baseline would
// freeze today's findings as permanently acceptable; Issue #36 forbids it.
detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    autoCorrect = false
    // Production Kotlin only — see the scope note in config/detekt/detekt.yml.
    source.setFrom("src/main/java", "src/main/kotlin")
}

dependencies {
    // app -> samsung is the only production module edge (modules.md).
    implementation(project(":samsung"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // S02: ViewModels for LocalNetwork/Discovery and lifecycle-aware state
    // collection. Same Lifecycle version already pinned and already on the
    // resolved graph through navigation-compose; now declared directly.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // S03 (Issue #79): the device-local application store. Room holds the
    // selected-television profile; DataStore holds the typed preference keys.
    // No television secret, address, certificate or command text is stored in
    // either (data.md#room, data.md#datastore).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

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
    // S02: ViewModel tests drive viewModelScope with a test Main dispatcher.
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented (on-device) tests. These prove the installed debug APK
    // launches to Welcome, which is an Issue #27 acceptance criterion that a
    // JVM/Robolectric test cannot evidence.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
}

// ---------------------------------------------------------------------------
// Manifest guards (docs/architecture/release.md#manifest-allowlist)
// ---------------------------------------------------------------------------

// The allowlist is owned verbatim by release.md. It is an allowlist, not an
// instruction to declare these permissions: S02 declares the four discovery
// permissions and nothing else (no BILLING until the licensing slice).
val manifestPermissionAllowlist =
    setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.CHANGE_WIFI_MULTICAST_STATE",
        "com.android.vending.BILLING",
    )

abstract class MergedManifestGuard : DefaultTask() {
    @get:InputFile abstract val mergedManifest: RegularFileProperty

    @get:Input abstract val allowlist: SetProperty<String>

    @get:Input abstract val applicationId: Property<String>

    @get:Input abstract val variantName: Property<String>

    @TaskAction
    fun check() {
        val text = mergedManifest.get().asFile.readText()

        val declared =
            Regex("""<uses-permission[^>]*android:name\s*=\s*"([^"]+)"""")
                .findAll(text)
                .map { it.groupValues[1] }
                .toSortedSet()

        // `AD_ID` must never appear, in any form, regardless of who declared it.
        val adIdOffenders = declared.filter { it.endsWith("AD_ID") }
        if (adIdOffenders.isNotEmpty()) {
            throw GradleException(
                "adIdAbsentFromManifest failed for ${variantName.get()}: AD_ID must never appear " +
                    "in the merged manifest (docs/architecture/diagnostics.md).\n" +
                    adIdOffenders.joinToString("\n") { "  $it" }
            )
        }

        // An app-scoped *self*-permission is a different thing from a capability
        // request, and the two must not be conflated.
        //
        // AndroidX injects `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
        // and defines it, in the same merged manifest, with
        // `android:protectionLevel="signature"`. It grants the app nothing from
        // the platform, the user, or any other app: it is how `ContextCompat`
        // keeps a dynamically registered receiver private on API levels below 33,
        // which the AppT baseline (minSdk 29) still includes. It carries no
        // privacy meaning, cannot appear on a store listing permission list, and
        // is not something release.md's allowlist is about.
        //
        // It is therefore evaluated as a self-permission, under conditions strict
        // enough that nothing meaningful can hide here:
        //   * the name must be namespaced under this build's applicationId, and
        //   * the same manifest must define it with signature protection.
        // Anything failing either condition is treated as a capability request
        // and must be on the release.md allowlist.
        //
        // The allowlist itself is unchanged and still matches
        // docs/architecture/release.md#manifest-allowlist exactly.
        val signaturePermissions =
            Regex(
                    """<permission[^>]*android:name\s*=\s*"([^"]+)"[^>]*android:protectionLevel\s*=\s*"signature""""
                )
                .findAll(text)
                .map { it.groupValues[1] }
                .toSet() +
                Regex(
                        """<permission[^>]*android:protectionLevel\s*=\s*"signature"[^>]*android:name\s*=\s*"([^"]+)""""
                    )
                    .findAll(text)
                    .map { it.groupValues[1] }
                    .toSet()

        val selfPermissionPrefix = applicationId.get() + "."
        val (selfPermissions, requested) =
            declared.partition { permission ->
                permission.startsWith(selfPermissionPrefix) && permission in signaturePermissions
            }

        val unapproved = requested.toSortedSet() - allowlist.get()
        if (unapproved.isNotEmpty()) {
            throw GradleException(
                "manifestPermissionAllowlist failed for ${variantName.get()}: the merged manifest " +
                    "declares permissions outside the allowlist owned by " +
                    "docs/architecture/release.md#manifest-allowlist.\n" +
                    unapproved.joinToString("\n") { "  $it" } +
                    "\nAllowlist:\n" +
                    allowlist.get().sorted().joinToString("\n") { "  $it" }
            )
        }

        logger.lifecycle(
            "manifestPermissionAllowlist + adIdAbsentFromManifest: OK for ${variantName.get()}. " +
                "Requested permissions: ${if (requested.isEmpty()) "none" else requested.sorted().joinToString(", ")}. " +
                "App-scoped signature self-permissions: " +
                (if (selfPermissions.isEmpty()) "none"
                else selfPermissions.sorted().joinToString(", ")) +
                "."
        )
    }
}

androidComponents.onVariants { variant ->
    val capitalized = variant.name.replaceFirstChar { it.titlecase(Locale.ROOT) }
    val guard =
        tasks.register<MergedManifestGuard>("checkMergedManifest$capitalized") {
            group = "verification"
            description =
                "Checks the merged $capitalized manifest against the release.md permission allowlist."
            mergedManifest.set(
                variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
            )
            allowlist.set(manifestPermissionAllowlist)
            applicationId.set(variant.applicationId)
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
    tasks.matching { it.name == "assemble$capitalized" }.configureEach { dependsOn(guard) }
}

// Accepted names, aggregating every variant's merged manifest.
tasks.register("adIdAbsentFromManifest") {
    group = "verification"
    description = "Fails if AD_ID appears in any merged manifest."
    dependsOn(tasks.withType<MergedManifestGuard>())
}

tasks.register("manifestPermissionAllowlist") {
    group = "verification"
    description =
        "Fails if any merged manifest declares a permission outside the release.md allowlist."
    dependsOn(tasks.withType<MergedManifestGuard>())
}

// This release-artifact guard proves the debug-only S05 physical verifier does not leak into
// the distributable variant.
tasks.register("verifyReleaseS05Boundaries") {
    group = "verification"
    description = "Checks the release APK excludes the debug-only S05 physical verifier."
    dependsOn("assembleRelease")
    doLast {
        val forbiddenMarkers =
            listOf(
                "DebugS05VerifierAction",
                "DebugS05VerifierCommand",
                "DebugS05VerifierDialog",
                "DebugLatencyRun",
                "DebugLatencyReport",
                "DebugMeasuredCommand",
                "DebugS05VerifierActivity",
                "s05:physical-verifier",
                "S05 physical verification",
                "S05 physical verifier",
                "Start verification",
                "Warm-up Volume Up",
                "Measure Volume Up",
                "Unmeasured warm-up",
                "Measured interactions:",
                "Copy verification result",
            )
        val distributableSources = listOf(file("src/main"), file("src/release"))
        val sourceLeaks =
            distributableSources
                .filter { it.exists() }
                .flatMap { sourceRoot -> sourceRoot.walkTopDown().filter { it.isFile }.toList() }
                .flatMap { source ->
                    val contents = source.readText()
                    forbiddenMarkers.filter { marker -> marker in contents }.map { marker ->
                        "${source.relativeTo(projectDir)} contains '$marker'"
                    }
                }
        if (sourceLeaks.isNotEmpty()) {
            throw GradleException(
                "Release source graph contains debug S05 verifier material:\n" +
                    sourceLeaks.joinToString("\n")
            )
        }

        val apkDirectory = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val releaseApks = apkDirectory.listFiles { candidate -> candidate.extension == "apk" }.orEmpty()
        if (releaseApks.isEmpty()) {
            throw GradleException("No release APK found under ${apkDirectory.relativeTo(projectDir)}.")
        }
        releaseApks.forEach { apk ->
            ZipFile(apk).use { archive ->
                val inspectedEntries =
                    archive.entries().asSequence().filter { entry ->
                        !entry.isDirectory &&
                            (entry.name == "AndroidManifest.xml" ||
                                entry.name == "resources.arsc" ||
                                (entry.name.startsWith("classes") && entry.name.endsWith(".dex")))
                    }
                inspectedEntries.forEach { entry ->
                    val contents =
                        archive.getInputStream(entry).use { it.readBytes().toString(Charsets.ISO_8859_1) }
                    val leakedMarkers = forbiddenMarkers.filter { marker -> marker in contents }
                    if (leakedMarkers.isNotEmpty()) {
                        throw GradleException(
                            "${apk.name}:${entry.name} contains debug-only S05 material: " +
                                leakedMarkers.joinToString(", ")
                        )
                    }
                }
            }
        }
        logger.lifecycle(
            "verifyReleaseS05Boundaries: OK — release source graph and APK exclude the debug S05 physical verifier."
        )
    }
}

// ---------------------------------------------------------------------------
// Issue #54 — narrow security constraints (never resolutionStrategy.force)
// ---------------------------------------------------------------------------
//
// Some transitive versions on AGP 9.4.1's own lint tooling classpath
// (`androidLintTool`) and on the unit-test classpaths carry GitHub security
// advisories, and their parent graph (AGP lint / the test stack) cannot move
// until Google or the upstream publisher releases. Each family gets the
// narrowest constraint that reaches a patched release:
//   * bcprov-jdk18on 1.86 — GHSA-9pwp-9qqc-pr26, GHSA-qp49-qgx5-5m26,
//     GHSA-c3fc-8qff-9hwx (fixed well before 1.85; 1.86 is the latest stable)
//   * bcpkix-jdk18on 1.86 — GHSA-wg6q-6289-32hp (patched in 1.84)
//   * commons-lang3 3.20.0 — GHSA-j288-q9x7-2f5v (patched in 3.18.0)
//   * httpclient 4.5.14 — GHSA-7r82-7xv7-xcpj (patched in 4.5.13)
// The configuration-name match keeps the constraint attached exactly where
// those families resolve; everything else keeps resolving untouched.
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
            cfg.contains("UnitTest") || cfg == "testImplementationDependenciesMetadata" ->
                listOf("org.bouncycastle:bcprov-jdk18on:1.86")
            else -> emptyList<String>()
        }
    notations.forEach { notation -> project.dependencies.constraints { add(cfg, notation) } }
}

// Keep failed Robolectric/JUnit assertions actionable in the hosted diagnostic
// path as well as in the uploaded XML reports.
tasks.withType<Test>().configureEach {
    testLogging {
        events = setOf(TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
    }
}
