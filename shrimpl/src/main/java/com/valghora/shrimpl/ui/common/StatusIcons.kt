package com.valghora.shrimpl.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Material Symbols that material-icons-core doesn't include, for the home statuses (R2.2, R2.6).

/** Material's "arrow_upward": an update is ready. */
val UpdateIcon: ImageVector = materialIcon("Update", "M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8 -8,8z")

/** Material's "cached": the app isn't installed and can be built again. */
val CachedIcon: ImageVector = materialIcon(
    "Cached",
    "M19,8l-4,4h3c0,3.31 -2.69,6 -6,6 -1.01,0 -1.97,-0.25 -2.8,-0.7l-1.46,1.46C8.97,19.54 10.43,20 12,20c4.42,0 8,-3.58 8,-8h3l-4,-4z" +
        "M6,12c0,-3.31 2.69,-6 6,-6 1.01,0 1.97,0.25 2.8,0.7l1.46,-1.46C15.03,4.46 13.57,4 12,4c-4.42,0 -8,3.58 -8,8H1l4,4 4,-4H6z",
)

private fun materialIcon(name: String, path: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
    .build()
