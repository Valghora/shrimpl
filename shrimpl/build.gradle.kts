// Shrimpl: makes web apps on the device (docs/SHRIMPL_SPEC.md).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    jacoco
}

/** Copies the neutral template release APK and its version into the generated assets (R8.1). */
abstract class BundleTemplate : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val templateApk: RegularFileProperty

    @get:Input
    abstract val templateVersion: Property<Int>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val out = outputDir.get().asFile.resolve("template")
        out.deleteRecursively()
        out.mkdirs()
        templateApk.get().asFile.copyTo(out.resolve("template.apk"))
        out.resolve("version").writeText(templateVersion.get().toString())
    }
}

val neutralTemplateApk: RegularFile =
    rootProject.layout.projectDirectory.file("template/build/outputs/apk/release/template-release.apk")

android {
    namespace = "com.valghora.shrimpl"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.valghora.shrimpl"
        minSdk = 37
        targetSdk = 37
        // R11.8: shrimpl.appVersion, as its code; the code only ever rises, so Shrimpl always updates in place.
        versionCode = rootProject.extra["shrimplVersionCode"] as Int
        versionName = providers.gradleProperty("shrimpl.appVersion").get()
        // Instrumented tests run on an emulator in CI (docs/TESTING.md, "The emulator's environment").
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        // Robolectric runs Shrimpl's Compose screens in unit tests (docs/TESTING.md, "Robolectric").
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        aidl = true // the Shizuku shell service (R6.1)
        buildConfig = true // the version on the settings page
    }

    buildTypes {
        debug {
            // Line coverage of the unit and Robolectric tests (testing-setup skill):
            // :shrimpl:createDebugUnitTestCoverageReport writes build/reports/coverage/test/debug.
            enableUnitTestCoverage = true
        }
        release {
            // R8 code and resource optimization with the optimized default rules (r8-analyzer skill,
            // AGP 9.3+ DSL); Shrimpl's own keep rules are in src/main/keepRules.
            optimization {
                enable = true
            }
            // R12.3: signed with the debug key, for personal sideloading.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        val bundle = tasks.register<BundleTemplate>("bundle${variant.name.replaceFirstChar { it.uppercase() }}Template") {
            dependsOn(":template:assembleRelease")
            templateApk.set(neutralTemplateApk)
            templateVersion.set(rootProject.extra["templateVersionCode"] as Int)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(bundle, BundleTemplate::outputDir)
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    // Only for MaterialColors.harmonize, the Harmony badge theme (docs/SHRIMPL_SPEC.md R2.5).
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
}

// Robolectric's Android runtime comes through Gradle (cached, retried, the same repositories as every
// other dependency) and the tests run Robolectric offline against it, so they never download at test
// time (docs/TESTING.md, "Robolectric").
val robolectricRuntime: Configuration by configurations.creating
dependencies { robolectricRuntime(libs.robolectric.android.all) { isTransitive = false } }
val robolectricRuntimeDir = layout.buildDirectory.dir("robolectric-runtime")
val prepareRobolectricRuntime = tasks.register<Sync>("prepareRobolectricRuntime") {
    from(robolectricRuntime)
    into(robolectricRuntimeDir)
}
tasks.withType<Test>().configureEach {
    dependsOn(prepareRobolectricRuntime)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricRuntimeDir.get().asFile.absolutePath)
    // Robolectric reaches into these JDK internals to emulate Android's file descriptors (Java 17+).
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
    // Coverage of classes Robolectric loads through its own class loader, without the JDK's internals.
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}
