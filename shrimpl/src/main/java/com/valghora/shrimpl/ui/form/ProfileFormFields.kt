package com.valghora.shrimpl.ui.form

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
import com.valghora.shrimpl.R
import com.valghora.shrimpl.core.UaMode
import com.valghora.shrimpl.core.profile.HeaderMode
import com.valghora.shrimpl.core.profile.ProfileForm
import com.valghora.shrimpl.core.records.IconOptions
import com.valghora.shrimpl.core.records.IconRecord
import com.valghora.shrimpl.core.records.WebAppDraft
import com.valghora.shrimpl.ui.common.AppTextField
import com.valghora.shrimpl.ui.common.ColorSwatch
import com.valghora.shrimpl.ui.common.DropdownItem
import com.valghora.shrimpl.ui.common.NavigationItem
import com.valghora.shrimpl.ui.common.Notice
import com.valghora.shrimpl.ui.common.NoticeCard
import com.valghora.shrimpl.ui.common.NoticeSetSaver
import com.valghora.shrimpl.ui.common.SettingsItem
import com.valghora.shrimpl.ui.common.SettingsSection
import com.valghora.shrimpl.ui.common.StringListSaver
import com.valghora.shrimpl.ui.common.SwitchItem
import com.valghora.shrimpl.ui.common.TextValueItem
import com.valghora.shrimpl.ui.common.TooltipIconButton
import com.valghora.shrimpl.ui.icons.CustomIconSheet
import com.valghora.shrimpl.ui.icons.IconTiles

/**
 * A web app's settings (docs/SHRIMPL_SPEC.md R3.3), shared by the configuration and edit pages, as
 * grouped settings items. [hints] are discovery sources shown under low- and medium-confidence values.
 * On wide windows (R3.5) the sections are split over two columns.
 */
@Composable
fun ProfileFormFields(
    draft: WebAppDraft,
    onChange: (WebAppDraft) -> Unit,
    hints: Map<String, String>,
    notes: List<Notice>,
    storedImage: ((String) -> ByteArray)?,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val result = draft.form.result()
    val fields = Fields(draft.form, { onChange(draft.withForm(it)) }, result::errorFor, hints)
    // Images not yet saved are in the draft; the rest are read from the stored record.
    val loadImage: (String) -> ByteArray = { file -> draft.newImages[file] ?: requireNotNull(storedImage) { "no image $file" }(file) }
    val icon: @Composable () -> Unit = { IconSection(draft, onChange, loadImage, result::errorFor, hints) }
    if (wide) {
        // Notices span both columns, so the columns' first sections (Identity, Behavior) line up.
        Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 96.dp)) {
            Notes(notes)
            Row(horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
                Column(Modifier.weight(1f)) {
                    fields.Identity()
                    icon()
                    fields.Appearance()
                }
                Column(Modifier.weight(1f)) {
                    fields.Browser()
                    fields.Window()
                    fields.Permissions()
                }
            }
        }
    } else {
        Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 96.dp)) {
            Notes(notes)
            fields.Identity()
            icon()
            fields.Appearance()
            fields.Browser()
            fields.Window()
            fields.Permissions()
        }
    }
}

/** The gutter between the two columns: wider than the 24 dp side margins, so the columns read apart. */
private val COLUMN_GAP = 40.dp

/** Discovery's notes as dismissible notifications; a dismissed note stays hidden while the page is open. */
@Composable
private fun Notes(notes: List<Notice>) {
    var dismissed by rememberSaveable(notes, stateSaver = NoticeSetSaver) { mutableStateOf(emptySet<Notice>()) }
    notes.filter { it !in dismissed }.forEach { note ->
        NoticeCard(note, Modifier.padding(top = 12.dp), onDismiss = { dismissed = dismissed + note })
    }
}

/**
 * R10.1: one tile per icon option, then the selected option's settings: Glyph and Background color for
 * Brand, Fill icon canvas for a website image, Background color for Custom.
 */
@Composable
private fun IconSection(
    draft: WebAppDraft,
    onChange: (WebAppDraft) -> Unit,
    loadImage: (String) -> ByteArray,
    error: (String) -> String?,
    hints: Map<String, String>,
) {
    val form = draft.form
    val icons = draft.icons
    fun changeForm(newForm: ProfileForm) = onChange(draft.withForm(newForm))
    var picking by rememberSaveable { mutableStateOf(false) }
    SettingsSection(stringResource(R.string.section_icon)) {
        IconTiles(
            icons = icons,
            background = form.iconBackground,
            foreground = form.iconForeground,
            newImages = draft.newImages,
            loadImage = loadImage,
            onSelect = { onChange(draft.select(it)) },
            onCustom = { picking = true },
        )
        val brand = icons.selected == IconOptions.Option.BRAND && icons.brand != null
        val custom = icons.selected == IconOptions.Option.CUSTOM && icons.custom != null
        if (brand) {
            ColorItem(
                stringResource(R.string.field_icon_foreground), form.iconForeground, { changeForm(form.copy(iconForeground = it)) },
                error("iconForeground"), emptyLabel = stringResource(R.string.field_auto_contrast),
            )
        }
        if (icons.selected == IconOptions.Option.WEBSITE && icons.website.kind == IconRecord.Kind.IMAGE) {
            SwitchItem(
                stringResource(R.string.field_fill_canvas),
                icons.website.maskable,
                { onChange(draft.withWebsiteFill(it)) },
                summary = stringResource(R.string.field_fill_canvas_detail),
            )
        }
        // The background shows behind the brand glyph and around a custom image.
        if (brand || custom) {
            ColorItem(
                stringResource(R.string.field_icon_background), form.iconBackground, { changeForm(form.copy(iconBackground = it)) },
                error("iconBackground"), hints["iconBackground"],
            )
        }
    }
    if (picking) {
        CustomIconSheet(onPicked = { bytes, maskable, origin -> onChange(draft.withCustomIcon(bytes, maskable, origin)) }, onDismiss = { picking = false })
    }
}

private class Fields(
    val form: ProfileForm,
    val onChange: (ProfileForm) -> Unit,
    val error: (String) -> String?,
    val hints: Map<String, String>,
) {
    /** The app's name and where it lives: start address and internal domains. */
    @Composable
    fun Identity() {
        var editingDomains by rememberSaveable { mutableStateOf(false) }
        SettingsSection(stringResource(R.string.section_identity)) {
            TextValueItem(stringResource(R.string.field_name), form.name, { onChange(form.copy(name = it)) }, error("name"), hints["name"])
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
}

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
                AppTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.field_hex_color),
                    placeholder = emptyLabel,
                    leadingIcon = { ColorSwatch(swatch(text), size = 24) },
                    inDialog = true,
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
                AppTextField(
                    value = adding,
                    onValueChange = { adding = it },
                    label = stringResource(R.string.field_add_domain),
                    inDialog = true,
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
                AppTextField(
                    w, { w = it }, label = stringResource(R.string.field_width_dp), inDialog = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
                AppTextField(
                    h, { h = it }, label = stringResource(R.string.field_height_dp), inDialog = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(w.trim(), h.trim()) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
