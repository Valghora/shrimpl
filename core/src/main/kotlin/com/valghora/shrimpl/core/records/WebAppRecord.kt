package com.valghora.shrimpl.core.records

import com.valghora.shrimpl.core.build.WebAppBuilder
import com.valghora.shrimpl.core.discovery.IconChooser
import com.valghora.shrimpl.core.icons.IconSource
import com.valghora.shrimpl.core.icons.SvgGlyph
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.profile.ProfileCodec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * How a web app's icon is made (docs/SHRIMPL_SPEC.md R10.1, R11.1). Image bytes live next to the
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
    /** Where the icon came from ("simple-icons 'discord'", "web manifest icon", "Photo", ...). */
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
        fun letter(letter: Char, origin: String = "Letter") = IconRecord(Kind.LETTER, letter = letter.uppercaseChar().toString(), origin = origin)

        fun glyph(svg: ByteArray, origin: String) = IconRecord(Kind.GLYPH, svg = svg.decodeToString(), origin = origin)

        /** An image icon stored as [file] next to the record. */
        fun image(maskable: Boolean, origin: String, file: String) = IconRecord(Kind.IMAGE, image = file, maskable = maskable, origin = origin)
    }
}

/**
 * A web app's icon options (docs/SHRIMPL_SPEC.md R10.1): the brand glyph (when one was found), the
 * website's own icon (an image, or a letter when the site has none) and a custom image (once the user
 * picked one). [selected] says which one the app uses.
 */
@Serializable
data class IconOptions(
    val selected: Option,
    val brand: IconRecord? = null,
    val website: IconRecord,
    val custom: IconRecord? = null,
) {
    @Serializable
    enum class Option {
        @SerialName("brand") BRAND,
        @SerialName("website") WEBSITE,
        @SerialName("custom") CUSTOM,
    }

    /** The icon the app uses; an option without an icon falls back to the website's. */
    val icon: IconRecord
        get() = when (selected) {
            Option.BRAND -> brand ?: website
            Option.WEBSITE -> website
            Option.CUSTOM -> custom ?: website
        }

    /** The options shown, in display order: Brand only when there is a brand glyph. */
    val available: List<Option>
        get() = listOfNotNull(Option.BRAND.takeIf { brand != null }, Option.WEBSITE, Option.CUSTOM)

    /** The image files the options use; any other icon image next to the record is unused. */
    val imageFiles: Set<String>
        get() = listOfNotNull(brand, website, custom).mapNotNullTo(mutableSetOf()) { it.image }

    companion object {
        /** Image file names next to the record. */
        const val WEBSITE_IMAGE = "icon-website"
        const val CUSTOM_IMAGE = "icon-custom"

        /** The letter for letter icons, and the themed icon of image icons: the name's first ASCII letter or digit. */
        fun letterFor(name: String): Char = name.uppercase().firstOrNull { it.code < 128 && it.isLetterOrDigit() } ?: 'A'

        /** Brand when there is a brand glyph, else website. */
        fun of(brand: IconRecord?, website: IconRecord) =
            IconOptions(if (brand != null) Option.BRAND else Option.WEBSITE, brand = brand, website = website)

        /**
         * A record saved before icon options: its one icon becomes the matching option. An image is
         * the Website icon when it came from the site (its origin names one of the site's icons),
         * else Custom.
         */
        fun legacy(icon: IconRecord, name: String): IconOptions {
            val letter = IconRecord.letter(letterFor(name))
            return when (icon.kind) {
                IconRecord.Kind.GLYPH -> IconOptions(Option.BRAND, brand = icon, website = letter)
                IconRecord.Kind.IMAGE ->
                    if (IconChooser.isWebsiteSource(icon.origin)) {
                        IconOptions(Option.WEBSITE, website = icon)
                    } else {
                        IconOptions(Option.CUSTOM, website = letter, custom = icon)
                    }
                IconRecord.Kind.LETTER -> IconOptions(Option.WEBSITE, website = icon)
            }
        }
    }
}

/** Shrimpl's record of one web app it built (docs/SHRIMPL_SPEC.md R11.1). */
@Serializable
data class WebAppRecord(
    val schemaVersion: Int = SCHEMA_VERSION,
    val profile: Profile,
    /** The icon the last build used. */
    val icon: IconRecord,
    /** Every icon option; null in records saved before options existed (see [iconOptions]). */
    val icons: IconOptions? = null,
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

    /** The icon options, upgraded from [icon] for older records. */
    val iconOptions: IconOptions get() = icons ?: IconOptions.legacy(icon, profile.name)

    /** True when [bundledTemplateVersion] is newer than the template this app was built with. */
    fun updateAvailable(bundledTemplateVersion: Int) = templateVersion < bundledTemplateVersion

    /** The record after a successful install of build [built] (R11.3: failed installs never get here). */
    fun installed(profile: Profile, icons: IconOptions, built: Int, templateVersion: Int, signerFingerprint: String, now: Long) = copy(
        profile = profile, icon = icons.icon, icons = icons, buildNumber = built, templateVersion = templateVersion,
        signerFingerprint = signerFingerprint, updatedAt = now,
    )

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

        fun create(profile: Profile, icons: IconOptions, templateVersion: Int, signerFingerprint: String, now: Long) = WebAppRecord(
            profile = profile,
            icon = icons.icon,
            icons = icons,
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
