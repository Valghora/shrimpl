// The web app template (docs/SHRIMPL_SPEC.md R7): a site-neutral APK that Shrimpl patches per web
// app. It reads its site from assets/shrimpl/profile.json and has placeholder name and icons at fixed
// paths and the superset manifest.
plugins {
    alias(libs.plugins.android.application)
}

/** shrimpl.templateVersion in gradle.properties; Shrimpl reads it to offer updates (R11.4). */
val templateVersion = providers.gradleProperty("shrimpl.templateVersion").get().toInt()

// Release builds are signed with the local debug key; Shrimpl re-signs every web app.
android {
    // Fixed for every web app, so the classes keep their names when Shrimpl renames the package (R7.6).
    namespace = "com.valghora.shrimpl.webapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.valghora.shrimpl.template"
        minSdk = 37
        targetSdk = 37
        versionCode = templateVersion
        versionName = "$templateVersion.0"
        // Instrumented tests run on an emulator in CI (docs/TESTING.md, "The emulator's environment").
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            // R8 code and resource optimization with the optimized default rules (r8-analyzer skill,
            // AGP 9.3+ DSL). The template needs no keep rules of its own.
            optimization {
                enable = true
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Unit tests check the profile this build ships.
tasks.withType<Test>().configureEach {
    val profile = layout.projectDirectory.file("src/main/assets/shrimpl/profile.json")
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
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
}
