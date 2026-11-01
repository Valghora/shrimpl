package com.valghora.shrimpl.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * An indeterminate progress circle with a wavy arc, after Material 3 Expressive's
 * CircularWavyProgressIndicator (not public in the stable Material 3 Shrimpl uses; docs/BUILD_NOTES.md).
 * It turns and its arc grows and shrinks for as long as it is shown.
 */
@Composable
fun WavyProgressCircle(
    modifier: Modifier = Modifier,
    diameter: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "wavy")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(ROTATION_MS, easing = LinearEasing)),
        label = "rotation",
    )
    val sweep by transition.animateFloat(
        initialValue = MIN_SWEEP,
        targetValue = MAX_SWEEP,
        animationSpec = infiniteRepeatable(tween(SWEEP_MS), RepeatMode.Reverse),
        label = "sweep",
    )
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(WAVE_MS, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(modifier.size(diameter).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val stroke = diameter.toPx() * STROKE_FRACTION
        val amplitude = stroke * AMPLITUDE_FRACTION
        val radius = size.minDimension / 2 - stroke / 2 - amplitude
        val start = Math.toRadians((rotation - 90).toDouble())
        val span = Math.toRadians(sweep.toDouble())
        val path = Path()
        for (i in 0..SEGMENTS) {
            val angle = start + span * i / SEGMENTS
            val r = radius + amplitude * sin(WAVES * angle + phase).toFloat()
            val x = center.x + r * cos(angle).toFloat()
            val y = center.y + r * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

private const val ROTATION_MS = 1_400
private const val SWEEP_MS = 900
private const val WAVE_MS = 1_000
private const val MIN_SWEEP = 70f
private const val MAX_SWEEP = 270f
private const val SEGMENTS = 96
private const val WAVES = 8
private const val STROKE_FRACTION = 0.11f
private const val AMPLITUDE_FRACTION = 0.45f
