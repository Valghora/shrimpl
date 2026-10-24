import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.application)
    jacoco
}

// ---------- Site selection ----------
// The template builds one site at a time: sites/<id>/site.toml layered over sites/defaults.toml.
// Choose it with -Psite=<id> (default: the `site` property in gradle.properties).
val selectedSite: String = providers.gradleProperty("site").get()
val siteConfigScript = rootProject.file("scripts/site_config.py")

val siteProperties: Map<String, String> = run {
    val result = providers.exec {
        commandLine("python3", siteConfigScript.path, "properties", "--site", selectedSite)
        isIgnoreExitValue = true
    }
    if (result.result.get().exitValue != 0) {
        throw GradleException("Site '$selectedSite' is not buildable:\n${result.standardError.asText.get().trim()}")
    }
    result.standardOutput.asText.get().lines()
        .filter { "=" in it }
        .associate { it.substringBefore("=") to it.substringAfter("=") }
}

fun site(key: String): String = siteProperties[key] ?: throw GradleException("site_config.py did not report '$key'")

/** Generates AppConfig.kt, site resources/icons and the manifest overlay for one variant. */
abstract class GenerateSiteSources @Inject constructor(private val execOps: ExecOperations) : DefaultTask() {
    @get:Input
    abstract val siteId: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configInputs: ConfigurableFileCollection

    @get:Internal
    abstract val script: RegularFileProperty

    @get:OutputDirectory
    abstract val kotlinDir: DirectoryProperty

    @get:OutputDirectory
    abstract val resDir: DirectoryProperty

    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    @TaskAction
    fun generate() {
        execOps.exec {
            commandLine(
                "python3", script.get().asFile.path, "generate",
                "--site", siteId.get(),
                "--kotlin-out", kotlinDir.get().asFile.path,
                "--res-out", resDir.get().asFile.path,
                "--manifest-out", manifestFile.get().asFile.path,
            )
        }
    }
}

// Sideload signing key (optional): environment variables or ~/.gradle/gradle.properties.
// See scripts/create_sideload_key.sh. Without it, release builds use the local debug key.
fun sideload(env: String, prop: String): String? =
    providers.environmentVariable(env).orElse(providers.gradleProperty(prop)).orNull?.takeIf { it.isNotBlank() }

val sideloadKeystore = sideload("SIDELOAD_KEYSTORE", "sideload.keystore")

android {
    namespace = site("namespace")
    compileSdk = site("compileSdk").toInt()

    defaultConfig {
        applicationId = site("applicationId")
        minSdk = site("minSdk").toInt()
        targetSdk = site("targetSdk").toInt()
        versionCode = site("versionCode").toInt()
        versionName = site("versionName")
    }

    signingConfigs {
        if (sideloadKeystore != null) {
            create("sideload") {
                storeFile = file(sideloadKeystore)
                storePassword = sideload("SIDELOAD_KEYSTORE_PASSWORD", "sideload.storePassword")
                keyAlias = sideload("SIDELOAD_KEY_ALIAS", "sideload.keyAlias") ?: "sideload"
                keyPassword = sideload("SIDELOAD_KEY_PASSWORD", "sideload.keyPassword") ?: storePassword
            }
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            // R1.6: for personal sideloading only; never for store distribution.
            signingConfig = when (site("releaseSigning")) {
                "unsigned" -> null
                else -> signingConfigs.findByName("sideload") ?: signingConfigs.getByName("debug")
            }
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

androidComponents {
    onVariants { variant ->
        val generate = tasks.register<GenerateSiteSources>(
            "generate${variant.name.replaceFirstChar { it.uppercase() }}SiteSources",
        ) {
            siteId.set(selectedSite)
            script.set(siteConfigScript)
            configInputs.from(
                rootProject.fileTree("sites") { include("defaults.toml", "defaults.local.toml", "$selectedSite/**") },
                rootProject.files("scripts/site_config.py", "scripts/generate_icons.py", "scripts/letter_glyphs.json"),
            )
        }
        variant.sources.kotlin?.addGeneratedSourceDirectory(generate, GenerateSiteSources::kotlinDir)
        variant.sources.res?.addGeneratedSourceDirectory(generate, GenerateSiteSources::resDir)
        variant.sources.manifests.addGeneratedManifestFile(generate, GenerateSiteSources::manifestFile)
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
val corePackagePath = site("namespace").replace('.', '/') + "/core/**"
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
