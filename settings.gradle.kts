rootProject.name = "haulio-monorepo"

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

include(":android:app")
project(":android:app").projectDir = file("android/app")

include(":shared")
project(":shared").projectDir = file("shared")