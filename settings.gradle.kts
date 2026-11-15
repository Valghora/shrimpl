pluginManagement {
    repositories {
        google()
        // Google's Maven Central mirror first (avoids Central 429 rate limits through the proxy); Central stays as fallback.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Shevery's API (R6.1), published only on its own repository; limited to its group, so that
        // repository can't supply any other dependency.
        exclusiveContent {
            forRepository { maven("https://raw.githubusercontent.com/HmnDev-Tech/Shevery-API/maven") }
            filter { includeGroup("com.hamondev.shevery.api") }
        }
        google()
        // Google's Maven Central mirror first (avoids Central 429 rate limits through the proxy); Central stays as fallback.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "shrimpl"
include(":core")
include(":template")
include(":shrimpl")
