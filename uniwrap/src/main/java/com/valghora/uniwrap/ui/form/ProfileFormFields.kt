package com.valghora.uniwrap.ui.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.valghora.uniwrap.R
import com.valghora.uniwrap.icons.IconLayers
import com.valghora.uniwrap.ui.icons.AdaptiveIcon
import com.valghora.wrapper.core.Modifier as KeyModifier
import com.valghora.wrapper.core.UaMode
import com.valghora.wrapper.core.profile.ProfileForm
import com.valghora.wrapper.core.profile.ShortcutRow

/**
 * The web app settings form (docs/UNIWRAP_SPEC.md R3.3), shared by the configuration and edit pages:
 * an icon and name header, then Identity, Website, Permissions, Appearance and a collapsed Advanced
 * section. [hints] are discovery sources shown under low- and medium-confidence values.
 */
@Composable
fun ProfileFormFields(
    form: ProfileForm,
    onChange: (ProfileForm) -> Unit,
    hints: Map<String, String>,
    notes: List<String>,
    iconLayers: IconLayers?,
    onEditIcon: () -> Unit,
    wide: Boolean,
    appIdEditable: Boolean,
    modifier: Modifier = Modifier,
) {
    val result = form.result()
    fun error(field: String) = result.errorFor(field)
    if (wide) {
        // R3.5: two columns, with the icon preview fixed in the first.
        Row(modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.width(280.dp).padding(top = 8.dp)) { Header(form, iconLayers, onEditIcon) }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Sections(form, onChange, ::error, hints, notes, appIdEditable)
            }
        }
    } else {
        Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Header(form, iconLayers, onEditIcon)
            Sections(form, onChange, ::error, hints, notes, appIdEditable)
        }
    }
}

@Composable
private fun Header(form: ProfileForm, layers: IconLayers?, onEditIcon: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (layers != null) {
            AdaptiveIcon(layers, CircleShape, 96.dp)
        } else {
            Box(Modifier.size(96.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
        }
        Spacer(Modifier.size(12.dp))
        Text(form.name.ifBlank { stringResource(R.string.field_name) }, style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = onEditIcon) { Text(stringResource(R.string.action_change_icon)) }
    }
}

@Composable
private fun Sections(
    form: ProfileForm,
    onChange: (ProfileForm) -> Unit,
    error: (String) -> String?,
    hints: Map<String, String>,
    notes: List<String>,
    appIdEditable: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 96.dp)) {
        notes.forEach { NoteCard(it) }

        SectionTitle(R.string.section_identity)
        Field(form.name, { onChange(form.copy(name = it)) }, R.string.field_name, error("name"), hints["name"])
        if (!appIdEditable) {
            Text(stringResource(R.string.field_app_id_fixed, form.appId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionTitle(R.string.section_website)
        Field(form.startUrl, { onChange(form.copy(startUrl = it)) }, R.string.field_start_url, error("startUrl"), hints["startUrl"], keyboard = KeyboardType.Uri)
        Domains(form.internalDomains, { onChange(form.copy(internalDomains = it)) }, error("internalDomains"), hints["internalDomains"])

        SectionTitle(R.string.section_permissions)
        SwitchRow(R.string.field_microphone, form.allowMicrophone, hints["allowMicrophone"]) { onChange(form.copy(allowMicrophone = it)) }
        SwitchRow(R.string.field_camera, form.allowCamera, hints["allowCamera"]) { onChange(form.copy(allowCamera = it)) }
        SwitchRow(R.string.field_notifications, form.allowNotifications, hints["allowNotifications"]) { onChange(form.copy(allowNotifications = it)) }

        SectionTitle(R.string.section_appearance)
        ColorField(form.headerColor, { onChange(form.copy(headerColor = it)) }, R.string.field_header_color, error("headerColor"), hints["headerColor"])
        ColorField(form.headerColorNight, { onChange(form.copy(headerColorNight = it)) }, R.string.field_header_color_night, error("headerColorNight"), hints["headerColorNight"], placeholder = R.string.field_same_as_light)
        ColorField(form.iconBackground, { onChange(form.copy(iconBackground = it)) }, R.string.field_icon_background, error("iconBackground"), hints["iconBackground"])
        ColorField(form.iconForeground, { onChange(form.copy(iconForeground = it)) }, R.string.field_icon_foreground, error("iconForeground"), null, placeholder = R.string.field_auto)

        Advanced(form, onChange, error)
    }
}

@Composable
private fun Advanced(form: ProfileForm, onChange: (ProfileForm) -> Unit, error: (String) -> String?) {
    var open by rememberSaveable { mutableStateOf(false) }
    HorizontalDivider(Modifier.padding(top = 8.dp))
    ListItem(
        headlineContent = { Text(stringResource(R.string.section_advanced), style = MaterialTheme.typography.titleMedium) },
        trailingContent = {
            IconButton(onClick = { open = !open }) {
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(if (open) R.string.action_collapse else R.string.action_expand),
                )
            }
        },
    )
    if (!open) return
    Text(stringResource(R.string.field_user_agent), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(UaMode.AUTO to R.string.ua_auto, UaMode.DESKTOP to R.string.ua_desktop, UaMode.MOBILE to R.string.ua_mobile).forEach { (mode, label) ->
            FilterChip(selected = form.userAgentMode == mode, onClick = { onChange(form.copy(userAgentMode = mode)) }, label = { Text(stringResource(label)) })
        }
    }
    SwitchRow(R.string.field_keep_alive, form.keepAliveWhenHidden, null) { onChange(form.copy(keepAliveWhenHidden = it)) }
    SwitchRow(R.string.field_pause_power_save, form.pauseOnPowerSave, null) { onChange(form.copy(pauseOnPowerSave = it)) }
    SwitchRow(R.string.field_multi_window, form.enableMultiWindow, null) { onChange(form.copy(enableMultiWindow = it)) }
    SwitchRow(R.string.field_context_menu, form.nativeContextMenu, null) { onChange(form.copy(nativeContextMenu = it)) }
    Text(stringResource(R.string.field_window_default), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(form.defaultWidth, { onChange(form.copy(defaultWidth = it)) }, R.string.field_width_dp, error("defaultWindowDp"), null, Modifier.weight(1f), KeyboardType.Number)
        Field(form.defaultHeight, { onChange(form.copy(defaultHeight = it)) }, R.string.field_height_dp, null, null, Modifier.weight(1f), KeyboardType.Number)
    }
    Text(stringResource(R.string.field_window_min), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field(form.minWidth, { onChange(form.copy(minWidth = it)) }, R.string.field_width_dp, error("minWindowDp"), null, Modifier.weight(1f), KeyboardType.Number)
        Field(form.minHeight, { onChange(form.copy(minHeight = it)) }, R.string.field_height_dp, null, null, Modifier.weight(1f), KeyboardType.Number)
    }
    Shortcuts(form.shortcuts, { onChange(form.copy(shortcuts = it)) }, error("appShortcuts"))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Domains(domains: List<String>, onChange: (List<String>) -> Unit, error: String?, hint: String?) {
    var adding by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.field_domains), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        domains.forEach { domain ->
            InputChip(
                selected = false,
                onClick = {},
                label = { Text(domain) },
                trailingIcon = {
                    IconButton(onClick = { onChange(domains - domain) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove_domain, domain))
                    }
                },
            )
        }
    }
    fun add() {
        val value = adding.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/')
        if (value.isNotEmpty() && value !in domains) onChange(domains + value)
        adding = ""
    }
    OutlinedTextField(
        value = adding,
        onValueChange = { adding = it },
        label = { Text(stringResource(R.string.field_add_domain)) },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: hint?.let { stringResource(R.string.hint_discovered, it) })?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { add() }),
        trailingIcon = { IconButton(onClick = { add() }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add)) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Shortcuts(rows: List<ShortcutRow>, onChange: (List<ShortcutRow>) -> Unit, error: String?) {
    Text(stringResource(R.string.field_shortcuts), style = MaterialTheme.typography.labelLarge)
    Text(stringResource(R.string.field_shortcuts_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    rows.forEachIndexed { index, row ->
        fun update(new: ShortcutRow) = onChange(rows.toMutableList().also { it[index] = new })
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(row.label, { update(row.copy(label = it)) }, label = { Text(stringResource(R.string.field_shortcut_label)) }, singleLine = true, modifier = Modifier.weight(2f))
                    OutlinedTextField(row.key, { update(row.copy(key = it)) }, label = { Text(stringResource(R.string.field_shortcut_key)) }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onChange(rows.filterIndexed { i, _ -> i != index }) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove_shortcut))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyModifier.entries.forEach { mod ->
                        FilterChip(
                            selected = mod in row.modifiers,
                            onClick = { update(row.copy(modifiers = if (mod in row.modifiers) row.modifiers - mod else row.modifiers + mod)) },
                            label = { Text(mod.name.lowercase().replaceFirstChar(Char::uppercaseChar)) },
                        )
                    }
                }
            }
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    OutlinedButton(onClick = { onChange(rows + ShortcutRow()) }) { Text(stringResource(R.string.action_add_shortcut)) }
}

@Composable
private fun SectionTitle(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun NoteCard(note: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Text(note, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    error: String?,
    hint: String?,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: hint?.let { stringResource(R.string.hint_discovered, it) })?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColorField(value: String, onChange: (String) -> Unit, label: Int, error: String?, hint: String?, placeholder: Int? = null) {
    val swatch = runCatching { Color(ProfileForm.color(value).toColorInt()) }.getOrNull()
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        placeholder = placeholder?.let { { Text(stringResource(it)) } },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: hint?.let { stringResource(R.string.hint_discovered, it) })?.let { { Text(it) } },
        leadingIcon = {
            Box(Modifier.size(24.dp).background(swatch ?: MaterialTheme.colorScheme.surfaceVariant, CircleShape))
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, hint: String?, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        supportingContent = hint?.let { { Text(stringResource(R.string.hint_discovered, it)) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}
