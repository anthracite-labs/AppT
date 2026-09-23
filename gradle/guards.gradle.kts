// Repository guards — the executable privacy / supply-chain / module floor.
//
// Sources of truth:
//   docs/architecture/release.md#pull-request-checks   CI check list and allowlist
//   docs/architecture/diagnostics.md                   telemetry invariants and check names
//   docs/architecture/modules.md#dependency-set-boundaries  module edges
//
// Accepted check names are preserved verbatim so the architecture and the
// build speak the same language:
//   noTelemetryDependency
//   adIdAbsentFromManifest
//   noProductionModuleDependsOnBenchmark
//
// Manifest-derived guards (`adIdAbsentFromManifest`,
// `manifestPermissionAllowlist`) are registered in `app/build.gradle.kts`,
// because only there can they read AGP's MERGED_MANIFEST artifact. The
// lifecycle tasks below aggregate them so every guard is invocable by its
// accepted name from the repository root.

// ---------------------------------------------------------------------------
// Forbidden-artifact vocabulary
// ---------------------------------------------------------------------------

/** Crash-reporting, analytics, advertising and attribution artifacts. */
val telemetryArtifactPatterns: List<String> = listOf(
    "crashlytics",
    "firebase-crashlytics",
    "acra",
    "bugsnag",
    "sentry",
    "firebase-analytics",
    "google-analytics",
    "play-services-analytics",
    "play-services-measurement",
    "play-services-ads",
    "play-services-ads-identifier",
    "play-services-appset",
    "appsflyer",
    "adjust",
    "amplitude",
    "mixpanel",
    "segment",
    "braze",
    "flurry",
    "countly",
    "matomo",
    "datadog",
    "newrelic",
    "instabug",
    "embrace",
)

/** Firestore client artifacts. Firestore is server-only (sync.md). */
val firestoreArtifactPatterns: List<String> = listOf(
    "firebase-firestore",
    "google-cloud-firestore",
    "firestore",
)

/** Everything `:samsung` must stay clear of (modules.md forbidden edges). */
val samsungForbiddenArtifactPatterns: List<String> =
    telemetryArtifactPatterns + firestoreArtifactPatterns + listOf(
        "firebase-",
        "com.google.firebase",
        "play-services-",
        "billing",
        "integrity",
    )

fun Project.resolvedArtifactIds(configurationName: String): List<String> {
    val configuration = configurations.findByName(configurationName)
        ?: error("Configuration '$configurationName' not found on ${this.path}")
    return configuration.incoming.resolutionResult.allComponents
        .map { it.id.displayName }
        .filterNot { it.startsWith("project ") }
        .sorted()
}

fun matches(artifactId: String, patterns: List<String>): Boolean {
    val lowered = artifactId.lowercase()
    return patterns.any { lowered.contains(it) }
}

// ---------------------------------------------------------------------------
// noTelemetryDependency
// ---------------------------------------------------------------------------

tasks.register("noTelemetryDependency") {
    group = "verification"
    description =
        "Fails if any crash-reporting, analytics, advertising or attribution " +
            "artifact appears in the merged production dependency graph."
    val graphs = listOf(":app" to "debugRuntimeClasspath", ":samsung" to "debugRuntimeClasspath")
    doLast {
        val offenders = mutableListOf<String>()
        graphs.forEach { (path, configurationName) ->
            project(path).resolvedArtifactIds(configurationName)
                .filter { matches(it, telemetryArtifactPatterns) }
                .forEach { offenders += "$path/$configurationName -> $it" }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "noTelemetryDependency failed. V1 ships no crash-reporting, analytics, " +
                    "advertising or attribution SDK (docs/architecture/diagnostics.md).\n" +
                    offenders.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("noTelemetryDependency: OK — no telemetry artifact in the production graph.")
    }
}

// ---------------------------------------------------------------------------
// :app must contain no Firestore client and no crash reporter
// ---------------------------------------------------------------------------

tasks.register("noFirestoreClientInApp") {
    group = "verification"
    description = "Fails if a Firestore client artifact appears on :app's runtime graph."
    doLast {
        val offenders = project(":app").resolvedArtifactIds("debugRuntimeClasspath")
            .filter { matches(it, firestoreArtifactPatterns) }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "noFirestoreClientInApp failed. Firestore is server-only; the client never " +
                    "talks to Firestore directly (docs/architecture/sync.md).\n" +
                    offenders.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("noFirestoreClientInApp: OK — no Firestore client artifact on :app.")
    }
}

tasks.register("noCrashReportingInApp") {
    group = "verification"
    description = "Fails if a crash-reporting artifact appears on :app's runtime graph."
    doLast {
        val crashPatterns = listOf("crashlytics", "acra", "bugsnag", "sentry", "instabug", "embrace")
        val offenders = project(":app").resolvedArtifactIds("debugRuntimeClasspath")
            .filter { matches(it, crashPatterns) }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "noCrashReportingInApp failed. V1 ships no crash-reporting SDK " +
                    "(docs/architecture/diagnostics.md).\n" +
                    offenders.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("noCrashReportingInApp: OK — no crash-reporting artifact on :app.")
    }
}

// ---------------------------------------------------------------------------
// :samsung dependency boundary
// ---------------------------------------------------------------------------

tasks.register("samsungDependencyBoundary") {
    group = "verification"
    description =
        "Fails if :samsung gains a Firebase, Play services or telemetry dependency, " +
            "or a dependency on :app."
    doLast {
        val samsung = project(":samsung")
        val offenders = samsung.resolvedArtifactIds("debugRuntimeClasspath")
            .filter { matches(it, samsungForbiddenArtifactPatterns) }
            .toMutableList()

        val projectEdges = samsung.configurations
            .filter { it.name.endsWith("RuntimeClasspath") || it.name.endsWith("CompileClasspath") }
            .flatMap { configuration ->
                configuration.dependencies
                    .filterIsInstance<ProjectDependency>()
                    .map { it.path }
            }
            .distinct()
        if (projectEdges.any { it == ":app" }) {
            offenders += ":samsung depends on :app (forbidden edge, modules.md)"
        }

        if (offenders.isNotEmpty()) {
            throw GradleException(
                "samsungDependencyBoundary failed. Local control never needs the cloud " +
                    "(docs/architecture/modules.md#dependency-set-boundaries).\n" +
                    offenders.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("samsungDependencyBoundary: OK — :samsung graph is clean.")
    }
}

// ---------------------------------------------------------------------------
// noProductionModuleDependsOnBenchmark
// ---------------------------------------------------------------------------

tasks.register("noProductionModuleDependsOnBenchmark") {
    group = "verification"
    description =
        "Fails if a production module depends on :macrobenchmark, or if benchmark " +
            "code could reach a shipped artifact."
    doLast {
        val offenders = mutableListOf<String>()

        listOf(":app", ":samsung").forEach { path ->
            val productionProject = project(path)
            productionProject.configurations.forEach { configuration ->
                configuration.dependencies
                    .filterIsInstance<ProjectDependency>()
                    .filter { it.path == ":macrobenchmark" }
                    .forEach { offenders += "$path:${configuration.name} depends on :macrobenchmark" }
            }
        }

        // The benchmark module must remain a com.android.test module: such a
        // module produces no publishable/shippable artifact and is never a
        // dependency of an application or library module.
        val benchmark = project(":macrobenchmark")
        if (!benchmark.plugins.hasPlugin("com.android.test")) {
            offenders += ":macrobenchmark must apply com.android.test so it cannot be shipped"
        }

        if (offenders.isNotEmpty()) {
            throw GradleException(
                "noProductionModuleDependsOnBenchmark failed " +
                    "(docs/architecture/modules.md#shape).\n" +
                    offenders.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle(
            "noProductionModuleDependsOnBenchmark: OK — :macrobenchmark is test-only and unreferenced " +
                "by production modules.",
        )
    }
}

// ---------------------------------------------------------------------------
// Source-level guards
// ---------------------------------------------------------------------------

fun productionKotlinSources(project: Project): List<File> {
    val main = project.file("src/main")
    if (!main.exists()) return emptyList()
    return main.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
}

tasks.register("noLogInSamsungSource") {
    group = "verification"
    description = "Fails if :samsung production source calls android.util.Log directly."
    doLast {
        val logCall = Regex("""(^|[^\w.])Log\s*\.\s*(v|d|i|w|e|wtf|println)\s*\(|android\.util\.Log""")
        val offenders = productionKotlinSources(project(":samsung")).flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> logCall.containsMatchIn(line) }
                .map { (index, line) -> "${file.relativeTo(rootDir)}:${index + 1}: ${line.trim()}" }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "noLogInSamsungSource failed. Tokens must not reach logcat by accident " +
                    "(docs/architecture/release.md supply-chain checks).\n" +
                    offenders.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("noLogInSamsungSource: OK — no direct android.util.Log call in :samsung.")
    }
}

tasks.register("noSyncRecordInProductionSource") {
    group = "verification"
    description =
        "Fails if production source declares a sync record, tombstone or mutation queue."
    doLast {
        val forbidden = listOf(
            Regex("""\b(class|interface|object|enum class|data class)\s+\w*SyncRecord\w*"""),
            Regex("""\b(class|interface|object|enum class|data class)\s+\w*Tombstone\w*"""),
            Regex("""\b(class|interface|object|enum class|data class)\s+\w*MutationQueue\w*"""),
        )
        val offenders = listOf(":app", ":samsung").flatMap { path ->
            productionKotlinSources(project(path)).flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> forbidden.any { it.containsMatchIn(line) } }
                    .map { (index, line) -> "${file.relativeTo(rootDir)}:${index + 1}: ${line.trim()}" }
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "noSyncRecordInProductionSource failed. No television or personalization data " +
                    "is ever synchronized (docs/architecture/sync.md).\n" +
                    offenders.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("noSyncRecordInProductionSource: OK — no sync record, tombstone or mutation queue.")
    }
}

// ---------------------------------------------------------------------------
// Version-catalog hygiene: no `+`, dynamic or snapshot versions
// ---------------------------------------------------------------------------

tasks.register("versionCatalogPinned") {
    group = "verification"
    description = "Fails if the version catalog declares a dynamic, `+` or snapshot version."
    val catalog = rootProject.file("gradle/libs.versions.toml")
    doLast {
        val offenders = catalog.readLines().withIndex()
            .filterNot { (_, line) -> line.trimStart().startsWith("#") }
            .filter { (_, line) ->
                val versionValues = Regex("""(?:version(?:\.ref)?\s*=\s*|=\s*)"([^"]*)"""")
                    .findAll(line).map { it.groupValues[1] }.toList()
                versionValues.any { value ->
                    value.endsWith("+") ||
                        value.contains("SNAPSHOT", ignoreCase = true) ||
                        value.contains("latest", ignoreCase = true) ||
                        Regex("""^\[|^\(|,\s*\)$|,\s*]$""").containsMatchIn(value)
                }
            }
            .map { (index, line) -> "gradle/libs.versions.toml:${index + 1}: ${line.trim()}" }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "versionCatalogPinned failed. No `+` ranges, no snapshots, no dynamic versions " +
                    "(docs/architecture/release.md#gradle).\n" +
                    offenders.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("versionCatalogPinned: OK — every declared version is exact.")
    }
}

// ---------------------------------------------------------------------------
// dependencyLockCheck
// ---------------------------------------------------------------------------

// `dependencyLockCheck` is the repository-level verification the S01 route
// invokes. It resolves every locked configuration of every module against the
// committed lockfiles. With locking enabled and no `--write-locks`, resolution
// itself fails on drift, so this task's job is to force that resolution.
tasks.register("dependencyLockCheck") {
    group = "verification"
    description =
        "Resolves all locked configurations against the committed lockfiles and fails on drift."
    dependsOn(tasks.named("versionCatalogPinned"))
    doLast {
        var resolved = 0
        val failures = mutableListOf<String>()
        subprojects.forEach { subproject ->
            subproject.configurations
                .filter { it.isCanBeResolved }
                .filter { it.name.contains("RuntimeClasspath") || it.name.contains("CompileClasspath") }
                .forEach { configuration ->
                    // Resolving the graph forces lock-state validation, so a
                    // drifted or missing lockfile surfaces here rather than being
                    // deferred to a later task. Deliberately NOT `files`: that also
                    // forces artifact selection, which is ambiguous for
                    // configurations seeing several variants of `:app` and would
                    // fail for a reason unrelated to the lock state.
                    runCatching { configuration.incoming.resolutionResult.root }
                        .onSuccess { resolved++ }
                        .onFailure { failure ->
                            failures += "${subproject.path}:${configuration.name}: ${failure.message}"
                        }
                }
        }
        if (failures.isNotEmpty()) {
            throw GradleException(
                "dependencyLockCheck failed. Lockfiles are committed and must match the " +
                    "resolved graph (docs/architecture/release.md#gradle); refresh them with " +
                    "`./gradlew resolveAndLockAll --write-locks` as a reviewed change.\n" +
                    failures.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("dependencyLockCheck: OK — $resolved locked configurations match the committed lockfiles.")
    }
}

// ---------------------------------------------------------------------------
// Aggregate: everything CI runs as the S01 guard floor
// ---------------------------------------------------------------------------

tasks.register("appTGuards") {
    group = "verification"
    description = "Runs the whole S01 privacy, supply-chain and module guard floor."
    dependsOn(
        tasks.named("versionCatalogPinned"),
        tasks.named("noTelemetryDependency"),
        tasks.named("noFirestoreClientInApp"),
        tasks.named("noCrashReportingInApp"),
        tasks.named("samsungDependencyBoundary"),
        tasks.named("noProductionModuleDependsOnBenchmark"),
        tasks.named("noLogInSamsungSource"),
        tasks.named("noSyncRecordInProductionSource"),
    )
}

gradle.projectsEvaluated {
    // Manifest guards live in :app (they read AGP's MERGED_MANIFEST artifact).
    // Re-expose them under their accepted names at the repository root.
    listOf("adIdAbsentFromManifest", "manifestPermissionAllowlist").forEach { name ->
        val appTask = project(":app").tasks.findByName(name) ?: return@forEach
        tasks.register(name) {
            group = "verification"
            description = appTask.description
            dependsOn(appTask)
        }
        tasks.named("appTGuards") { dependsOn(appTask) }
    }
}
