package com.valghora.shrimpl.ui.common

import android.content.res.ColorStateList
import android.view.View
import android.widget.ProgressBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The spinner on a checklist's current step (R3.2, the build sheet). The platform indeterminate
 * ProgressBar animates on the render thread, so it keeps turning while the main thread is busy, as it
 * is while the search's hidden WebView loads a page; Compose's indicator would stand still then.
 * The step row around it says "In progress", so the view itself is hidden from accessibility services.
 */
@Composable
fun StepSpinner(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        factory = { context ->
            ProgressBar(context).apply {
                isIndeterminate = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        },
        modifier = modifier,
        update = { it.indeterminateTintList = ColorStateList.valueOf(color) },
    )
}
