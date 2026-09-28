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
        // TEMP DIAGNOSTIC (S04): -Werror masks warnings behind a generic summary; every K2
        // warning diagnostic (FirErrors @ v2.4.20) escalated by name so the first offender is
        // NAMED. REMOVE.
        freeCompilerArgs.addAll(
            "-Xwarning-level=ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT:error",
            "-Xwarning-level=ACTUAL_IGNORABILITY_NOT_MATCH_EXPECT:error",
            "-Xwarning-level=ANNOTATIONS_ON_BLOCK_LEVEL_EXPRESSION_ON_THE_SAME_LINE:error",
            "-Xwarning-level=ANNOTATION_WILL_BE_APPLIED_ALSO_TO_PROPERTY_OR_FIELD:error",
            "-Xwarning-level=ARRAY_EQUALITY_OPERATOR_CAN_BE_REPLACED_WITH_CONTENT_EQUALS:error",
            "-Xwarning-level=ASSIGNED_VALUE_IS_NEVER_READ:error",
            "-Xwarning-level=ATOMIC_REF_CALL_ARGUMENT_WITHOUT_CONSISTENT_IDENTITY:error",
            "-Xwarning-level=ATOMIC_REF_WITHOUT_CONSISTENT_IDENTITY:error",
            "-Xwarning-level=CANNOT_CHANGE_ACCESS_PRIVILEGE_WARNING:error",
            "-Xwarning-level=CANNOT_INFER_VISIBILITY_WARNING:error",
            "-Xwarning-level=CANNOT_WEAKEN_ACCESS_PRIVILEGE_WARNING:error",
            "-Xwarning-level=CAN_BE_VAL:error",
            "-Xwarning-level=CAN_BE_VAL_DELAYED_INITIALIZATION:error",
            "-Xwarning-level=CAN_BE_VAL_LATEINIT:error",
            "-Xwarning-level=CAST_NEVER_SUCCEEDS:error",
            "-Xwarning-level=CLASS_LITERAL_LHS_NOT_A_CLASS_WARNING:error",
            "-Xwarning-level=CONFLICTING_PROJECTION_IN_CALLABLE_REFERENCE_WARNING:error",
            "-Xwarning-level=CONTEXTUAL_OVERLOAD_SHADOWED:error",
            "-Xwarning-level=DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE:error",
            "-Xwarning-level=DEPRECATED_ACCESS_TO_ENTRIES_AS_QUALIFIER:error",
            "-Xwarning-level=DEPRECATED_ACCESS_TO_ENTRIES_PROPERTY:error",
            "-Xwarning-level=DEPRECATED_ACCESS_TO_ENTRY_PROPERTY_FROM_ENUM:error",
            "-Xwarning-level=DEPRECATED_ACCESS_TO_ENUM_ENTRY_COMPANION_PROPERTY:error",
            "-Xwarning-level=DEPRECATED_ACCESS_TO_ENUM_ENTRY_PROPERTY_AS_REFERENCE:error",
            "-Xwarning-level=DEPRECATED_IDENTITY_EQUALS:error",
            "-Xwarning-level=DEPRECATED_MODIFIER_CONTAINING_DECLARATION:error",
            "-Xwarning-level=DEPRECATED_MODIFIER_FOR_TARGET:error",
            "-Xwarning-level=DEPRECATED_MODIFIER_PAIR:error",
            "-Xwarning-level=DEPRECATED_SMARTCAST_ON_DELEGATED_PROPERTY:error",
            "-Xwarning-level=DEPRECATION:error",
            "-Xwarning-level=DEPRECATION_ERROR_MIGRATION_PERIOD_WARNING:error",
            "-Xwarning-level=DESTRUCTURING_SHORT_FORM_NAME_MISMATCH:error",
            "-Xwarning-level=DESTRUCTURING_SHORT_FORM_OF_NON_DATA_CLASS:error",
            "-Xwarning-level=DESTRUCTURING_SHORT_FORM_UNDERSCORE:error",
            "-Xwarning-level=DIFFERENT_NAMES_FOR_THE_SAME_PARAMETER_IN_SUPERTYPES:error",
            "-Xwarning-level=DIVISION_BY_ZERO:error",
            "-Xwarning-level=DSL_MARKER_APPLIED_TO_WRONG_TARGET:error",
            "-Xwarning-level=DSL_MARKER_PROPAGATES_TO_MANY:error",
            "-Xwarning-level=DUPLICATE_BRANCH_CONDITION_IN_WHEN:error",
            "-Xwarning-level=EMPTY_RANGE:error",
            "-Xwarning-level=EQUALITY_NOT_APPLICABLE_WARNING:error",
            "-Xwarning-level=ERROR_SUPPRESSION:error",
            "-Xwarning-level=ESCAPING_CAPTURED_VARIABLE:error",
            "-Xwarning-level=EXPANSIVE_INHERITANCE_IN_JAVA:error",
            "-Xwarning-level=EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING:error",
            "-Xwarning-level=EXPLICIT_TYPE_ARGUMENTS_IN_PROPERTY_ACCESS_WARNING:error",
            "-Xwarning-level=EXPOSED_TYPE_PARAMETER_BOUND_DEPRECATION_WARNING:error",
            "-Xwarning-level=EXPRESSION_OF_NULLABLE_TYPE_IN_CLASS_LITERAL_LHS_WARNING:error",
            "-Xwarning-level=EXTENSION_FUNCTION_SHADOWED_BY_MEMBER_PROPERTY_WITH_INVOKE:error",
            "-Xwarning-level=EXTENSION_SHADOWED_BY_MEMBER:error",
            "-Xwarning-level=FINAL_UPPER_BOUND:error",
            "-Xwarning-level=FINITE_BOUNDS_VIOLATION_IN_JAVA:error",
            "-Xwarning-level=FORBIDDEN_IDENTITY_EQUALS_WARNING:error",
            "-Xwarning-level=ILLEGAL_TYPE_ARGUMENT_FOR_VARARG_PARAMETER_WARNING:error",
            "-Xwarning-level=IMPLICIT_BOXING_IN_IDENTITY_EQUALS:error",
            "-Xwarning-level=IMPLICIT_PROPERTY_TYPE_MAKES_BEHAVIOR_ORDER_DEPENDANT:error",
            "-Xwarning-level=INAPPLICABLE_OPERATOR_MODIFIER_WARNING:error",
            "-Xwarning-level=INAPPLICABLE_TARGET_ON_PROPERTY_WARNING:error",
            "-Xwarning-level=INCOMPATIBLE_ENUM_COMPARISON:error",
            "-Xwarning-level=INCOMPATIBLE_TYPES_WARNING:error",
            "-Xwarning-level=INEFFICIENT_EQUALS_OVERRIDING_IN_VALUE_CLASS:error",
            "-Xwarning-level=INFERRED_INVISIBLE_REIFIED_TYPE_ARGUMENT_WARNING:error",
            "-Xwarning-level=INFERRED_INVISIBLE_RETURN_TYPE_WARNING:error",
            "-Xwarning-level=INFERRED_INVISIBLE_VARARG_TYPE_ARGUMENT_WARNING:error",
            "-Xwarning-level=INFERRED_INVISIBLE_WHEN_TYPE_WARNING:error",
            "-Xwarning-level=INFERRED_TYPE_VARIABLE_INTO_POSSIBLE_EMPTY_INTERSECTION:error",
            "-Xwarning-level=INITIALIZATION_BEFORE_DECLARATION_WARNING:error",
            "-Xwarning-level=INLINE_CLASS_DEPRECATED:error",
            "-Xwarning-level=INTEGER_LITERAL_CAST_INSTEAD_OF_TO_CALL:error",
            "-Xwarning-level=INVISIBLE_REFERENCE_WARNING:error",
            "-Xwarning-level=K_SUSPEND_FUNCTION_TYPE_OF_DANGEROUSLY_LARGE_ARITY:error",
            "-Xwarning-level=LABEL_NAME_CLASH:error",
            "-Xwarning-level=LEAKED_IN_PLACE_LAMBDA:error",
            "-Xwarning-level=LOCAL_VARIABLE_WITH_TYPE_PARAMETERS_WARNING:error",
            "-Xwarning-level=MISPLACED_TYPE_PARAMETER_CONSTRAINTS:error",
            "-Xwarning-level=MISSING_BRANCH_FOR_NON_ABSTRACT_SEALED_CLASS:error",
            "-Xwarning-level=MISSING_DEPENDENCY_CLASS_IN_EXPRESSION_TYPE:error",
            "-Xwarning-level=MISSING_DEPENDENCY_CLASS_IN_LAMBDA_PARAMETER:error",
            "-Xwarning-level=MISSING_DEPENDENCY_CLASS_IN_LAMBDA_RECEIVER:error",
            "-Xwarning-level=MISSING_DEPENDENCY_CLASS_IN_TYPEALIAS:error",
            "-Xwarning-level=MISSING_DEPENDENCY_SUPERCLASS_WARNING:error",
            "-Xwarning-level=MUST_BE_INITIALIZED_OR_BE_ABSTRACT_WARNING:error",
            "-Xwarning-level=MUST_BE_INITIALIZED_OR_BE_FINAL_WARNING:error",
            "-Xwarning-level=MUST_BE_INITIALIZED_OR_FINAL_OR_ABSTRACT_WARNING:error",
            "-Xwarning-level=MUST_BE_INITIALIZED_WARNING:error",
            "-Xwarning-level=MUTABLE_PROPERTY_WITH_CAPTURED_TYPE:error",
            "-Xwarning-level=NEWER_VERSION_IN_SINCE_KOTLIN:error",
            "-Xwarning-level=NON_FINAL_MEMBER_IN_FINAL_CLASS:error",
            "-Xwarning-level=NON_FINAL_MEMBER_IN_OBJECT:error",
            "-Xwarning-level=NON_PUBLIC_CALL_FROM_PUBLIC_INLINE_DEPRECATION:error",
            "-Xwarning-level=NON_TAIL_RECURSIVE_CALL:error",
            "-Xwarning-level=NOTHING_TO_INLINE:error",
            "-Xwarning-level=NOT_NULL_ASSERTION_ON_CALLABLE_REFERENCE:error",
            "-Xwarning-level=NOT_NULL_ASSERTION_ON_LAMBDA_EXPRESSION:error",
            "-Xwarning-level=NOT_YET_SUPPORTED_IN_INLINE_WARNING:error",
            "-Xwarning-level=NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING:error",
            "-Xwarning-level=NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING:error",
            "-Xwarning-level=NO_TAIL_CALLS_FOUND:error",
            "-Xwarning-level=NUMERIC_CAST_NEVER_SUCCEEDS_BUT_CAN_BE_REPLACED_WITH_TO_CALL:error",
            "-Xwarning-level=OPERATOR_RENAMED_ON_IMPORT:error",
            "-Xwarning-level=OPT_IN_ARGUMENT_IS_NOT_MARKER:error",
            "-Xwarning-level=OPT_IN_MARKER_ON_OVERRIDE_WARNING:error",
            "-Xwarning-level=OPT_IN_OVERRIDE:error",
            "-Xwarning-level=OPT_IN_TO_INHERITANCE:error",
            "-Xwarning-level=OPT_IN_USAGE:error",
            "-Xwarning-level=OPT_IN_WITHOUT_ARGUMENTS:error",
            "-Xwarning-level=OVERRIDE_BY_INLINE:error",
            "-Xwarning-level=OVERRIDE_DEPRECATION:error",
            "-Xwarning-level=OVERRIDING_IGNORABLE_WITH_MUST_USE:error",
            "-Xwarning-level=PARAMETER_NAME_CHANGED_ON_OVERRIDE:error",
            "-Xwarning-level=PLATFORM_CLASS_MAPPED_TO_KOTLIN:error",
            "-Xwarning-level=POTENTIALLY_NON_REPORTED_ANNOTATION:error",
            "-Xwarning-level=REDUNDANT_ANNOTATION:error",
            "-Xwarning-level=REDUNDANT_ANNOTATION_TARGET:error",
            "-Xwarning-level=REDUNDANT_CALL_OF_CONVERSION_METHOD:error",
            "-Xwarning-level=REDUNDANT_ELSE_IN_WHEN:error",
            "-Xwarning-level=REDUNDANT_EXPLICIT_BACKING_FIELD:error",
            "-Xwarning-level=REDUNDANT_INTERPOLATION_PREFIX:error",
            "-Xwarning-level=REDUNDANT_LABEL_WARNING:error",
            "-Xwarning-level=REDUNDANT_MODALITY_MODIFIER:error",
            "-Xwarning-level=REDUNDANT_MODIFIER:error",
            "-Xwarning-level=REDUNDANT_MODIFIER_FOR_TARGET:error",
            "-Xwarning-level=REDUNDANT_NULLABLE:error",
            "-Xwarning-level=REDUNDANT_OPEN_IN_INTERFACE:error",
            "-Xwarning-level=REDUNDANT_PROJECTION:error",
            "-Xwarning-level=REDUNDANT_RETURN:error",
            "-Xwarning-level=REDUNDANT_RETURN_UNIT_TYPE:error",
            "-Xwarning-level=REDUNDANT_SETTER_PARAMETER_TYPE:error",
            "-Xwarning-level=REDUNDANT_SINGLE_EXPRESSION_STRING_TEMPLATE:error",
            "-Xwarning-level=REDUNDANT_SPREAD_OPERATOR_IN_NAMED_FORM_IN_ANNOTATION:error",
            "-Xwarning-level=REDUNDANT_SPREAD_OPERATOR_IN_NAMED_FORM_IN_FUNCTION:error",
            "-Xwarning-level=REDUNDANT_VISIBILITY_MODIFIER:error",
            "-Xwarning-level=REPEATED_ANNOTATION_WARNING:error",
            "-Xwarning-level=RESOLVED_TO_UNDERSCORE_NAMED_CATCH_PARAMETER:error",
            "-Xwarning-level=RETURN_IN_FUNCTION_WITH_EXPRESSION_BODY_WARNING:error",
            "-Xwarning-level=RETURN_VALUE_NOT_USED:error",
            "-Xwarning-level=RETURN_VALUE_NOT_USED_COERCION:error",
            "-Xwarning-level=ROOT_IDE_PACKAGE_DEPRECATED:error",
            "-Xwarning-level=SAFE_CAST_RELYING_ON_NULL:error",
            "-Xwarning-level=SENSELESS_COMPARISON:error",
            "-Xwarning-level=SENSELESS_NULL_IN_WHEN:error",
            "-Xwarning-level=TAIL_RECURSION_IN_TRY_IS_NOT_SUPPORTED:error",
            "-Xwarning-level=TRIM_MARGIN_BLANK_PREFIX:error",
            "-Xwarning-level=TYPEALIAS_EXPANSION_DEPRECATION:error",
            "-Xwarning-level=TYPE_ARGUMENTS_NOT_ALLOWED_IN_PACKAGE_QUALIFIER_WARNING:error",
            "-Xwarning-level=TYPE_ARGUMENTS_NOT_ALLOWED_WARNING:error",
            "-Xwarning-level=TYPE_ARGUMENTS_REDUNDANT_IN_SUPER_QUALIFIER:error",
            "-Xwarning-level=TYPE_INTERSECTION_AS_REIFIED_DEPRECATION_WARNING:error",
            "-Xwarning-level=TYPE_PARAMETER_AS_REIFIED_DEPRECATION_WARNING:error",
            "-Xwarning-level=UNCHECKED_CAST:error",
            "-Xwarning-level=UNNAMED_PROPERTY_WITH_IMPLICIT_IGNORABLE_TYPE:error",
            "-Xwarning-level=UNNECESSARY_LATEINIT:error",
            "-Xwarning-level=UNNECESSARY_NOT_NULL_ASSERTION:error",
            "-Xwarning-level=UNNECESSARY_SAFE_CALL:error",
            "-Xwarning-level=UNREACHABLE_CODE:error",
            "-Xwarning-level=UNSAFE_CAST_RELYING_ON_NULL:error",
            "-Xwarning-level=UNSUPPORTED_ARRAY_OF_NOTHING_IN_CLASS_LITERAL_LHS:error",
            "-Xwarning-level=UNUSED_ANONYMOUS_PARAMETER:error",
            "-Xwarning-level=UNUSED_EXPRESSION:error",
            "-Xwarning-level=UNUSED_LAMBDA_EXPRESSION:error",
            "-Xwarning-level=UNUSED_VARIABLE:error",
            "-Xwarning-level=UPPER_BOUND_VIOLATED_DEPRECATION_WARNING:error",
            "-Xwarning-level=UPPER_BOUND_VIOLATED_IN_LHS_OF_CLASS_LITERAL_WARNING:error",
            "-Xwarning-level=UPPER_BOUND_VIOLATED_IN_TYPEALIAS_EXPANSION_DEPRECATION_WARNING:error",
            "-Xwarning-level=USELESS_CALL_ON_NOT_NULL:error",
            "-Xwarning-level=USELESS_CAST:error",
            "-Xwarning-level=USELESS_ELVIS:error",
            "-Xwarning-level=USELESS_ELVIS_LEFT_IS_NULL:error",
            "-Xwarning-level=USELESS_ELVIS_RIGHT_IS_NULL:error",
            "-Xwarning-level=USELESS_IS_CHECK:error",
            "-Xwarning-level=USELESS_VARARG_ON_PARAMETER:error",
            "-Xwarning-level=VARIABLE_INITIALIZER_IS_REDUNDANT:error",
            "-Xwarning-level=VARIABLE_NEVER_READ:error",
            "-Xwarning-level=VERSION_REQUIREMENT_DEPRECATION:error",
            "-Xwarning-level=WRONG_ANNOTATION_TARGET_WARNING:error",
            "-Xwarning-level=WRONG_INVOCATION_KIND:error",
            "-Xwarning-level=WRONG_NUMBER_OF_TYPE_ARGUMENTS_IN_GET_CLASS_WARNING:error",
            "-Xwarning-level=WRONG_NUMBER_OF_TYPE_ARGUMENTS_IN_LOCAL_CLASS_IN_LHS_WARNING:error",
            "-Xwarning-level=WRONG_NUMBER_OF_TYPE_ARGUMENTS_WARNING:error",
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
