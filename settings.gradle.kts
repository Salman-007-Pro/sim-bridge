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

rootProject.name = "SimBridge"

include(
    ":apps:host",
    ":apps:client",
    ":core:model",
    ":core:protocol",
    ":core:crypto",
    ":core:transport",
    ":core:storage",
    ":core:ui",
    ":core:media",
    ":core:testing",
)
