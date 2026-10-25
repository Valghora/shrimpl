package com.valghora.uniwrap.ui.form

import com.valghora.uniwrap.ui.icons.IconSelection
import com.valghora.wrapper.core.profile.ProfileForm
import com.valghora.wrapper.core.records.IconRecord

/**
 * What the configuration and edit pages hold: the form, the icon and, for a new or changed image
 * icon, its bytes. The icon colors live in the form; [iconSelection] combines them for the editor.
 */
data class WebAppDraft(val form: ProfileForm, val icon: IconRecord, val newImage: ByteArray?) {
    val iconSelection: IconSelection get() = IconSelection(icon, newImage, form.iconBackground, form.iconForeground)

    fun withIcon(selection: IconSelection) = copy(
        form = form.copy(iconBackground = selection.background, iconForeground = selection.foreground),
        icon = selection.icon,
        newImage = selection.image,
    )

    override fun equals(other: Any?): Boolean =
        other is WebAppDraft && form == other.form && icon == other.icon && newImage.contentEqualsNullable(other.newImage)

    override fun hashCode(): Int = 31 * (31 * form.hashCode() + icon.hashCode()) + (newImage?.contentHashCode() ?: 0)
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?) = if (this == null) other == null else other != null && contentEquals(other)
