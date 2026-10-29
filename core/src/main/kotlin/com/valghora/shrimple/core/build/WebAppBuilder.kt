package com.valghora.shrimple.core.build

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.android.apksig.KeyConfig
import com.valghora.shrimple.core.profile.Profile
import com.valghora.shrimple.core.profile.ProfileCodec
import com.valghora.shrimple.core.profile.ProfileValidator
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Replacement launcher and notification icon layers, as PNG bytes (docs/SHRIMPLE_SPEC.md R10.3). */
class IconSet(
    val background: ByteArray,
    val foreground: ByteArray,
    val monochrome: ByteArray,
    val notification: ByteArray,
)

/** What to build: a profile, its build number, and optionally its icons (null keeps the template's). */
data class WebAppSpec(
    val profile: Profile,
    val buildNumber: Int,
    val icons: IconSet? = null,
) {
    val packageName: String get() = WebAppBuilder.packageNameFor(profile.appId)
}

/** The built APK and what it was checked to contain. */
data class BuiltWebApp(
    val apk: File,
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val signerFingerprint: String,
)

class BuildException(message: String, cause: Throwable? = null) : Exception(message, cause)

class BuildCancelledException : Exception("Build cancelled")

/**
 * Builds a web app on the device from the template APK (docs/SHRIMPLE_SPEC.md R8): patches the
 * manifest, replaces the profile and icons, signs with Shrimple's key, and verifies the result.
 */
object WebAppBuilder {
    const val PACKAGE_PREFIX = "com.valghora.shrimple.app"
    const val TEMPLATE_PACKAGE = "com.valghora.shrimple.template"

    /** Fixed paths inside the template APK (R7.4); the template build keeps resource paths unshortened. */
    object Paths {
        const val MANIFEST = "AndroidManifest.xml"
        const val PROFILE = "assets/shrimple/profile.json"
        const val ICON_BACKGROUND = "res/drawable-nodpi-v4/ic_launcher_background.png"
        const val ICON_FOREGROUND = "res/drawable-nodpi-v4/ic_launcher_foreground.png"
        const val ICON_MONOCHROME = "res/drawable-nodpi-v4/ic_launcher_monochrome.png"
        const val ICON_NOTIFICATION = "res/drawable-nodpi-v4/ic_stat_notify.png"
        val REQUIRED = listOf(MANIFEST, PROFILE, ICON_BACKGROUND, ICON_FOREGROUND, ICON_MONOCHROME, ICON_NOTIFICATION)
    }

    enum class Stage { PATCHING, SIGNING, VERIFYING }

    private val V1_SIGNATURE = Regex("""META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))""")
    private val PACKAGE_NAME = Regex("""[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+""")

    fun packageNameFor(appId: String) = "$PACKAGE_PREFIX.$appId"

    /** Permissions and features to remove for capabilities the profile does not enable (R7.3). */
    fun removals(profile: Profile): Pair<Set<String>, Set<String>> {
        val permissions = mutableSetOf<String>()
        val features = mutableSetOf<String>()
        if (!profile.allowMicrophone) {
            permissions += setOf("android.permission.RECORD_AUDIO", "android.permission.MODIFY_AUDIO_SETTINGS")
            features += "android.hardware.microphone"
        }
        if (!profile.allowCamera) {
            permissions += "android.permission.CAMERA"
            features += setOf("android.hardware.camera", "android.hardware.camera.autofocus")
        }
        if (!profile.allowNotifications) permissions += "android.permission.POST_NOTIFICATIONS"
        if (!profile.allowLocation) {
            permissions += setOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")
            features += setOf("android.hardware.location", "android.hardware.location.gps")
        }
        return permissions to features
    }

    /** The template's version: its own versionCode (R11.4). */
    fun templateVersion(template: File): Int = ZipFile(template).use { zip ->
        val entry = zip.getEntry(Paths.MANIFEST) ?: throw BuildException("The template has no manifest")
        ManifestPatcher.read(zip.getInputStream(entry).use { it.readBytes() }).versionCode
            ?: throw BuildException("The template manifest has no versionCode")
    }

    fun build(
        template: File,
        output: File,
        spec: WebAppSpec,
        key: SigningKey,
        onStage: (Stage) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): BuiltWebApp {
        val errors = ProfileValidator.validate(spec.profile)
        if (errors.isNotEmpty()) {
            throw BuildException("Invalid settings: " + errors.joinToString("; ") { "${it.field}: ${it.message}" })
        }
        if (spec.buildNumber < 1) throw BuildException("Build number must be at least 1")
        val packageName = spec.packageName
        if (!PACKAGE_NAME.matches(packageName)) throw BuildException("Invalid package name $packageName")

        fun checkCancelled() {
            if (isCancelled()) throw BuildCancelledException()
        }

        val unsigned = File(output.parentFile, output.name + ".unsigned")
        try {
            onStage(Stage.PATCHING)
            val templateVersion = templateVersion(template)
            val versionName = "$templateVersion.${spec.buildNumber}"
            val (permissions, features) = removals(spec.profile)
            val edits = ManifestEdits(
                packageName = packageName,
                label = spec.profile.name,
                versionCode = spec.buildNumber,
                versionName = versionName,
                removePermissions = permissions,
                removeFeatures = features,
                launchMode = if (spec.profile.enableMultiWindow) LaunchMode.SINGLE_INSTANCE_PER_TASK else LaunchMode.SINGLE_TASK,
                window = with(spec.profile) {
                    WindowLayout(defaultWindowDp.width, defaultWindowDp.height, minWindowDp.width, minWindowDp.height)
                },
                multiInstance = spec.profile.enableMultiWindow,
            )
            val replacements = buildMap {
                put(Paths.PROFILE, ProfileCodec.encode(spec.profile).toByteArray(Charsets.UTF_8))
                spec.icons?.let {
                    put(Paths.ICON_BACKGROUND, it.background)
                    put(Paths.ICON_FOREGROUND, it.foreground)
                    put(Paths.ICON_MONOCHROME, it.monochrome)
                    put(Paths.ICON_NOTIFICATION, it.notification)
                }
            }
            rewrite(template, unsigned, edits, replacements)
            checkCancelled()

            onStage(Stage.SIGNING)
            val signer = ApkSigner.SignerConfig.Builder("shrimple", KeyConfig.Jca(key.privateKey), listOf(key.certificate)).build()
            ApkSigner.Builder(listOf(signer))
                .setInputApk(unsigned)
                .setOutputApk(output)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setAlignmentPreserved(false) // re-align stored entries (resources.arsc must be 4-byte aligned)
                .build()
                .sign()
            checkCancelled()

            onStage(Stage.VERIFYING)
            verify(output, key, edits)
            return BuiltWebApp(output, packageName, spec.buildNumber, versionName, key.fingerprint)
        } catch (e: BuildCancelledException) {
            output.delete()
            throw e
        } finally {
            unsigned.delete()
        }
    }

    /** Copies the template with the patched manifest and replaced entries, keeping each entry's compression. */
    private fun rewrite(template: File, out: File, edits: ManifestEdits, replacements: Map<String, ByteArray>) {
        ZipFile(template).use { zip ->
            val missing = Paths.REQUIRED.filter { zip.getEntry(it) == null }
            if (missing.isNotEmpty()) throw BuildException("The template is missing ${missing.joinToString()}")
            if (templatePackage(zip) != TEMPLATE_PACKAGE) {
                throw BuildException("Not the Shrimple template (package ${templatePackage(zip)})")
            }
            ZipOutputStream(out.outputStream().buffered()).use { zipOut ->
                for (entry in zip.entries()) {
                    if (V1_SIGNATURE.matches(entry.name)) continue
                    val data = when (entry.name) {
                        Paths.MANIFEST -> ManifestPatcher.patch(zip.getInputStream(entry).use { it.readBytes() }, edits)
                        in replacements -> replacements.getValue(entry.name)
                        else -> zip.getInputStream(entry).use { it.readBytes() }
                    }
                    zipOut.putNextEntry(copyOf(entry, data))
                    zipOut.write(data)
                    zipOut.closeEntry()
                }
            }
        }
    }

    private fun templatePackage(zip: ZipFile): String =
        ManifestPatcher.read(zip.getInputStream(zip.getEntry(Paths.MANIFEST)).use { it.readBytes() }).packageName

    private fun copyOf(entry: ZipEntry, data: ByteArray) = ZipEntry(entry.name).apply {
        time = entry.time
        if (entry.method == ZipEntry.STORED) {
            method = ZipEntry.STORED
            size = data.size.toLong()
            compressedSize = data.size.toLong()
            crc = CRC32().apply { update(data) }.value
        } else {
            method = ZipEntry.DEFLATED
        }
    }

    /** R8.4: the signature verifies with our certificate, and the manifest says what we asked for. */
    private fun verify(apk: File, key: SigningKey, edits: ManifestEdits) {
        val result = ApkVerifier.Builder(apk).build().verify()
        if (!result.isVerified) throw BuildException("Signature check failed: ${result.errors.joinToString()}")
        if (result.signerCertificates.firstOrNull() != key.certificate) throw BuildException("Signed with an unexpected key")
        val info = ZipFile(apk).use { zip ->
            ManifestPatcher.read(zip.getInputStream(zip.getEntry(Paths.MANIFEST)).use { it.readBytes() })
        }
        val problems = buildList {
            if (info.packageName != edits.packageName) add("package is ${info.packageName}")
            if (info.literalLabel != edits.label) add("label is ${info.literalLabel}")
            if (info.versionCode != edits.versionCode) add("versionCode is ${info.versionCode}")
            if (info.permissions.any { it in edits.removePermissions }) add("a removed permission is still declared")
        }
        if (problems.isNotEmpty()) throw BuildException("The built APK is not as intended: ${problems.joinToString()}")
    }
}
