// UniWrap: makes web apps on the device (docs/UNIWRAP_SPEC.md).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
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
    namespace = "com.valghora.uniwrap"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.valghora.uniwrap"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures {
        aidl = true // the Shizuku shell service (R6.1)
        buildConfig = true // the version on the settings page
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // R12.3: debug key unless a release key is configured (added with the CI work in Phase 6).
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
            templateVersion.set(providers.gradleProperty("uniwrap.templateVersion").map(String::toInt))
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
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    testImplementation(libs.junit)
}
