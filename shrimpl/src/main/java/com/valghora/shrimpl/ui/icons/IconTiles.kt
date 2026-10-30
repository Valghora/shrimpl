package com.valghora.shrimpl.ui.icons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.valghora.shrimpl.R
import com.valghora.shrimpl.core.records.IconOptions
import com.valghora.shrimpl.core.records.IconRecord

/**
 * The icon choice (docs/SHRIMPL_SPEC.md R10.1): one preview tile per option (Brand, Website, Custom),
 * the selected one ringed. An empty Custom tile shows a plus. [onCustom] is called for the Custom tile
 * when it has no icon yet or is already selected (to pick another); otherwise a tap selects.
 */
@Composable
fun IconTiles(
    icons: IconOptions,
    background: String,
    foreground: String,
    name: String,
    newImages: Map<String, ByteArray>,
    loadImage: (String) -> ByteArray,
    onSelect: (IconOptions.Option) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.selectableGroup().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            icons.available.forEach { option ->
                val selected = option == icons.selected
                val icon = when (option) {
                    IconOptions.Option.BRAND -> icons.brand
                    IconOptions.Option.WEBSITE -> icons.website
                    IconOptions.Option.CUSTOM -> icons.custom
                }
                Tile(
                    label = stringResource(option.label),
                    selected = selected,
                    onClick = {
                        if (option == IconOptions.Option.CUSTOM && (icon == null || selected)) onCustom() else onSelect(option)
                    },
                ) {
                    if (icon == null) {
                        EmptyCustom()
                    } else {
                        TilePreview(icon, background, foreground, name, icon.image?.let(newImages::get), loadImage)
                    }
                }
            }
        }
    }
}

private val IconOptions.Option.label: Int
    get() = when (this) {
        IconOptions.Option.BRAND -> R.string.icon_option_brand
        IconOptions.Option.WEBSITE -> R.string.icon_option_website
        IconOptions.Option.CUSTOM -> R.string.icon_option_custom
    }

@Composable
private fun Tile(label: String, selected: Boolean, onClick: () -> Unit, preview: @Composable () -> Unit) {
    Column(
        Modifier
            .width(TILE_WIDTH)
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(RING_SIZE)
                .border(BorderStroke(3.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent), CircleShape),
            contentAlignment = Alignment.Center,
        ) { preview() }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TilePreview(
    icon: IconRecord,
    background: String,
    foreground: String,
    name: String,
    image: ByteArray?,
    loadImage: (String) -> ByteArray,
) {
    val layers by rememberIconLayers(icon, background, foreground, name, image, loadImage)
    val current = layers
    if (current == null) {
        CircularProgressIndicator(Modifier.size(24.dp))
    } else {
        AdaptiveIcon(current, CircleShape, ICON_SIZE)
    }
}

/** A dashed circle with a plus: no custom icon yet. */
@Composable
private fun EmptyCustom() {
    val outline = MaterialTheme.colorScheme.outline
    Box(Modifier.size(ICON_SIZE), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(ICON_SIZE)) {
            drawCircle(
                color = outline,
                radius = size.minDimension / 2 - 1.dp.toPx(),
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
            )
        }
        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val ICON_SIZE = 60.dp
private val RING_SIZE = 76.dp
private val TILE_WIDTH = 96.dp
