package com.valghora.shrimpl.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.valghora.shrimpl.R
import com.valghora.shrimpl.core.update.Changelog

/**
 * What's new after an update (docs/SHRIMPL_SPEC.md R14.7): the [releases] since the last notes shown,
 * titled with the installed [version]. A bottom sheet in a compact window, a dialog in a wider one, by
 * the window size class (R1.1); Done or dismissing records them as seen ([onDone]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(releases: List<Changelog.Release>, version: String, onDone: () -> Unit) {
    val title = stringResource(R.string.whats_new_title, version)
    if (isMediumWidth()) {
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text(title, Modifier.semantics { heading() }) },
            text = { Notes(releases, version, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) } },
        )
    } else {
        ModalBottomSheet(onDismissRequest = onDone) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Notes(releases, version, Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDone) { Text(stringResource(R.string.action_done)) }
                }
            }
        }
    }
}

/** The notes: per release when there are several, then each heading's items as a bulleted list. */
@Composable
private fun Notes(releases: List<Changelog.Release>, version: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        releases.forEach { release ->
            if (releases.size > 1) {
                Text(
                    release.version ?: version,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
            }
            release.sections.forEach { section ->
                if (section.heading.isNotEmpty()) {
                    Text(
                        section.heading,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                    )
                }
                section.items.forEach { item -> Bullet(item) }
            }
        }
    }
}

@Composable
private fun Bullet(item: String) {
    Row {
        Text("•", Modifier.width(16.dp), style = MaterialTheme.typography.bodyMedium)
        Text(
            buildAnnotatedString {
                Changelog.runs(item).forEach { run ->
                    if (run.bold) withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(run.text) } else append(run.text)
                }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
