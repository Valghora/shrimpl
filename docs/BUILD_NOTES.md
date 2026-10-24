# Build notes

Findings from setting up the infrastructure (2026-10-24). They apply to the upcoming spec run.

## Versions (latest stable at setup time)

| Component | Version | Source |
|---|---|---|
| Android Gradle Plugin | 9.4.1 | https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml |
| Gradle | 9.8.2 | https://services.gradle.org/versions/current |
| Kotlin | 2.4.21 | https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml |
| androidx.webkit | 1.18.0 | https://maven.google.com (androidx/webkit/webkit) |
| androidx.core:core-ktx | 1.19.1 | maven.google.com |
| androidx.appcompat | 1.8.0 | maven.google.com |
| androidx.activity:activity-ktx | 1.13.0 | maven.google.com |
| com.google.android.material | 1.14.0 | maven.google.com |
| junit | 4.13.2 | Maven Central |
| cmdline-tools | 16111833 | https://dl.google.com/android/repository/repository2-3.xml |
| Platform / build-tools | android-37.x / 37.0.0 | same |

The catalog in `gradle/libs.versions.toml` already contains exactly the R1.1 dependencies.
Before Phase 0 of the spec, confirm webkit 1.18.0 still provides UserAgentMetadata,
WebMessageListener and DocumentStartScript.

## Toolchain findings

- **sdkmanager is deprecated.** cmdline-tools 16111833 ships the `android` CLI
  (`android --sdk=$ANDROID_HOME sdk install|list`). `sdkmanager --licenses` is no longer needed.
  Package IDs use `/`, for example `platforms/android-37.0`.
- **API 37 platforms have minor versions** (android-37.0, 37.1, 37.2). `compileSdk = 37` builds
  against any of them; verified with both 37.0 and 37.2.
- **JDK:** only JDK 21 is installed, and no JDK 17. `jvmToolchain(17)` fails with "Cannot find a
  Java installation". Use `kotlin { jvmToolchain(21) }`, which satisfies the spec's "JDK 17+".
- **AGP 9 has built-in Kotlin.** Apply only `com.android.application`, never `kotlin-android`.
- **Maven Central rate limits.** Gradle's parallel downloads through the session proxy got
  HTTP 429 from repo.maven.apache.org. `settings.gradle.kts` puts Google's official Central mirror
  (`maven-central.storage-download.googleapis.com/maven2`) first, with `mavenCentral()` as fallback.

## JaCoCo on AGP 9 (R2.5): verified working

Kotlin classes compile to `build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes`,
not to the old `tmp/kotlin-classes/debug`. Unit-test coverage also has to be enabled on the debug
build type. Snippet tested with a throwaway app (replace the package path with CORE_PACKAGE):

```kotlin
plugins {
    alias(libs.plugins.android.application)
    jacoco
}

android {
    buildTypes {
        debug { enableUnitTestCoverage = true }
    }
}

val corePath = "com/example/app/core/**"   // CORE_PACKAGE as a path
val coreClasses = fileTree(layout.buildDirectory) {
    include("intermediates/built_in_kotlinc/debug/**/$corePath")
    exclude("**/R.class", "**/R$*.class", "**/BuildConfig.*")
}
val execData = fileTree(layout.buildDirectory) {
    include("outputs/unit_test_code_coverage/debugUnitTest/*.exec")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coreClasses)
    sourceDirectories.setFrom("src/main/java")
    executionData.setFrom(execData)
    reports { xml.required.set(true); html.required.set(true) }
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coreClasses)
    executionData.setFrom(execData)
    violationRules { rule { limit { counter = "LINE"; minimum = "0.85".toBigDecimal() } } }
}
```

## aapt2 37 output vs. spec Phase 6.4

- The min SDK line is `minSdkVersion:'37'`, not `sdkVersion:'37'`.
- Adaptive icons in `res/mipmap-anydpi/` are emitted as `res/mipmap-anydpi-v21/ic_launcher.xml`.
  The spec's regex `mipmap-anydpi(-v26)?` does not match that, so `ci_verify.sh` accepts any
  `-vNN` qualifier. Record this in the Deviations table for Phase 6.4.
- The `launchable-activity:` line only carries an icon if the activity itself sets
  `android:icon` (R4.5 requires that).

## Spec notes

- MIN_SDK 37 means Android 17+ only. Under R1.5 that rules out every `SDK_INT` guard, including
  for the caption-bar constants (API 35) and `PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI` (API 35).
- Lint warns `DataExtractionRules` on `allowBackup="false"` (it is a warning, not an error, so
  Phase 6.2 still passes). List it among the remaining warnings.
- No emulator or KVM is available in the cloud container, so Phase 6.8 (adb) cannot run here.
