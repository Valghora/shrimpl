import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure-JVM logic shared by the web app template and UniWrap (docs/UNIWRAP_SPEC.md R4).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    jacoco
}

java {
    // The Android modules compile for Java 17; keep this library's bytecode consumable by them.
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    implementation(libs.apksig)
    testImplementation(libs.junit)
}

// The builder tests patch the real template APK (docs/UNIWRAP_SPEC.md R8.5), so it is built first.
// A -Psite build would produce a site APK instead of the neutral template, so the two don't mix.
val templateApk = rootProject.layout.projectDirectory.file("template/build/outputs/apk/release/template-release.apk")
val siteBuild = providers.gradleProperty("site").orNull?.isNotBlank() == true

tasks.test {
    dependsOn(":template:assembleRelease")
    inputs.file(templateApk).withPropertyName("templateApk")
    systemProperty("templateApk", templateApk.asFile.path)
    val rejectSiteBuild = siteBuild // a local copy: the configuration cache can't hold script references
    doFirst {
        if (rejectSiteBuild) throw GradleException(":core:test needs the neutral template; run it without -Psite")
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
