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

// Placeholder until the app is scaffolded: Phase 1 sets this to APP_ID and adds include(":app").
rootProject.name = "wrapper"
