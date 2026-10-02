buildscript {
    repositories { mavenCentral() }
    dependencies {
        constraints {
            classpath("org.bouncycastle:bcprov-jdk18on:1.86")
            classpath("org.bouncycastle:bcpkix-jdk18on:1.86")
            classpath("org.apache.commons:commons-lang3:3.20.0")
            classpath("org.apache.httpcomponents:httpclient:4.5.14")
            classpath("org.bitbucket.b_c:jose4j:0.9.6")
            classpath("org.eclipse.jgit:org.eclipse.jgit:6.10.1.202505221210-r")
            classpath("org.jdom:jdom2:2.0.6.1")
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktfmt().kotlinlangStyle()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktfmt().kotlinlangStyle()
    }
}

dependencies {
    kover(project(":app"))
    kover(project(":samsung"))
}

apply(from = rootProject.file("gradle/guards.gradle.kts"))
apply(from = rootProject.file("gradle/format-probe.gradle.kts"))

// TEMPORARY — Issue #145 S06–S17 program diagnostics.
//
// detekt's own failure message carries only a finding count, and the hosted diagnostic jobs do not
// expose the uploaded report artifacts to the agent that is fixing the findings. Emit each finding
// as a GitHub workflow command so a failing static-analysis run names its findings in the run's
// annotations. This changes no failure semantics: detekt still fails the run exactly as before.
// Remove before the program candidate is verified.
val reportDetektFindings =
    tasks.register("reportDetektFindings") {
        group = "verification"
        description = "TEMPORARY: prints detekt findings as GitHub workflow commands."
        doLast {
            val root = rootProject.projectDir
            val filePattern =
                Regex("<file name=\"([^\"]+)\">(.*?)</file>", RegexOption.DOT_MATCHES_ALL)
            val errorPattern =
                Regex(
                    "<error line=\"(\\d+)\" column=\"(\\d+)\" severity=\"[^\"]*\" " +
                        "message=\"([^\"]*)\" source=\"([^\"]*)\""
                )
            // Enough findings to name the problem without flooding the annotation list.
            val maxReported = 25
            var reported = 0
            listOf("app", "samsung")
                .map { File(root, "$it/build/reports/detekt/detekt.xml") }
                .filter { it.isFile }
                .forEach { report ->
                    filePattern.findAll(report.readText()).forEach { fileMatch ->
                        val path = File(fileMatch.groupValues[1]).relativeToOrSelf(root).path
                        errorPattern.findAll(fileMatch.groupValues[2]).forEach { finding ->
                            val line = finding.groupValues[1]
                            val column = finding.groupValues[2]
                            val message = finding.groupValues[3].replace('\n', ' ')
                            val rule = finding.groupValues[4]
                            reported++
                            if (reported <= maxReported) {
                                println(
                                    "::error file=$path,line=$line,col=$column::$message [$rule]"
                                )
                            }
                        }
                    }
                }
            println(
                "::notice title=detekt findings::$reported finding(s) in the module reports; " +
                    "at most $maxReported are emitted as annotations."
            )
        }
    }

// TEMPORARY — Issue #145 S06–S17 program diagnostics.
//
// The hosted diagnostic jobs report only "There were failing tests" and do not expose the test
// report artifact to the agent fixing them. Emit each failing test as a GitHub workflow command so
// the failure annotation names the test, its assertion message and its first source frame. Remove
// before the program candidate is verified.
val reportTestFailures =
    tasks.register("reportTestFailures") {
        group = "verification"
        description = "TEMPORARY: prints unit-test failures as GitHub workflow commands."
        doLast {
            val maxReported = 25
            var reported = 0
            val failurePattern =
                Regex(
                    "<testcase name=\"([^\"]*)\" classname=\"([^\"]*)\"[^>]*>\\s*" +
                        "<failure message=\"([^\"]*)\"[^>]*>([^<]*)",
                    RegexOption.DOT_MATCHES_ALL,
                )
            val framePattern = Regex("\\(([A-Za-z0-9_]+\\.kt):(\\d+)\\)")
            fileTree(rootProject.projectDir) { include("*/build/test-results/**/*.xml") }
                .files
                .sorted()
                .forEach { report ->
                    val text =
                        try {
                            report.readText()
                        } catch (unreadable: Exception) {
                            println("::warning title=test reports::unreadable ${report.name}")
                            return@forEach
                        }
                    failurePattern.findAll(text).forEach { failure ->
                        val name = failure.groupValues[1]
                        val klass = failure.groupValues[2]
                        val message =
                            failure.groupValues[3]
                                .replace("&#10;", " ")
                                .replace("&quot;", "'")
                                .replace("&apos;", "'")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .replace("&amp;", "&")
                                .replace('\n', ' ')
                        val frame = framePattern.find(failure.groupValues[4])?.value.orEmpty()
                        reported++
                        if (reported <= maxReported) {
                            println(
                                "::error title=$klass.$name::$message $frame (in " +
                                    report.name +
                                    ")"
                            )
                        }
                    }
                }
            println(
                "::notice title=test failures::$reported failing test(s); at most $maxReported are " +
                    "emitted as annotations."
            )
        }
    }

subprojects {
    tasks.matching { it.name == "detekt" }.configureEach { finalizedBy(reportDetektFindings) }
    tasks
        .matching { it.name == "test" || it.name == "testDebugUnitTest" }
        .configureEach { finalizedBy(reportTestFailures) }
}

allprojects { dependencyLocking { lockAllConfigurations() } }

subprojects {
    tasks.register("resolveAndLockAll") {
        group = "verification"
        description =
            "Resolves this module's locked configurations so --write-locks can refresh the lockfiles."
        notCompatibleWithConfigurationCache("Writes lockfiles during resolution")
        doFirst {
            require(gradle.startParameter.isWriteDependencyLocks) {
                "resolveAndLockAll must be run with --write-locks"
            }
        }
        doLast {
            configurations
                .filter { it.isCanBeResolved }
                .forEach { configuration -> configuration.incoming.resolutionResult.root }
        }
    }
}

tasks.register("androidFormat") {
    group = "verification"
    description = "Android formatting responsibility: deterministic Spotless + ktfmt checking."
    dependsOn("spotlessCheck")
}

tasks.register("androidStatic") {
    group = "verification"
    description = "Android static and policy verification: Android Lint, detekt, appTGuards."
    dependsOn(
        ":app:lintDebug",
        ":samsung:lintDebug",
        ":app:detekt",
        ":samsung:detekt",
        "appTGuards",
    )
}

tasks.register("androidBuild") {
    group = "verification"
    description = "Android build: debug APK assembly and the release engineering-exclusion guard."
    dependsOn(":app:assembleDebug", ":app:verifyReleaseEngineeringBoundaries")
}

tasks.register("androidUnit") {
    group = "verification"
    description =
        "Android unit/Robolectric verification for :app and :samsung, plus Kover XML coverage reporting."
    dependsOn(":app:testDebugUnitTest", ":samsung:test")
}

tasks.register("ciCheck") {
    group = "verification"
    description =
        "Root Android/Kotlin verification interface aggregating the complete verification floor."
    dependsOn("androidFormat", "androidStatic", "androidBuild", "androidUnit")
}

gradle.projectsEvaluated {
    val koverXml =
        tasks.findByName("koverXmlReport")
            ?: project(":app").tasks.findByName("koverXmlReport")
            ?: tasks.findByName("koverXmlReportDebug")
            ?: project(":app").tasks.findByName("koverXmlReportDebug")

    tasks.named("androidUnit") { koverXml?.let { dependsOn(it) } }
}
