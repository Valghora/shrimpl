package com.valghora.shrimpl.core.records

import com.valghora.shrimpl.core.discovery.IconChoice
import com.valghora.shrimpl.core.profile.ProfileForm

/**
 * What the configuration and edit pages hold (docs/SHRIMPL_SPEC.md R3, R10): the form, the icon
 * options and, by file name, the bytes of image icons that are new or changed (the rest are read from
 * the stored record). The brand icon's colors live in the form.
 */
data class WebAppDraft(val form: ProfileForm, val icons: IconOptions, val newImages: Map<String, ByteArray> = emptyMap()) {
    fun select(option: IconOptions.Option) = copy(icons = icons.copy(selected = option))

    /** Uses [image] as the custom icon and selects it. */
    fun withCustomIcon(image: ByteArray, maskable: Boolean, origin: String) = copy(
        icons = icons.copy(
            selected = IconOptions.Option.CUSTOM,
            custom = IconRecord.image(maskable, origin, IconOptions.CUSTOM_IMAGE),
        ),
        newImages = newImages + (IconOptions.CUSTOM_IMAGE to image),
    )

    /** Fill icon canvas: the website's image fills the icon shape instead of sitting inside the safe zone. */
    fun withWebsiteFill(fill: Boolean) = copy(icons = icons.copy(website = icons.website.copy(maskable = fill)))

    /**
     * The form with [newForm]. When the name changes, a letter website icon follows its first letter
     * (a letter chosen in an older version stays until then).
     */
    fun withForm(newForm: ProfileForm): WebAppDraft {
        val website = icons.website
        val letter = IconOptions.letterFor(newForm.name)
        val follows = website.kind == IconRecord.Kind.LETTER && newForm.name != form.name && website.letter != letter.toString()
        return copy(form = newForm, icons = if (follows) icons.copy(website = IconRecord.letter(letter)) else icons)
    }

    override fun equals(other: Any?): Boolean =
        other is WebAppDraft && form == other.form && icons == other.icons &&
            newImages.keys == other.newImages.keys && newImages.all { (file, bytes) -> bytes.contentEquals(other.newImages[file]) }

    override fun hashCode(): Int =
        31 * (31 * form.hashCode() + icons.hashCode()) + newImages.entries.sumOf { (file, bytes) -> file.hashCode() xor bytes.contentHashCode() }

    companion object {
        /**
         * A new web app's draft (R10.1). The Brand option is [brand], when the site has a brand glyph,
         * and is selected. The Website option is the site's own image [website] (stored as
         * [IconOptions.WEBSITE_IMAGE]), or else a letter of the name.
         */
        fun create(form: ProfileForm, brand: IconRecord?, website: IconChoice.Raster?): WebAppDraft = WebAppDraft(
            form = form,
            icons = IconOptions.of(
                brand = brand,
                website = website?.let { IconRecord.image(it.maskable, it.source, IconOptions.WEBSITE_IMAGE) }
                    ?: IconRecord.letter(IconOptions.letterFor(form.name)),
            ),
            newImages = website?.let { mapOf(IconOptions.WEBSITE_IMAGE to it.bytes) }.orEmpty(),
        )

        /** Brand from discovery's best icon, when that is a glyph (simple-icons or mask-icon). */
        fun brandOf(best: IconChoice): IconRecord? = (best as? IconChoice.Glyph)?.let { IconRecord.glyph(it.svg, it.source) }
    }
}
