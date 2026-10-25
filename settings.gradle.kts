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
        google()
        // Google's Maven Central mirror first (avoids Central 429 rate limits through the proxy); Central stays as fallback.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "universal-android-wrapper"
include(":core")
include(":app")
// Proof of concept: clone an APK on the device (docs/POC_CLONER.md).
include(":cloner")
