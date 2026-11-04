package com.valghora.shrimpl.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Material icons that material-icons-core doesn't include, for the home statuses (R2.2, R2.6). Both are
// drawn with 2 dp lines, light beside the solid wrench of Fix required; a 2-unit outline in the icon's own
// color (it takes the same tint) brings them to the wrench's weight without changing their shapes.

/** Material's "arrow_upward": an update is ready. */
val UpdateIcon: ImageVector = weightedIcon("Update", "M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8 -8,8z")

/** Material's "replay": the app isn't installed and can be built again. */
val ReplayIcon: ImageVector = weightedIcon(
    "Replay",
    "M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4c0,4.42 3.58,8 8,8s8,-3.58 8,-8 -3.58,-8 -8,-8z",
)

private fun weightedIcon(name: String, path: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(
        PathParser().parsePathString(path).toNodes(),
        fill = SolidColor(Color.Black),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = OUTLINE,
        strokeLineJoin = StrokeJoin.Round,
    )
    .build()

/** The added outline, in the icons' 24-unit grid. */
private const val OUTLINE = 2f
