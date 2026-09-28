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
        allWarningsAsErrors.set(false) // TEMP DIAGNOSTIC: warnings captured below instead of failing
        extraWarnings.set(true)
        // TEMP DIAGNOSTIC (S04): bisect which warning category fails -Werror. REMOVE BEFORE FINISH.
        freeCompilerArgs.addAll(
            "-Xwarning-level=UNUSED_VARIABLE:disabled",
            "-Xwarning-level=UNUSED_EXPRESSION:disabled",
            "-Xwarning-level=UNUSED_ANONYMOUS_PARAMETER:disabled",
            "-Xwarning-level=UNUSED_LAMBDA_EXPRESSION:disabled",
            "-Xwarning-level=ASSIGNED_VALUE_IS_NEVER_READ:disabled",
            "-Xwarning-level=USELESS_ELVIS:disabled",
            "-Xwarning-level=USELESS_CAST:disabled",
            "-Xwarning-level=USELESS_IS_CHECK:disabled",
            "-Xwarning-level=USELESS_CALL_ON_NOT_NULL:disabled",
            "-Xwarning-level=UNNECESSARY_SAFE_CALL:disabled",
            "-Xwarning-level=UNNECESSARY_NOT_NULL_ASSERTION:disabled",
            "-Xwarning-level=REDUNDANT_VISIBILITY_MODIFIER:disabled",
            "-Xwarning-level=REDUNDANT_SINGLE_EXPRESSION_STRING_TEMPLATE:disabled",
            "-Xwarning-level=DEPRECATION:disabled",
            "-Xwarning-level=UNCHECKED_CAST:disabled",
            "-Xwarning-level=CAST_NEVER_SUCCEEDS:disabled",
            "-Xwarning-level=EXTENSION_SHADOWED_BY_MEMBER:disabled",
            "-Xwarning-level=IMPLICIT_BOXING_IN_IDENTITY_EQUALS:disabled",
            "-Xwarning-level=INTEGER_LITERAL_CAST_INSTEAD_OF_TO_CALL:disabled",
            "-Xwarning-level=DEPRECATED_SMARTCAST_ON_DELEGATED_PROPERTY:disabled",
            "-Xwarning-level=OPT_IN_USAGE:disabled",
            "-Xwarning-level=OPT_IN_OVERRIDE:disabled",
            "-Xwarning-level=OPT_IN_TO_INHERITANCE:disabled",
            "-Xwarning-level=OPT_IN_WITHOUT_ARGUMENTS:disabled",
            "-Xwarning-level=REDUNDANT_ELSE_IN_WHEN:disabled",
            "-Xwarning-level=VARIABLE_INITIALIZER_IS_REDUNDANT:disabled",
            "-Xwarning-level=REDUNDANT_NULLABLE:disabled",
            "-Xwarning-level=REDUNDANT_RETURN:disabled",
            "-Xwarning-level=REDUNDANT_MODALITY_MODIFIER:disabled",
            "-Xwarning-level=DEPRECATED_IDENTITY_EQUALS:disabled",
            "-Xwarning-level=REDUNDANT_MODIFIER:disabled",
            "-Xwarning-level=REDUNDANT_MODIFIER_FOR_TARGET:disabled",
            "-Xwarning-level=UNNECESSARY_LATEINIT:disabled",
            "-Xwarning-level=REDUNDANT_LABEL_WARNING:disabled",
            "-Xwarning-level=REDUNDANT_PROJECTION:disabled",
            "-Xwarning-level=INFERRED_INVISIBLE_RETURN_TYPE_WARNING:disabled",
            "-Xwarning-level=INFERRED_INVISIBLE_WHEN_TYPE_WARNING:disabled",
            "-Xwarning-level=IMPLICIT_PROPERTY_TYPE_MAKES_BEHAVIOR_ORDER_DEPENDANT:disabled",
            "-Xwarning-level=RETURN_IN_FUNCTION_WITH_EXPRESSION_BODY_WARNING:disabled",
            "-Xwarning-level=REDUNDANT_CALL_OF_CONVERSION_METHOD:disabled",
            "-Xwarning-level=INVISIBLE_REFERENCE_WARNING:disabled",
            "-Xwarning-level=REDUNDANT_INTERPOLATION_PREFIX:disabled",
            "-Xwarning-level=USELESS_VARARG_ON_PARAMETER:disabled",
            "-Xwarning-level=REDUNDANT_SPREAD_OPERATOR_IN_NAMED_FORM_IN_FUNCTION:disabled",
            "-Xwarning-level=CONTEXTUAL_OVERLOAD_SHADOWED:disabled",
            "-Xwarning-level=EXTENSION_FUNCTION_SHADOWED_BY_MEMBER_PROPERTY_WITH_INVOKE:disabled",
            "-Xwarning-level=ANNOTATIONS_ON_BLOCK_LEVEL_EXPRESSION_ON_THE_SAME_LINE:disabled",
            "-Xwarning-level=NOT_NULL_ASSERTION_ON_LAMBDA_EXPRESSION:disabled",
            "-Xwarning-level=UNSAFE_CAST_RELYING_ON_NULL:disabled",
            "-Xwarning-level=SAFE_CAST_RELYING_ON_NULL:disabled",
            "-Xwarning-level=NUMERIC_CAST_NEVER_SUCCEEDS_BUT_CAN_BE_REPLACED_WITH_TO_CALL:disabled",
            "-Xwarning-level=DEPRECATED_MODIFIER_FOR_TARGET:disabled",
            "-Xwarning-level=DEPRECATED_MODIFIER_PAIR:disabled",
            "-Xwarning-level=DEPRECATED_MODIFIER_CONTAINING_DECLARATION:disabled",
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

// TEMP DIAGNOSTIC (S04): surface Kotlin compile warnings through the task failure message, so
// they reach the CI annotation channel. REMOVE BEFORE FINISH.
val capturedWarnings = mutableListOf<String>()
tasks.matching { it.name == "compileDebugKotlin" }.configureEach {
    doFirst {
        logging.addStandardOutputListener { line ->
            if (line.contains("w: ")) capturedWarnings.add(line.trim())
        }
        logging.addStandardErrorListener { line ->
            if (line.contains("w: ")) capturedWarnings.add(line.trim())
        }
    }
    doLast {
        if (capturedWarnings.isNotEmpty()) {
            throw GradleException(
                "CAPTURED COMPILE WARNINGS: " + capturedWarnings.joinToString(" || ")
            )
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
