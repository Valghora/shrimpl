// The web app template (docs/SHRIMPLE_SPEC.md R7): a site-neutral APK that Shrimple patches per web
// app. It reads its site from assets/shrimple/profile.json and has placeholder name and icons at fixed
// paths and the superset manifest.
plugins {
    alias(libs.plugins.android.application)
}

/** shrimple.templateVersion in gradle.properties; Shrimple reads it to offer updates (R11.4). */
val templateVersion = providers.gradleProperty("shrimple.templateVersion").get().toInt()

// Release builds are signed with the local debug key; Shrimple re-signs every web app.
android {
    // Fixed for every web app, so the classes keep their names when Shrimple renames the package (R7.6).
    namespace = "com.valghora.shrimple.webapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.valghora.shrimple.template"
        minSdk = 37
        targetSdk = 37
        versionCode = templateVersion
        versionName = "$templateVersion.0"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Unit tests check the profile this build ships.
tasks.withType<Test>().configureEach {
    val profile = layout.projectDirectory.file("src/main/assets/shrimple/profile.json")
    inputs.file(profile).withPropertyName("profileJson")
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-DprofileJson=${profile.asFile.path}") })
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.webkit)
    testImplementation(libs.junit)
}
