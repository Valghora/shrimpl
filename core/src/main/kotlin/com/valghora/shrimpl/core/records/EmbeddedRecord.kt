package com.valghora.shrimpl.core.records

import com.valghora.shrimpl.core.Versions
import com.valghora.shrimpl.core.build.WebAppBuilder
import com.valghora.shrimpl.core.profile.ProfileCodec
import com.valghora.shrimpl.core.profile.ProfileException
import java.util.zip.ZipFile
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * What a built web app carries so a reinstalled Shrimpl can list it again (docs/SHRIMPL_SPEC.md R11.6):
 * besides its profile, the icon options as `assets/shrimpl/icons.json` and the options' images next to
 * it. [recover] turns an installed APK back into a record.
 */
object EmbeddedRecord {
    const val ICONS = "assets/shrimpl/icons.json"
    private const val DIRECTORY = "assets/shrimpl/"

    /** Marks the icon of an app recovered from its drawn layers (one built before icons.json). */
    const val DRAWN_ORIGIN = "Installed app"

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    /** The APK entries for [icons]; [image] reads an option's image by its file name. */
    fun entries(icons: IconOptions, image: (String) -> ByteArray): Map<String, ByteArray> =
        mapOf(ICONS to json.encodeToString(IconOptions.serializer(), icons).toByteArray()) +
            icons.imageFiles.associate { DIRECTORY + it to image(it) }

    /** What Android says about the installed package. */
    data class Installed(
        val packageName: String,
        val versionCode: Long,
        val versionName: String,
        val signerFingerprint: String,
        val firstInstalled: Long,
        val lastUpdated: Long,
    )

    /** A recovered record and the images to store next to it. */
    data class Recovered(val record: WebAppRecord, val images: Map<String, ByteArray>)

    class RecoveryException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * The record of the web app [installed] whose APK is [apk]. Apps built before icons.json come back
     * with their drawn icon as a custom image: [drawnIcon] combines the launcher icon's background and
     * foreground layers into one square PNG (null when it can't, leaving the letter icon).
     */
    fun recover(apk: ZipFile, installed: Installed, drawnIcon: (background: ByteArray, foreground: ByteArray) -> ByteArray?): Recovered {
        fun read(path: String): ByteArray? = apk.getEntry(path)?.let { entry -> apk.getInputStream(entry).use { it.readBytes() } }

        val profile = try {
            ProfileCodec.decode(read(WebAppBuilder.Paths.PROFILE)?.decodeToString() ?: throw RecoveryException("no profile"))
        } catch (e: ProfileException) {
            throw RecoveryException("invalid profile: ${e.message}", e)
        }
        if (WebAppBuilder.packageNameFor(profile.appId) != installed.packageName) {
            throw RecoveryException("the profile is for ${profile.appId}, not ${installed.packageName}")
        }
        val templateVersion = Versions.templateCodeOf(installed.versionName)
            ?: throw RecoveryException("unexpected version ${installed.versionName}")
        val buildNumber = installed.versionCode.toInt().takeIf { it >= WebAppRecord.FIRST_BUILD }
            ?: throw RecoveryException("unexpected build number ${installed.versionCode}")

        val letter = IconRecord.letter(IconOptions.letterFor(profile.name))
        val (icons, images) = read(ICONS)?.let { embedded ->
            val icons = try {
                json.decodeFromString(IconOptions.serializer(), embedded.decodeToString())
            } catch (e: SerializationException) {
                throw RecoveryException("invalid icons.json: ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                throw RecoveryException("invalid icons.json: ${e.message}", e)
            }
            icons to icons.imageFiles.associateWith { read(DIRECTORY + it) ?: throw RecoveryException("missing icon image $it") }
        } ?: run {
            val background = read(WebAppBuilder.Paths.ICON_BACKGROUND)
            val foreground = read(WebAppBuilder.Paths.ICON_FOREGROUND)
            val drawn = if (background != null && foreground != null) drawnIcon(background, foreground) else null
            if (drawn == null) {
                IconOptions(IconOptions.Option.WEBSITE, website = letter) to emptyMap()
            } else {
                val custom = IconRecord.image(maskable = true, origin = DRAWN_ORIGIN, file = IconOptions.CUSTOM_IMAGE)
                IconOptions(IconOptions.Option.CUSTOM, website = letter, custom = custom) to mapOf(IconOptions.CUSTOM_IMAGE to drawn)
            }
        }

        val record = WebAppRecord(
            profile = profile,
            icon = icons.icon,
            icons = icons,
            buildNumber = buildNumber,
            templateVersion = templateVersion,
            signerFingerprint = installed.signerFingerprint,
            createdAt = installed.firstInstalled,
            updatedAt = installed.lastUpdated,
        )
        return Recovered(record, images)
    }
}
