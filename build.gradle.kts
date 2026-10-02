
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
    description = "Android build: debug APK assembly and release S05 exclusion guard."
    dependsOn(":app:assembleDebug", ":app:verifyReleaseS05Boundaries")
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
