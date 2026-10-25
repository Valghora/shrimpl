import javax.inject.Inject
import org.gradle.process.ExecOperations

// The web app template (docs/UNIWRAP_SPEC.md R7). It reads its site from assets/uniwrap/profile.json.
//
// Two build modes:
// - Template (default): a site-neutral APK for UniWrap to patch: placeholder name and icons at fixed
//   paths, the superset manifest, and a default profile (src/neutral/).
// - Site (-Psite=<id>, used by ./wrap and CI until UniWrap replaces them): the profile, resources,
//   icons and a manifest overlay are generated from sites/<id>/site.toml by scripts/site_config.py.
plugins {
    alias(libs.plugins.android.application)
}

/** Bump whenever template code or resources change; UniWrap uses it to offer updates (R11.4). */
val templateVersion = 1

val selectedSite: String? = providers.gradleProperty("site").orNull?.takeIf { it.isNotBlank() }
val siteConfigScript = rootProject.file("scripts/site_config.py")

val siteProperties: Map<String, String> = if (selectedSite == null) {
    mapOf(
        "applicationId" to "com.valghora.uniwrap.template",
        "versionCode" to "$templateVersion",
        "versionName" to "template-$templateVersion",
        "releaseSigning" to "auto",
    )
} else {
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

/** Generates the profile asset, site resources and icons, and the manifest overlay for one variant. */
abstract class GenerateSiteSources @Inject constructor(private val execOps: ExecOperations) : DefaultTask() {
    @get:Input
    abstract val siteId: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configInputs: ConfigurableFileCollection

    @get:Internal
    abstract val script: RegularFileProperty

    @get:OutputDirectory
    abstract val assetsDir: DirectoryProperty

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
                "--assets-out", assetsDir.get().asFile.path,
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
    // Fixed for every web app, so the classes keep their names when UniWrap renames the package (R7.6).
    namespace = "com.valghora.wrapper"
    compileSdk = 37

    defaultConfig {
        applicationId = site("applicationId")
        minSdk = 37
        targetSdk = 37
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
        release {
            // For personal sideloading only; never for store distribution. UniWrap re-signs the template.
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
        if (selectedSite == null) {
            variant.sources.assets?.addStaticSourceDirectory("src/neutral/assets")
            variant.sources.res?.addStaticSourceDirectory("src/neutral/res")
            variant.sources.manifests.addStaticManifestFile("src/neutral/AndroidManifest.xml")
        } else {
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
            variant.sources.assets?.addGeneratedSourceDirectory(generate, GenerateSiteSources::assetsDir)
            variant.sources.res?.addGeneratedSourceDirectory(generate, GenerateSiteSources::resDir)
            variant.sources.manifests.addGeneratedManifestFile(generate, GenerateSiteSources::manifestFile)
        }
    }
}

// Unit tests check the profile this build ships: the generated one for a site build, else the default.
tasks.withType<Test>().configureEach {
    val profile: Provider<RegularFile> = if (selectedSite == null) {
        provider { layout.projectDirectory.file("src/neutral/assets/uniwrap/profile.json") }
    } else {
        tasks.named<GenerateSiteSources>("generateDebugSiteSources").flatMap { it.assetsDir.file("uniwrap/profile.json") }
    }
    inputs.file(profile).withPropertyName("profileJson")
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-DprofileJson=${profile.get().asFile.path}") })
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
