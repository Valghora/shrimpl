package com.valghora.shrimpl.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.valghora.shrimpl.R

// Settings pages in the style of grouped Material 3 Expressive settings: a section title, then the
// items as tonal surfaces a small gap apart, the value or control at the end. A group is rounded on
// the outside and tight where its items meet.

/** A group's outer corners, the corners where its items meet, and the gap between items. */
private val OUTER_RADIUS = 20.dp
private val INNER_RADIUS = 4.dp
private val ITEM_GAP = 3.dp

/** The rounded rectangle of settings items, also used for the app list's cards. */
val ItemShape = RoundedCornerShape(OUTER_RADIUS)

/** Whether an item sits in a [SettingsSection], which shapes it by its place in the group. */
val LocalInGroup = staticCompositionLocalOf { false }

/**
 * A titled group of items (docs/SHRIMPL_SPEC.md R3.3). Each visible item is shaped by its place: the
 * first rounded at the top, the last at the bottom, the ones between with small corners, and a lone
 * item rounded all round. Items that come and go (Shizuku access, say) are counted as they are.
 */
@Composable
fun SettingsSection(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 12.dp),
        )
        Layout(content = { CompositionLocalProvider(LocalInGroup provides true, content) }) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
            // Dialogs and popups an item opens leave empty nodes; they take no place in the group.
            val count = placeables.count { it.height > 0 }
            val gap = ITEM_GAP.roundToPx()
            val height = placeables.sumOf { it.height } + gap * (count - 1).coerceAtLeast(0)
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeables.maxOfOrNull { it.width } ?: 0
            layout(width, height) {
                var y = 0
                var index = 0 // among the items that take place
                placeables.forEach { placeable ->
                    if (placeable.height == 0) {
                        placeable.place(0, y)
                    } else {
                        val position = index++
                        placeable.placeWithLayer(0, y) {
                            shape = groupShape(position, count)
                            clip = true
                        }
                        y += placeable.height + gap
                    }
                }
            }
        }
    }
}

/** The shape of item [index] of [count] in a group. */
private fun groupShape(index: Int, count: Int): Shape {
    val top = if (index == 0) OUTER_RADIUS else INNER_RADIUS
    val bottom = if (index == count - 1) OUTER_RADIUS else INNER_RADIUS
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * One item: [title], an optional [summary] (and an error or hint below it), and [trailing] at the end.
 * Clickable when [onClick] is set; dimmed when not [enabled].
 */
@Composable
fun SettingsItem(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    error: String? = null,
    errorColor: Color = Tone.ERROR.text,
    hint: String? = null,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            // In a section the section clips it to its place in the group.
            .then(if (LocalInGroup.current) Modifier else Modifier.clip(ItemShape))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = role, onClick = onClick) else Modifier)
            .heightIn(min = 72.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            summary?.takeIf(String::isNotEmpty)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                error != null -> Text(error, style = MaterialTheme.typography.bodyMedium, color = errorColor)
                hint != null -> Text(
                    stringResource(R.string.hint_discovered, hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        trailing?.invoke()
    }
}

/** An on/off item; the switch shows a check or a cross, as in the system settings. */
@Composable
fun SwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    hint: String? = null,
    enabled: Boolean = true,
) {
    SettingsItem(
        title = title,
        // The row is the switch for accessibility services: they announce its on or off state.
        modifier = Modifier.semantics { toggleableState = ToggleableState(checked) },
        summary = summary,
        hint = hint,
        enabled = enabled,
        role = Role.Switch,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = {
                    Icon(
                        if (checked) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        },
    )
}

/** An item whose value is picked from a menu: the current label and a down arrow at the end. */
@Composable
fun <T> DropdownItem(
    title: String,
    value: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingsItem(
        title = title,
        summary = summary,
        enabled = enabled,
        role = Role.DropdownList,
        onClick = { open = true },
        trailing = {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        options.firstOrNull { it.first == value }?.second.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
                AppMenu(expanded = open, onDismissRequest = { open = false }) {
                    options.forEach { (option, label) ->
                        AppMenuItem(
                            text = { Text(label) },
                            onClick = {
                                open = false
                                onSelect(option)
                            },
                            trailingIcon = if (option == value) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                        )
                    }
                }
            }
        },
    )
}

/** An item that opens another page or a dialog: a chevron at the end. */
@Composable
fun NavigationItem(title: String, onClick: () -> Unit, summary: String? = null, error: String? = null, enabled: Boolean = true) {
    SettingsItem(
        title = title,
        summary = summary,
        error = error,
        enabled = enabled,
        onClick = onClick,
        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
    )
}

/** A text value shown as the summary; tapping edits it in a dialog. */
@Composable
fun TextValueItem(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
    hint: String? = null,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    SettingsItem(
        title = title,
        summary = value.ifEmpty { placeholder.orEmpty() },
        error = error,
        hint = hint,
        onClick = { editing = true },
    )
    if (editing) {
        TextDialog(title, value, placeholder, keyboardType, onDone = {
            editing = false
            onValueChange(it)
        }, onDismiss = { editing = false })
    }
}

/** A dialog with one text field; [onDone] gets the text as typed. */
@Composable
fun TextDialog(
    title: String,
    initial: String,
    placeholder: String?,
    keyboardType: KeyboardType,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            AppTextField(
                value = text,
                onValueChange = { text = it },
                label = title,
                placeholder = placeholder,
                inDialog = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onDone(text) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A filled circle in [color], outlined so light colors show on light surfaces. */
@Composable
fun ColorSwatch(color: Color?, modifier: Modifier = Modifier, size: Int = 32) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color ?: MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

private const val DISABLED_ALPHA = 0.38f
