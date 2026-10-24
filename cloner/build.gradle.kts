// Proof of concept: makes installable copies of an APK on the device (docs/POC_CLONER.md).
plugins {
    alias(libs.plugins.android.application)
}

/** Copies the template APK, the app this proof of concept clones, into the generated assets. */
abstract class CopyTemplateApk : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val templateApk: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        templateApk.get().asFile.copyTo(out.resolve("template.apk"))
    }
}

// Default: the committed Discord release build. Override with -Pcloner.templateApk=<path from the repo root>.
val templateApkFile: RegularFile = rootProject.layout.projectDirectory.file(
    providers.gradleProperty("cloner.templateApk").getOrElse("sites/discord/apks/1.1.0/discord-release.apk")
)

android {
    namespace = "com.valghora.cloner"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.valghora.cloner"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-poc"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    testOptions {
        unitTests.all { it.systemProperty("templateApk", templateApkFile.asFile.path) }
    }
}

androidComponents {
    onVariants { variant ->
        val copy = tasks.register<CopyTemplateApk>(
            "copy${variant.name.replaceFirstChar { it.uppercase() }}TemplateApk"
        ) {
            templateApk.set(templateApkFile)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(copy, CopyTemplateApk::outputDir)
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.apksig)
    testImplementation(libs.junit)
}
