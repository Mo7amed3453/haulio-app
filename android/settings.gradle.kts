pluginManagement {
    repositories {
        google()
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

rootProject.name = "haulio-android"

include(":app")
// TODO: Re-enable when KMM shared module integration is wired
// include(":shared")
// project(":shared").projectDir = File(settingsDir, "../shared")
