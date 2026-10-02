/** Crash-reporting, analytics, advertising and attribution artifacts. */
val telemetryArtifactPatterns: List<String> =
    listOf(
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

/** Firestore client artifacts. Firestore is server-only (account-entitlement.md). */
val firestoreArtifactPatterns: List<String> =
    listOf("firebase-firestore", "google-cloud-firestore", "firestore")

/** Everything `:samsung` must stay clear of (modules.md forbidden edges). */
val samsungForbiddenArtifactPatterns: List<String> =
    telemetryArtifactPatterns +
        firestoreArtifactPatterns +
        listOf("firebase-", "com.google.firebase", "play-services-", "billing", "integrity")

fun Project.resolvedArtifactIds(configurationName: String): List<String> {
    val configuration =
        configurations.findByName(configurationName)
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

val noTelemetryInstances =
    listOf(":app").map { path ->
        val target = project(path)
        target.tasks.register("noTelemetryDependency") {
            group = "verification"
            description =
                "Fails if any crash-reporting, analytics, advertising or attribution " +
                    "artifact appears on $path's production dependency graph."
            doLast {
                val offenders =
                    target
                        .resolvedArtifactIds("debugRuntimeClasspath")
                        .filter { matches(it, telemetryArtifactPatterns) }
                        .map { "$path/debugRuntimeClasspath -> $it" }
                if (offenders.isNotEmpty()) {
                    throw GradleException(
                        "noTelemetryDependency failed. V1 ships no crash-reporting, analytics, " +
                            "advertising or attribution SDK (docs/architecture/diagnostics.md).\n" +
                            offenders.joinToString("\n") { "  $it" }
                    )
                }
                logger.lifecycle(
                    "noTelemetryDependency ($path): OK — no telemetry artifact in the production graph."
                )
            }
        }
    }

tasks.register("noTelemetryDependency") {
    group = "verification"
    description =
        "Fails if any crash-reporting, analytics, advertising or attribution " +
            "artifact appears in the merged production dependency graph."
    dependsOn(noTelemetryInstances)
}

val noFirestoreClientInAppInstance =
    project(":app").tasks.register("noFirestoreClientInApp") {
        group = "verification"
        description = "Fails if a Firestore client artifact appears on :app's runtime graph."
        doLast {
            val offenders =
                project(":app").resolvedArtifactIds("debugRuntimeClasspath").filter {
                    matches(it, firestoreArtifactPatterns)
                }
            if (offenders.isNotEmpty()) {
                throw GradleException(
                    "noFirestoreClientInApp failed. Firestore is server-only; the client never " +
                        "talks to Firestore directly (docs/architecture/account-entitlement.md).\n" +
                        offenders.joinToString("\n") { "  $it" }
                )
            }
            logger.lifecycle("noFirestoreClientInApp: OK — no Firestore client artifact on :app.")
        }
    }

tasks.register("noFirestoreClientInApp") {
    group = "verification"
    description = "Fails if a Firestore client artifact appears on :app's runtime graph."
    dependsOn(noFirestoreClientInAppInstance)
}

val samsungDependencyBoundaryInstance =
    project(":samsung").tasks.register("samsungDependencyBoundary") {
        group = "verification"
        description =
            "Fails if :samsung gains a Firebase, Play services or telemetry dependency, " +
                "or a dependency on :app."
        doLast {
            val offenders =
                project(":samsung")
                    .resolvedArtifactIds("debugRuntimeClasspath")
                    .filter { matches(it, samsungForbiddenArtifactPatterns) }
                    .toMutableList()

            val projectEdges =
                project(":samsung")
                    .configurations
                    .filter {
                        it.name.endsWith("RuntimeClasspath") || it.name.endsWith("CompileClasspath")
                    }
                    .flatMap { configuration ->
                        configuration.dependencies.filterIsInstance<ProjectDependency>().map {
                            it.path
                        }
                    }
                    .distinct()
            if (projectEdges.any { it == ":app" }) {
                offenders += ":samsung depends on :app (forbidden edge, modules.md)"
            }

            if (offenders.isNotEmpty()) {
                throw GradleException(
                    "samsungDependencyBoundary failed. Local control never needs the cloud " +
                        "(docs/architecture/modules.md#dependency-set-boundaries).\n" +
                        offenders.joinToString("\n") { "  $it" }
                )
            }
            logger.lifecycle("samsungDependencyBoundary: OK — :samsung graph is clean.")
        }
    }

tasks.register("samsungDependencyBoundary") {
    group = "verification"
    description =
        "Fails if :samsung gains a Firebase, Play services or telemetry dependency, " +
            "or a dependency on :app."
    dependsOn(samsungDependencyBoundaryInstance)
}

tasks.register("samsungGraphExcludesFirebase") {
    group = "verification"
    description =
        "Gradle dependencies of :samsung exclude Firebase, Play services and every telemetry SDK " +
            "(docs/architecture/testing.md)."
    dependsOn(samsungDependencyBoundaryInstance)
}

fun productionKotlinSources(project: Project): List<File> {
    val main = project.file("src/main")
    if (!main.exists()) return emptyList()
    return main
        .walkTopDown()
        .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
        .toList()
}

tasks.register("noLogInSamsungSource") {
    group = "verification"
    description = "Fails if :samsung production source calls android.util.Log directly."
    doLast {
        val logCall =
            Regex("""(^|[^\w.])Log\s*\.\s*(v|d|i|w|e|wtf|println)\s*\(|android\.util\.Log""")
        val offenders =
            productionKotlinSources(project(":samsung")).flatMap { file ->
                file
                    .readLines()
                    .withIndex()
                    .filter { (_, line) -> logCall.containsMatchIn(line) }
                    .map { (index, line) ->
                        "${file.relativeTo(rootDir)}:${index + 1}: ${line.trim()}"
                    }
            }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "noLogInSamsungSource failed. Tokens must not reach logcat by accident " +
                    "(docs/architecture/release.md supply-chain checks).\n" +
                    offenders.joinToString("\n") { "  $it" }
            )
        }
        logger.lifecycle("noLogInSamsungSource: OK — no direct android.util.Log call in :samsung.")
    }
}

tasks.register("noSyncRecordInProductionSource") {
    group = "verification"
    description = "Fails if production source declares a sync record, tombstone or mutation queue."
    doLast {
        val forbidden =
            listOf(
                Regex("""\b(class|interface|object|enum class|data class)\s+\w*SyncRecord\w*"""),
                Regex("""\b(class|interface|object|enum class|data class)\s+\w*Tombstone\w*"""),
                Regex("""\b(class|interface|object|enum class|data class)\s+\w*MutationQueue\w*"""),
            )
        val offenders =
            listOf(":app", ":samsung").flatMap { path ->
                productionKotlinSources(project(path)).flatMap { file ->
                    file
                        .readLines()
                        .withIndex()
                        .filter { (_, line) -> forbidden.any { it.containsMatchIn(line) } }
                        .map { (index, line) ->
                            "${file.relativeTo(rootDir)}:${index + 1}: ${line.trim()}"
                        }
                }
            }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "noSyncRecordInProductionSource failed. No television or personalization data " +
                    "is ever synchronized (docs/architecture/account-entitlement.md).\n" +
                    offenders.joinToString("\n") { "  $it" }
            )
        }
        logger.lifecycle(
            "noSyncRecordInProductionSource: OK — no sync record, tombstone or mutation queue."
        )
    }
}

tasks.register("versionCatalogPinned") {
    group = "verification"
    description = "Fails if the version catalog declares a dynamic, `+` or snapshot version."
    val catalog = rootProject.file("gradle/libs.versions.toml")
    doLast {
        val offenders =
            catalog
                .readLines()
                .withIndex()
                .filterNot { (_, line) -> line.trimStart().startsWith("#") }
                .filter { (_, line) ->
                    val versionValues =
                        Regex("""(?:version(?:\.ref)?\s*=\s*|=)"([^"]*)"""")
                            .findAll(line)
                            .map { it.groupValues[1] }
                            .toList()
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
                    offenders.joinToString("\n") { "  $it" }
            )
        }
        logger.lifecycle("versionCatalogPinned: OK — every declared version is exact.")
    }
}

tasks.register("appTGuards") {
    group = "verification"
    description = "Runs the whole S01 privacy, supply-chain and module guard floor."
    dependsOn(
        tasks.named("versionCatalogPinned"),
        tasks.named("noTelemetryDependency"),
        tasks.named("noFirestoreClientInApp"),
        tasks.named("samsungDependencyBoundary"),
        tasks.named("samsungGraphExcludesFirebase"),
        tasks.named("noLogInSamsungSource"),
        tasks.named("noSyncRecordInProductionSource"),
    )
}

gradle.projectsEvaluated {
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
