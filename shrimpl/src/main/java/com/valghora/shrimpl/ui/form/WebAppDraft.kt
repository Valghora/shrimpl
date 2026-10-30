package com.valghora.shrimpl.ui.form

import com.valghora.shrimpl.core.profile.ProfileForm
import com.valghora.shrimpl.core.records.IconOptions
import com.valghora.shrimpl.core.records.IconRecord

/**
 * What the configuration and edit pages hold: the form, the icon options and, by file name, the bytes
 * of image icons that are new or changed (the rest are read from the stored record). The brand
 * icon's colors live in the form.
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

    /** The form with a new name; a letter website icon follows the name's first letter. */
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
}
