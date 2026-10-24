plugins {
    alias(libs.plugins.android.application)
    jacoco
}

android {
    namespace = "com.valghora.pwa.discord"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.valghora.pwa.discord"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "1.1.0"
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            // R1.6: personal sideloading only; never for store distribution.
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

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.webkit)
    testImplementation(libs.junit)
}

// JaCoCo (R2.5): unit-test line coverage of the pure-JVM core package only.
val corePackagePath = "com/valghora/pwa/discord/core/**"
val coreClasses = fileTree(layout.buildDirectory) {
    include("intermediates/built_in_kotlinc/debug/**/$corePackagePath")
    exclude("**/R.class", "**/R$*.class", "**/BuildConfig.*")
}
val coverageData = fileTree(layout.buildDirectory) {
    include("outputs/unit_test_code_coverage/debugUnitTest/*.exec")
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coreClasses)
    sourceDirectories.setFrom("src/main/java")
    executionData.setFrom(coverageData)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coreClasses)
    executionData.setFrom(coverageData)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}
