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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Ghar Bazaar"
include(
    ":app",
    ":domain",
    ":shell",
    ":a2ui-catalog",
    ":a2ui-runtime",
    ":feature-onboarding",
    ":feature-mealplan",
    ":feature-grocery",
    ":data-local",
    ":data-remote",
    ":sync",
    ":localization",
)
