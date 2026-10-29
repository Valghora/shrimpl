package com.valghora.uniwrap.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.valghora.uniwrap.R

/**
 * The one "New web app" button, wherever it appears: a fully rounded filled button in the theme's
 * primary color, with its keyboard shortcut in the tooltip.
 */
@Composable
fun NewWebAppButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    WithTooltip(stringResource(R.string.tooltip_new_web_app), modifier) {
        Button(
            onClick = onClick,
            modifier = Modifier.heightIn(min = 56.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 24.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.action_new_web_app))
        }
    }
}

/**
 * An empty state whose [title] sits on the vertical center line of the space it fills, with
 * [below] under it, so empty states in side-by-side panes line up.
 */
@Composable
fun CenteredEmptyState(title: String, modifier: Modifier = Modifier, below: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxSize().padding(horizontal = 32.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
            below()
        }
    }
}
