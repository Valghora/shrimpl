package com.valghora.uniwrap.ui.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.valghora.uniwrap.R
import com.valghora.uniwrap.icons.IconLayers
import com.valghora.uniwrap.ui.common.ColorSwatch
import com.valghora.uniwrap.ui.common.DropdownItem
import com.valghora.uniwrap.ui.common.NavigationItem
import com.valghora.uniwrap.ui.common.Notice
import com.valghora.uniwrap.ui.common.NoticeCard
import com.valghora.uniwrap.ui.common.NoticeSetSaver
import com.valghora.uniwrap.ui.common.SettingsItem
import com.valghora.uniwrap.ui.common.SettingsSection
import com.valghora.uniwrap.ui.common.ShortcutRowSaver
import com.valghora.uniwrap.ui.common.StringListSaver
import com.valghora.uniwrap.ui.common.SwitchItem
import com.valghora.uniwrap.ui.common.TextValueItem
import com.valghora.uniwrap.ui.common.TooltipIconButton
import com.valghora.uniwrap.ui.icons.AdaptiveIcon
import com.valghora.wrapper.core.Modifier as KeyModifier
import com.valghora.wrapper.core.UaMode
import com.valghora.wrapper.core.profile.HeaderMode
import com.valghora.wrapper.core.profile.ProfileForm
import com.valghora.wrapper.core.profile.ShortcutRow

/**
 * A web app's settings (docs/UNIWRAP_SPEC.md R3.3), shared by the configuration and edit pages, as
 * grouped settings items. [hints] are discovery sources shown under low- and medium-confidence values.
 * On wide windows (R3.5) the sections are split over two columns.
 */
@Composable
fun ProfileFormFields(
    form: ProfileForm,
    onChange: (ProfileForm) -> Unit,
    hints: Map<String, String>,
    notes: List<Notice>,
    iconLayers: IconLayers?,
    iconOrigin: String,
    onEditIcon: () -> Unit,
    wide: Boolean,
    appIdEditable: Boolean,
    modifier: Modifier = Modifier,
) {
    val result = form.result()
    val fields = Fields(form, onChange, result::errorFor, hints)
    if (wide) {
        Row(
            modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Notes(notes)
                fields.Identity(iconLayers, iconOrigin, onEditIcon, appIdEditable)
                fields.Website()
                fields.Permissions()
                fields.Appearance()
            }
            Column(Modifier.weight(1f)) {
                fields.Browser()
                fields.Window()
                fields.Shortcuts()
            }
        }
    } else {
        Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 96.dp)) {
            Notes(notes)
            fields.Identity(iconLayers, iconOrigin, onEditIcon, appIdEditable)
            fields.Website()
            fields.Permissions()
            fields.Appearance()
            fields.Browser()
            fields.Window()
            fields.Shortcuts()
        }
    }
}

/** Discovery's notes as dismissible notifications; a dismissed note stays hidden while the page is open. */
@Composable
private fun Notes(notes: List<Notice>) {
    var dismissed by rememberSaveable(notes, stateSaver = NoticeSetSaver) { mutableStateOf(emptySet<Notice>()) }
    notes.filter { it !in dismissed }.forEach { note ->
        NoticeCard(note, Modifier.padding(top = 12.dp), onDismiss = { dismissed = dismissed + note })
    }
}

private class Fields(
    val form: ProfileForm,
    val onChange: (ProfileForm) -> Unit,
    val error: (String) -> String?,
    val hints: Map<String, String>,
) {
    @Composable
    fun Identity(layers: IconLayers?, origin: String, onEditIcon: () -> Unit, appIdEditable: Boolean) {
        SettingsSection(stringResource(R.string.section_identity)) {
            SettingsItem(
                title = stringResource(R.string.field_icon),
                summary = origin,
                onClick = onEditIcon,
                trailing = { if (layers != null) AdaptiveIcon(layers, CircleShape, 48.dp) },
            )
            TextValueItem(stringResource(R.string.field_name), form.name, { onChange(form.copy(name = it)) }, error("name"), hints["name"])
            if (!appIdEditable) SettingsItem(stringResource(R.string.field_app_id), summary = form.appId)
        }
    }

    @Composable
    fun Website() {
        var editingDomains by rememberSaveable { mutableStateOf(false) }
        SettingsSection(stringResource(R.string.section_website)) {
            TextValueItem(
                stringResource(R.string.field_start_url), form.startUrl, { onChange(form.copy(startUrl = it.trim())) },
                error("startUrl"), hints["startUrl"], keyboardType = KeyboardType.Uri,
            )
            NavigationItem(
                stringResource(R.string.field_domains),
                onClick = { editingDomains = true },
                summary = form.internalDomains.joinToString(", "),
                error = error("internalDomains"),
            )
        }
        if (editingDomains) {
            DomainsDialog(form.internalDomains, onDone = {
                editingDomains = false
                onChange(form.copy(internalDomains = it))
            }, onDismiss = { editingDomains = false })
        }
    }

    @Composable
    fun Permissions() {
        SettingsSection(stringResource(R.string.section_permissions)) {
            SwitchItem(stringResource(R.string.field_microphone), form.allowMicrophone, { onChange(form.copy(allowMicrophone = it)) }, hint = hints["allowMicrophone"])
            SwitchItem(stringResource(R.string.field_camera), form.allowCamera, { onChange(form.copy(allowCamera = it)) }, hint = hints["allowCamera"])
            SwitchItem(stringResource(R.string.field_notifications), form.allowNotifications, { onChange(form.copy(allowNotifications = it)) }, hint = hints["allowNotifications"])
            SwitchItem(stringResource(R.string.field_location), form.allowLocation, { onChange(form.copy(allowLocation = it)) }, hint = hints["allowLocation"])
        }
    }

    @Composable
    fun Appearance() {
        SettingsSection(stringResource(R.string.section_appearance)) {
            DropdownItem(
                stringResource(R.string.field_header_color),
                form.headerMode,
                listOf(
                    HeaderMode.SYSTEM to stringResource(R.string.header_system),
                    HeaderMode.DYNAMIC to stringResource(R.string.header_dynamic),
                    HeaderMode.CUSTOM to stringResource(R.string.header_custom),
                ),
                { onChange(form.copy(headerMode = it)) },
                summary = stringResource(
                    when (form.headerMode) {
                        HeaderMode.SYSTEM -> R.string.header_system_detail
                        HeaderMode.DYNAMIC -> R.string.header_dynamic_detail
                        HeaderMode.CUSTOM -> R.string.header_custom_detail
                    },
                ),
            )
            ColorItem(
                stringResource(R.string.field_custom_header), form.headerColor, { onChange(form.copy(headerColor = it)) }, error("headerColor"),
                enabled = form.headerMode == HeaderMode.CUSTOM,
            )
            ColorItem(
                stringResource(R.string.field_icon_background), form.iconBackground, { onChange(form.copy(iconBackground = it)) },
                error("iconBackground"), hints["iconBackground"],
            )
            ColorItem(
                stringResource(R.string.field_icon_foreground), form.iconForeground, { onChange(form.copy(iconForeground = it)) },
                error("iconForeground"), emptyLabel = stringResource(R.string.field_auto_contrast),
            )
        }
    }

    @Composable
    fun Browser() {
        SettingsSection(stringResource(R.string.section_browser)) {
            DropdownItem(
                stringResource(R.string.field_user_agent),
                form.userAgentMode,
                listOf(
                    UaMode.AUTO to stringResource(R.string.ua_auto),
                    UaMode.DESKTOP to stringResource(R.string.ua_desktop),
                    UaMode.MOBILE to stringResource(R.string.ua_mobile),
                ),
                { onChange(form.copy(userAgentMode = it)) },
                summary = stringResource(R.string.field_user_agent_detail),
            )
            SwitchItem(
                stringResource(R.string.field_keep_alive), form.keepAliveWhenHidden, { onChange(form.copy(keepAliveWhenHidden = it)) },
                summary = stringResource(R.string.field_keep_alive_detail),
            )
            SwitchItem(
                stringResource(R.string.field_pause_power_save), form.pauseOnPowerSave, { onChange(form.copy(pauseOnPowerSave = it)) },
                summary = stringResource(R.string.field_pause_power_save_detail),
            )
            SwitchItem(stringResource(R.string.field_context_menu), form.nativeContextMenu, { onChange(form.copy(nativeContextMenu = it)) })
        }
    }

    @Composable
    fun Window() {
        var editing by rememberSaveable { mutableStateOf<Int?>(null) }
        SettingsSection(stringResource(R.string.section_window)) {
            SwitchItem(stringResource(R.string.field_multi_window), form.enableMultiWindow, { onChange(form.copy(enableMultiWindow = it)) })
            NavigationItem(
                stringResource(R.string.field_window_default), { editing = R.string.field_window_default },
                summary = stringResource(R.string.window_size, form.defaultWidth, form.defaultHeight), error = error("defaultWindowDp"),
            )
            NavigationItem(
                stringResource(R.string.field_window_min), { editing = R.string.field_window_min },
                summary = stringResource(R.string.window_size, form.minWidth, form.minHeight), error = error("minWindowDp"),
            )
        }
        editing?.let { which ->
            val isDefault = which == R.string.field_window_default
            SizeDialog(
                title = stringResource(which),
                width = if (isDefault) form.defaultWidth else form.minWidth,
                height = if (isDefault) form.defaultHeight else form.minHeight,
                onDone = { w, h ->
                    editing = null
                    onChange(if (isDefault) form.copy(defaultWidth = w, defaultHeight = h) else form.copy(minWidth = w, minHeight = h))
                },
                onDismiss = { editing = null },
            )
        }
    }

    @Composable
    fun Shortcuts() {
        var editing by rememberSaveable { mutableStateOf<Int?>(null) }
        SettingsSection(stringResource(R.string.field_shortcuts)) {
            form.shortcuts.forEachIndexed { index, row ->
                NavigationItem(
                    row.label.ifBlank { stringResource(R.string.field_shortcut_unnamed) },
                    { editing = index },
                    summary = (row.modifiers.sortedBy { it.ordinal }.map { it.label() } + row.key.uppercase()).joinToString("+"),
                )
            }
            error("appShortcuts")?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            SettingsItem(
                stringResource(R.string.action_add_shortcut),
                summary = stringResource(R.string.field_shortcuts_detail),
                onClick = { editing = form.shortcuts.size },
                trailing = { Icon(Icons.Filled.Add, contentDescription = null) },
            )
        }
        editing?.let { index ->
            ShortcutDialog(
                initial = form.shortcuts.getOrNull(index) ?: ShortcutRow(),
                isNew = index >= form.shortcuts.size,
                onDone = { row ->
                    editing = null
                    val rows = form.shortcuts.toMutableList()
                    if (index < rows.size) rows[index] = row else rows += row
                    onChange(form.copy(shortcuts = rows))
                },
                onDelete = {
                    editing = null
                    onChange(form.copy(shortcuts = form.shortcuts.filterIndexed { i, _ -> i != index }))
                },
                onDismiss = { editing = null },
            )
        }
    }
}

private fun KeyModifier.label() = name.lowercase().replaceFirstChar(Char::uppercaseChar)

private fun swatch(hex: String): Color? = runCatching { Color(ProfileForm.color(hex).toColorInt()) }.getOrNull()

/** A color shown as a swatch; tapping edits it. [emptyLabel] names what an empty value means. */
@Composable
private fun ColorItem(
    title: String,
    value: String,
    onChange: (String) -> Unit,
    error: String?,
    hint: String? = null,
    emptyLabel: String? = null,
    enabled: Boolean = true,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    SettingsItem(
        title = title,
        summary = if (value.isEmpty()) emptyLabel else ProfileForm.color(value),
        error = error,
        hint = hint,
        enabled = enabled,
        onClick = { editing = true },
        trailing = { if (value.isNotEmpty()) ColorSwatch(swatch(value)) },
    )
    if (editing) {
        ColorDialog(title, value, emptyLabel, onDone = {
            editing = false
            onChange(it)
        }, onDismiss = { editing = false })
    }
}

private val PRESETS = listOf(
    "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
    "#009688", "#4CAF50", "#8BC34A", "#FFC107", "#FF9800", "#FF5722", "#795548", "#607D8B",
    "#FFFFFF", "#9E9E9E", "#212121", "#000000",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorDialog(title: String, initial: String, emptyLabel: String?, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.field_hex_color)) },
                    placeholder = emptyLabel?.let { { Text(it) } },
                    leadingIcon = { ColorSwatch(swatch(text), size = 24) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { hex ->
                        ColorSwatch(swatch(hex), Modifier.clickable(role = Role.Button) { text = hex }, size = 36)
                    }
                }
                if (emptyLabel != null) {
                    FilterChip(
                        selected = text.isEmpty(),
                        onClick = { text = "" },
                        label = { Text(emptyLabel) },
                        leadingIcon = if (text.isEmpty()) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(text.trim()) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DomainsDialog(initial: List<String>, onDone: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var domains by rememberSaveable(stateSaver = StringListSaver) { mutableStateOf(initial) }
    var adding by rememberSaveable { mutableStateOf("") }
    fun add() {
        val value = adding.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/')
        if (value.isNotEmpty() && value !in domains) domains = domains + value
        adding = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.field_domains)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.field_domains_detail), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    domains.forEach { domain ->
                        InputChip(
                            selected = false,
                            onClick = { domains = domains - domain },
                            label = { Text(domain) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_remove_domain, domain),
                                    modifier = Modifier.size(InputChipDefaults.IconSize),
                                )
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = adding,
                    onValueChange = { adding = it },
                    label = { Text(stringResource(R.string.field_add_domain)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    trailingIcon = { TooltipIconButton(stringResource(R.string.action_add), ::add) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                add()
                onDone(domains)
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SizeDialog(title: String, width: String, height: String, onDone: (String, String) -> Unit, onDismiss: () -> Unit) {
    var w by rememberSaveable { mutableStateOf(width) }
    var h by rememberSaveable { mutableStateOf(height) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    w, { w = it }, label = { Text(stringResource(R.string.field_width_dp)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    h, { h = it }, label = { Text(stringResource(R.string.field_height_dp)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(w.trim(), h.trim()) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutDialog(initial: ShortcutRow, isNew: Boolean, onDone: (ShortcutRow) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var row by rememberSaveable(stateSaver = ShortcutRowSaver) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.action_add_shortcut else R.string.shortcut_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    row.label, { row = row.copy(label = it) }, label = { Text(stringResource(R.string.field_shortcut_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    row.key, { row = row.copy(key = it) }, label = { Text(stringResource(R.string.field_shortcut_key)) },
                    supportingText = { Text(stringResource(R.string.field_shortcut_key_detail)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyModifier.entries.forEach { mod ->
                        FilterChip(
                            selected = mod in row.modifiers,
                            onClick = { row = row.copy(modifiers = if (mod in row.modifiers) row.modifiers - mod else row.modifiers + mod) },
                            label = { Text(mod.label()) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(row) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text(stringResource(R.string.action_remove)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
