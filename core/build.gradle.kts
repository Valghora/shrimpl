import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure-JVM logic shared by the web app template and Shrimpl (docs/SHRIMPL_SPEC.md R4).
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

// The builder tests patch the real template APK (docs/SHRIMPL_SPEC.md R8.5), so it is built first.
val templateApk = rootProject.layout.projectDirectory.file("template/build/outputs/apk/release/template-release.apk")

tasks.test {
    dependsOn(":template:assembleRelease")
    inputs.file(templateApk).withPropertyName("templateApk")
    systemProperty("templateApk", templateApk.asFile.path)
    // The version the template APK must carry, so a template version bump needs no test edits.
    systemProperty("templateVersion", rootProject.extra["templateVersionCode"] as Int)
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
                minimum = "0.95".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
