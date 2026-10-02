// Repository and module graph for AppT.
//
// docs/architecture/release.md#gradle owns these rules:
//   * dependencyResolutionManagement.repositoriesMode = FAIL_ON_PROJECT_REPOS
//   * repositories are google() and mavenCentral() only
//   * the plugin portal appears only under pluginManagement
//   * no JitPack and no ad-hoc Maven URLs

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AppT"

// Exactly two production Gradle modules (docs/architecture/modules.md#shape):
//   :app     Android application
//   :samsung Samsung control module
include(":app")

include(":samsung")
