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
        maven("https://artifact.esper.io/artifactory/esper-device-sdk/")
    }
}

rootProject.name = "Shared device"
include(":app")
include(":supervisorpulse")
include(":supervisorwalk")
include(":associatepick")
include(":associatescan")
