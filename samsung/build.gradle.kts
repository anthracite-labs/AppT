// :samsung — the production Samsung control module
// (docs/architecture/modules.md#samsung-control-samsung).
//
// S01 established the module and its dependency boundary. S02 adds discovery
// (SamsungTvs.discover()). S03 (Issue #79) adds the first live control session:
// open(), the RemoteSession command path, and the Tizen remote channel. Durable
// saved pairing (S04) and supervised reconnect (S06) follow.
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

    defaultConfig { minSdk = 29 }

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
        informational +=
            setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }
}

// Project-level Kotlin configuration. `jvmTarget` must match the Java
// `compileOptions` above, or AGP fails the build on mismatched bytecode.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // TEMP DIAGNOSTIC (S04): -Werror masks warnings behind a generic summary; name the
        // offenders instead by escalating likely diagnostics individually. REMOVE.
        allWarningsAsErrors.set(false)
        extraWarnings.set(true)
        freeCompilerArgs.addAll(
            "-Xwarning-level=UNUSED_VARIABLE:error",
            "-Xwarning-level=UNUSED_EXPRESSION:error",
            "-Xwarning-level=UNREACHABLE_CODE:error",
            "-Xwarning-level=UNUSED_IMPORT:error",
            "-Xwarning-level=USELESS_CAST:error",
            "-Xwarning-level=DEPRECATION:error",
        )
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

// TEMP DIAGNOSTIC (S04): surface detekt findings through the annotation channel, because the
// runner log is not reachable from the implementation sandbox. REMOVE BEFORE TERMINAL VERIFY.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports.xml.required.set(true)
    finalizedBy("surfaceDetektFindings")
}

tasks.register("surfaceDetektFindings") {
    doLast {
        val report = project.file("build/reports/detekt/detekt.xml")
        if (!report.exists()) {
            println("::error title=detekt-findings::NO DETEKT XML REPORT AT " + report.path)
            return@doLast
        }
        println("::notice title=detekt-findings::report exists, " + report.length() + " bytes")
        val text = report.readText()
        val errorTags = Regex("<error\\b[^>]*>")
        val attr: (String, String) -> String? = { tag, name ->
            Regex(name + "=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)
        }
        val files = Regex("<file name=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</file>")
        var count = 0
        files.findAll(text).forEach { fileMatch ->
            val path = fileMatch.groupValues[1].substringAfterLast("/")
            errorTags.findAll(fileMatch.groupValues[2]).take(12).forEach { errorMatch ->
                val tag = errorMatch.value
                val source = attr(tag, "source") ?: "?"
                val line = attr(tag, "line") ?: "?"
                val message = (attr(tag, "message") ?: "?").take(140)
                count++
                println("::error title=detekt::" + path + ":" + line + " " + source + " :: " + message)
            }
        }
        if (count == 0) {
            println("::error title=detekt-findings::REPORT PARSED BUT ZERO ERRORS EXTRACTED; head: " + text.take(400))
        }
    }
}

dependencies {
    // S02 discovery (Issue #73). Both are already on :app's resolved graph at
    // these exact versions; see the license/provenance notes in
    // gradle/libs.versions.toml and the S02 pull request.
    //   * coroutines: the Flow-based SamsungTvs.discover() contract;
    //   * serialization-json: the bounded device-info parser, through the
    //     JsonElement tree API only (no serialization compiler plugin here).
    // S03 (Issue #79) adds OkHttp for the live remote-control WebSocket session
    // and its TLS handshake. Nothing else: :samsung stays clear of Room,
    // DataStore, WorkManager, Firebase, Play and telemetry
    // (docs/architecture/modules.md#dependency-set-boundaries).
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
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
    notations.forEach { notation -> project.dependencies.constraints { add(cfg, notation) } }
}
