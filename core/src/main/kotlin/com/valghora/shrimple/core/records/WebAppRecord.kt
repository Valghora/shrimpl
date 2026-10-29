package com.valghora.shrimple.core.records

import com.valghora.shrimple.core.build.WebAppBuilder
import com.valghora.shrimple.core.discovery.IconChoice
import com.valghora.shrimple.core.icons.IconSource
import com.valghora.shrimple.core.icons.SvgGlyph
import com.valghora.shrimple.core.profile.Profile
import com.valghora.shrimple.core.profile.ProfileCodec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * How a web app's icon is made (docs/SHRIMPLE_SPEC.md R10.1, R11.1). Image bytes live next to the
 * record in [image]; glyphs are kept inline as SVG text.
 */
@Serializable
data class IconRecord(
    val kind: Kind,
    /** For [Kind.GLYPH]: the glyph SVG. */
    val svg: String? = null,
    /** For [Kind.IMAGE]: the image file's name in the record's directory. */
    val image: String? = null,
    /** For [Kind.IMAGE]: the image already fills the icon shape. */
    val maskable: Boolean = false,
    /** For [Kind.LETTER]: the letter or digit. */
    val letter: String? = null,
    /** Where the icon came from, shown in the icon editor ("simple-icons 'discord'", "Photo", ...). */
    val origin: String = "",
) {
    @Serializable
    enum class Kind {
        @SerialName("glyph") GLYPH,
        @SerialName("image") IMAGE,
        @SerialName("letter") LETTER,
    }

    /** The renderer's input; [loadImage] reads [image] from the record's directory. */
    fun toSource(loadImage: (String) -> ByteArray): IconSource = when (kind) {
        Kind.GLYPH -> IconSource.Glyph(SvgGlyph.parse(requireNotNull(svg) { "glyph icon without SVG" }.toByteArray()))
        Kind.IMAGE -> IconSource.Raster(loadImage(requireNotNull(image) { "image icon without a file" }), maskable)
        Kind.LETTER -> IconSource.Letter(requireNotNull(letter?.firstOrNull()) { "letter icon without a letter" })
    }

    companion object {
        const val IMAGE_FILE = "icon-image"

        fun letter(letter: Char, origin: String = "Letter") = IconRecord(Kind.LETTER, letter = letter.uppercaseChar().toString(), origin = origin)

        fun glyph(svg: ByteArray, origin: String) = IconRecord(Kind.GLYPH, svg = svg.decodeToString(), origin = origin)

        fun image(maskable: Boolean, origin: String) = IconRecord(Kind.IMAGE, image = IMAGE_FILE, maskable = maskable, origin = origin)

        /** The record for discovery's icon; for an image, also the bytes to store as [IMAGE_FILE]. */
        fun from(choice: IconChoice): Pair<IconRecord, ByteArray?> = when (choice) {
            is IconChoice.Glyph -> glyph(choice.svg, choice.source) to null
            is IconChoice.Raster -> image(choice.maskable, choice.source) to choice.bytes
            is IconChoice.Letter -> letter(choice.letter, choice.source) to null
        }
    }
}

/** Shrimple's record of one web app it built (docs/SHRIMPLE_SPEC.md R11.1). */
@Serializable
data class WebAppRecord(
    val schemaVersion: Int = SCHEMA_VERSION,
    val profile: Profile,
    val icon: IconRecord,
    /** The build installed last; the next build is [nextBuildNumber]. */
    val buildNumber: Int,
    /** The template version that build used (R11.4). */
    val templateVersion: Int,
    /** SHA-256 of the signing certificate, so a key change can be detected. */
    val signerFingerprint: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val appId: String get() = profile.appId
    val packageName: String get() = WebAppBuilder.packageNameFor(profile.appId)
    val nextBuildNumber: Int get() = buildNumber + 1

    /** True when [bundledTemplateVersion] is newer than the template this app was built with. */
    fun updateAvailable(bundledTemplateVersion: Int) = templateVersion < bundledTemplateVersion

    /** The record after a successful install of build [built] (R11.3: failed installs never get here). */
    fun installed(profile: Profile, icon: IconRecord, built: Int, templateVersion: Int, signerFingerprint: String, now: Long) =
        copy(profile = profile, icon = icon, buildNumber = built, templateVersion = templateVersion, signerFingerprint = signerFingerprint, updatedAt = now)

    companion object {
        const val SCHEMA_VERSION = 1
        const val FIRST_BUILD = 1

        private val json = Json {
            encodeDefaults = true
            explicitNulls = false
            prettyPrint = true
            // Records written by older versions may carry settings that were since removed.
            ignoreUnknownKeys = true
        }

        fun create(profile: Profile, icon: IconRecord, templateVersion: Int, signerFingerprint: String, now: Long) = WebAppRecord(
            profile = profile,
            icon = icon,
            buildNumber = FIRST_BUILD,
            templateVersion = templateVersion,
            signerFingerprint = signerFingerprint,
            createdAt = now,
            updatedAt = now,
        )

        fun encode(record: WebAppRecord): String = json.encodeToString(serializer(), record)

        fun decode(text: String): WebAppRecord = try {
            val element = json.parseToJsonElement(text)
            val profile = (element as? JsonObject)?.get("profile")
            val upgraded = if (element is JsonObject && profile != null) JsonObject(element + ("profile" to ProfileCodec.upgrade(profile))) else element
            json.decodeFromJsonElement(serializer(), upgraded)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Invalid app record: ${e.message}", e)
        }
    }
}
