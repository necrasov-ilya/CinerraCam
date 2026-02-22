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

rootProject.name = "CinerraCam"
include(
    ":app",
    ":core",
    ":camera2",
    ":pipeline",
    ":storage-dng",
    ":native-writer",
    ":benchmark"
)
