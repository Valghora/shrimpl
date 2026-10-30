package com.valghora.shrimpl.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.toColorInt
import com.valghora.shrimpl.icons.IconImages
import com.valghora.shrimpl.icons.IconLayers
import com.valghora.shrimpl.icons.IconRenderer
import com.valghora.shrimpl.core.profile.ProfileForm
import com.valghora.shrimpl.core.records.IconOptions
import com.valghora.shrimpl.core.records.IconRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rendered layers for [icon] with the brand colors [background] and [foreground] (empty: automatic),
 * or null while rendering or when it can't be drawn. [loadImage] reads an image icon's bytes by file
 * name. [version] changes when the image behind [icon] changes while [icon] itself (file name,
 * origin) stays the same, such as a newly picked image's bytes or a record's save time; it redraws.
 */
@Composable
fun rememberIconLayers(
    icon: IconRecord,
    background: String,
    foreground: String,
    name: String,
    version: Any? = null,
    loadImage: (String) -> ByteArray,
): State<IconLayers?> =
    produceState<IconLayers?>(null, icon, background, foreground, name, version) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val back = ProfileForm.color(background).toColorInt()
                val fore = ProfileForm.color(foreground).ifEmpty { autoForeground(background) }.toColorInt()
                IconImages.layers(IconRenderer.render(icon.toSource(loadImage), back, fore, IconOptions.letterFor(name)))
            }.getOrNull()
        }
    }

private fun autoForeground(background: String): String {
    val hex = ProfileForm.color(background)
    return runCatching {
        if (com.valghora.shrimpl.core.DesktopSessionController.isDarkColor(hex)) "#FFFFFF" else "#000000"
    }.getOrDefault("#FFFFFF")
}

/** One adaptive icon: the 72dp visible part of the 108dp layers, clipped to [shape]. */
@Composable
fun AdaptiveIcon(layers: IconLayers, shape: Shape, size: Dp, modifier: Modifier = Modifier, themed: Boolean = false) {
    val layerSize = size * 1.5f // 108 / 72
    Box(modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        if (themed) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer))
            Image(
                layers.monochrome,
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                modifier = Modifier.requiredSize(layerSize),
            )
        } else {
            Image(layers.background, contentDescription = null, modifier = Modifier.requiredSize(layerSize))
            Image(layers.foreground, contentDescription = null, modifier = Modifier.requiredSize(layerSize))
        }
    }
}
