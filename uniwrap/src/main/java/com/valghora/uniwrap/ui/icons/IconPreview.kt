package com.valghora.uniwrap.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.valghora.uniwrap.R
import com.valghora.uniwrap.build.WebAppFactory
import com.valghora.uniwrap.icons.IconImages
import com.valghora.uniwrap.icons.IconLayers
import com.valghora.uniwrap.icons.IconRenderer
import com.valghora.wrapper.core.profile.ProfileForm
import com.valghora.wrapper.core.records.IconRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An icon choice with its colors; [image] holds new image bytes (null: use the stored image). */
data class IconSelection(
    val icon: IconRecord,
    val image: ByteArray?,
    val background: String,
    val foreground: String,
)

/** Rendered layers for [selection], or null while rendering or when it can't be drawn. */
@Composable
fun rememberIconLayers(selection: IconSelection, name: String, storedImage: (() -> ByteArray)?): State<IconLayers?> =
    produceState<IconLayers?>(null, selection, name) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val background = ProfileForm.color(selection.background).toColorInt()
                val foreground = ProfileForm.color(selection.foreground).ifEmpty { autoForeground(selection.background) }.toColorInt()
                val source = selection.icon.toSource { selection.image ?: requireNotNull(storedImage).invoke() }
                IconImages.layers(IconRenderer.render(source, background, foreground, WebAppFactory.letterOf(name)))
            }.getOrNull()
        }
    }

private fun autoForeground(background: String): String {
    val hex = ProfileForm.color(background)
    return runCatching {
        if (com.valghora.wrapper.core.DesktopSessionController.isDarkColor(hex)) "#FFFFFF" else "#000000"
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

/** R10.4: the icon in the circle, squircle and rounded-square masks, and themed. */
@Composable
fun MaskPreviews(layers: IconLayers?, modifier: Modifier = Modifier) {
    val shapes = listOf(
        R.string.mask_circle to CircleShape,
        R.string.mask_squircle to RoundedCornerShape(percent = 35),
        R.string.mask_rounded_square to RoundedCornerShape(percent = 18),
    )
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        shapes.forEach { (label, shape) -> Labeled(label) { Slot(layers) { AdaptiveIcon(it, shape, 56.dp) } } }
        Labeled(R.string.mask_themed) { Slot(layers) { AdaptiveIcon(it, CircleShape, 56.dp, themed = true) } }
    }
}

@Composable
private fun Slot(layers: IconLayers?, content: @Composable (IconLayers) -> Unit) {
    if (layers == null) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
    } else {
        content(layers)
    }
}

@Composable
private fun Labeled(label: Int, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        content()
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
    }
}
