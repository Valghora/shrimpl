package com.valghora.shrimpl.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.toColorInt
import com.valghora.shrimpl.core.profile.ProfileForm
import com.valghora.shrimpl.core.profile.autoIconForeground
import com.valghora.shrimpl.core.records.IconRecord
import com.valghora.shrimpl.icons.IconRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An icon as previews show it: the background color under the adaptive foreground layer. */
class IconLayers(val background: Color, val foreground: ImageBitmap)

/**
 * Preview layers for [icon] with the brand colors [background] and [foreground] (empty: automatic),
 * or null while drawing or when it can't be drawn. [loadImage] reads an image icon's bytes by file
 * name. [version] changes when the image behind [icon] changes while [icon] itself (file name,
 * origin) stays the same, such as a newly picked image's bytes or a record's save time; it redraws.
 */
@Composable
fun rememberIconLayers(
    icon: IconRecord,
    background: String,
    foreground: String,
    version: Any? = null,
    loadImage: (String) -> ByteArray,
): State<IconLayers?> =
    produceState<IconLayers?>(null, icon, background, foreground, version) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val back = ProfileForm.color(background)
                val fore = ProfileForm.color(foreground).ifEmpty { autoIconForeground(back) }
                IconLayers(Color(back.toColorInt()), IconRenderer.foreground(icon.toSource(loadImage), fore.toColorInt()).asImageBitmap())
            }.getOrNull()
        }
    }

/** One adaptive icon: the 72dp visible part of the 108dp layers, clipped to [shape]. */
@Composable
fun AdaptiveIcon(layers: IconLayers, shape: Shape, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(shape).background(layers.background), contentAlignment = Alignment.Center) {
        Image(layers.foreground, contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f)) // 108 / 72
    }
}
