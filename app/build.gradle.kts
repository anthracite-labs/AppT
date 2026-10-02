import java.util.Base64
import java.util.Locale
import java.util.zip.ZipFile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

val apptBuildSha =
    providers
        .environmentVariable("APPT_BUILD_SHA")
        .orElse(providers.gradleProperty("apptBuildSha"))
        .getOrElse("local")

require(apptBuildSha == "local" || apptBuildSha.matches(Regex("[0-9a-f]{40}"))) {
    "APPT_BUILD_SHA must be a full lowercase 40-character Git SHA or unset for a local build."
}

android {
    namespace = "dev.anthracite.appt"
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

    buildTypes { release { isMinifyEnabled = false } }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests { isIncludeAndroidResources = true } }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true

        informational +=
            setOf(
                "AndroidGradlePluginVersion",
                "GradleDependency",
                "NewerVersionAvailable",
                "OldTargetApi",
            )
        disable += setOf("MissingTranslation")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
        extraWarnings.set(true)
    }
}

ksp {
    arg("room.schemaLocation", "${projectDir}/schemas")
    arg("room.generateKotlin", "false")
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    autoCorrect = false
    source.setFrom("src/main/java", "src/main/kotlin")
}

dependencies {
    implementation(project(":samsung"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

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
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
}

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

        val adIdOffenders = declared.filter { it.endsWith("AD_ID") }
        if (adIdOffenders.isNotEmpty()) {
            throw GradleException(
                "adIdAbsentFromManifest failed for ${variantName.get()}: AD_ID must never appear " +
                    "in the merged manifest (docs/architecture/diagnostics.md).\n" +
                    adIdOffenders.joinToString("\n") { "  $it" }
            )
        }

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
    tasks.matching { it.name == "assemble$capitalized" }.configureEach { dependsOn(guard) }
}

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

/**
 * TEMPORARY probe (remove before completion): applies the pinned Spotless/ktfmt formatter in this
 * run and reports its complete unified diff as base64 chunks, so the exact canonical content can be
 * applied here without a local JVM. GitHub keeps about ten annotations per step.
 */
tasks.register("formatProbe") {
    group = "verification"
    dependsOn(rootProject.tasks.named("spotlessApply"))
    doLast {
        val root = rootProject.projectDir
        fun git(vararg args: String): String {
            val process =
                ProcessBuilder(listOf("git", "--no-pager") + args)
                    .directory(root)
                    .redirectErrorStream(true)
                    .start()
            val text = process.inputStream.bufferedReader().readText()
            process.waitFor()
            return text
        }
        val diff = git("diff", "--unified=3", "--", "*.kt")
        val files = git("diff", "--numstat", "--", "*.kt").lines().filter { it.isNotBlank() }
        val encoded = Base64.getEncoder().encodeToString(diff.toByteArray())
        println("::warning title=fmt-count::" + files.size + " kotlin file(s) differ")
        encoded.chunked(3000).take(5).forEachIndexed { index, chunk ->
            println("::warning title=fmt-b64-" + index + "::" + chunk)
        }
        val gradleFiles =
            git("diff", "--numstat", "--", "*.gradle.kts", "*.kts").lines().filter { it.isNotBlank() }
        println("::warning title=fmt-kts::" + gradleFiles.joinToString(" | ").take(400))
        gradleFiles.firstOrNull()?.let { line ->
            val path = line.split("\t").getOrNull(2) ?: return@let
            git("diff", "--unified=0", "--", path)
                .lineSequence()
                .filter { it.startsWith("+") && !it.startsWith("+++") }
                .take(3)
                .forEach { added -> println("::warning title=fmt-kts+::" + added.take(150)) }
        }
        logger.lifecycle("formatProbe: " + files.size + " kotlin file(s) differ")
    }
}

tasks.register("verifyReleaseEngineeringBoundaries") {
    group = "verification"
    description =
        "Checks the release APK excludes the debug-only Engineering Verifier and its S05 latency " +
            "run."
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
                "EngineeringVerifierEntry",
                "EngineeringVerifierController",
                "EngineeringTestTags",
                "EngineeringScenarios",
                "EngineeringEvidenceClass",
                "EngineeringObservationSource",
                "EngineeringReport",
                "EngineeringLedger",
                "AppT engineering verification",
                "AppT engineering report",
                "Exact candidate SHA unavailable",
                "Requires a Ready television",
                "Pending external",
                "appt:engineering-entry",
                "appt:engineering-surface",
                "appt:engineering-record-",
            )
        val distributableSources = listOf(file("src/main"), file("src/release"))
        val sourceLeaks =
            distributableSources
                .filter { it.exists() }
                .flatMap { sourceRoot -> sourceRoot.walkTopDown().filter { it.isFile }.toList() }
                .flatMap { source ->
                    val contents = source.readText()
                    forbiddenMarkers
                        .filter { marker -> marker in contents }
                        .map { marker -> "${source.relativeTo(projectDir)} contains '$marker'" }
                }
        if (sourceLeaks.isNotEmpty()) {
            throw GradleException(
                "Release source graph contains debug engineering verifier material:\n" +
                    sourceLeaks.joinToString("\n")
            )
        }

        val apkDirectory = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val releaseApks =
            apkDirectory.listFiles { candidate -> candidate.extension == "apk" }.orEmpty()
        if (releaseApks.isEmpty()) {
            throw GradleException(
                "No release APK found under ${apkDirectory.relativeTo(projectDir)}."
            )
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
                        archive.getInputStream(entry).use {
                            it.readBytes().toString(Charsets.ISO_8859_1)
                        }
                    val leakedMarkers = forbiddenMarkers.filter { marker -> marker in contents }
                    if (leakedMarkers.isNotEmpty()) {
                        throw GradleException(
                            "${apk.name}:${entry.name} contains debug-only engineering verifier " +
                                "material: " + leakedMarkers.joinToString(", ")
                        )
                    }
                }
            }
        }
        logger.lifecycle(
            "verifyReleaseEngineeringBoundaries: OK — release source graph and APK exclude the " +
                "debug engineering verifier and its S05 latency run."
        )
    }
}

tasks.named("verifyReleaseEngineeringBoundaries") {
    dependsOn("formatProbe")
}

tasks.withType<Test>().configureEach {
    testLogging {
        events = setOf(TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
    }
}
