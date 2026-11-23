package com.valghora.shrimpl.ui.form

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.valghora.shrimpl.R
import com.valghora.shrimpl.core.AdBlocking
import com.valghora.shrimpl.core.DarkMode
import com.valghora.shrimpl.core.UaMode
import com.valghora.shrimpl.core.profile.HeaderMode
import com.valghora.shrimpl.core.profile.ProfileForm
import com.valghora.shrimpl.core.records.IconOptions
import com.valghora.shrimpl.core.records.IconRecord
import com.valghora.shrimpl.core.records.WebAppDraft
import com.valghora.shrimpl.ui.common.AppTextField
import com.valghora.shrimpl.ui.common.Browser
import com.valghora.shrimpl.ui.common.ColorSwatch
import com.valghora.shrimpl.ui.common.DropdownItem
import com.valghora.shrimpl.ui.common.FormColumnLayout
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
 * Wide enough (R3.5), the sections form the settings column ([FormColumnLayout]), which a
 * [floatingButton] (Create) never covers. [bottomInset] (the navigation
 * bar) is added inside the scroll, so the form scrolls behind the bar and ends above it.
 */
@Composable
fun ProfileFormFields(
    draft: WebAppDraft,
    onChange: (WebAppDraft) -> Unit,
    hints: Map<String, String>,
    notes: List<Notice>,
    storedImage: ((String) -> ByteArray)?,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    scrollState: ScrollState = rememberScrollState(),
    floatingButton: Boolean = true,
) {
    val result = draft.form.result()
    val fields = Fields(draft.form, { onChange(draft.withForm(it)) }, result::errorFor, hints)
    // Images not yet saved are in the draft; the rest are read from the stored record.
    val loadImage: (String) -> ByteArray = { file -> draft.newImages[file] ?: requireNotNull(storedImage) { "no image $file" }(file) }
    val icon: @Composable () -> Unit = { IconSection(draft, onChange, loadImage, result::errorFor, hints) }
    val sections: @Composable () -> Unit = {
        Notes(notes)
        fields.Identity()
        icon()
        fields.Appearance()
        fields.Browser()
        fields.Window()
        fields.Permissions()
    }
    // With one pane a floating button (Create) floats over the form; leave room below it.
    FormColumnLayout(
        modifier,
        scrollState,
        floatingButton = floatingButton,
        fullWidthBottom = (if (floatingButton) 96.dp else 32.dp) + bottomInset,
        columnBottom = 32.dp + bottomInset,
    ) { sections() }
}


/**
 * Discovery's notes as dismissible notifications; a dismissed note stays hidden while the page is open.
 * A note with an address to open is the Google sign-in note (R3.3), worded here and given Open in
 * Chrome, or Open in browser when Chrome isn't installed.
 */
@Composable
private fun Notes(notes: List<Notice>) {
    var dismissed by rememberSaveable(notes, stateSaver = NoticeSetSaver) { mutableStateOf(emptySet<Notice>()) }
    val context = LocalContext.current
    val chrome = remember(context) { Browser.hasChrome(context) }
    val signInText = stringResource(if (chrome) R.string.google_sign_in_notice else R.string.google_sign_in_notice_browser)
    val openLabel = stringResource(if (chrome) R.string.action_open_in_chrome else R.string.action_open_in_browser)
    notes.filter { it !in dismissed }.forEach { note ->
        // Keyed, so a card's swipe state stays with its note when an earlier one goes.
        key(note) {
            val address = note.openInBrowser
            NoticeCard(
                if (address == null) note else note.copy(text = signInText),
                Modifier.padding(top = 12.dp),
                onDismiss = { dismissed = dismissed + note },
                action = address?.let { openLabel to { Browser.open(context, it, chrome) } },
            )
        }
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

    /** The Permissions section: microphone, camera, notifications and location. */
    @Composable
    fun Permissions() {
        SettingsSection(stringResource(R.string.section_permissions)) {
            SwitchItem(
                stringResource(R.string.field_microphone), form.allowMicrophone, { onChange(form.copy(allowMicrophone = it)) },
                hint = hints["allowMicrophone"],
            )
            SwitchItem(
                stringResource(R.string.field_camera), form.allowCamera, { onChange(form.copy(allowCamera = it)) },
                hint = hints["allowCamera"],
            )
            SwitchItem(
                stringResource(R.string.field_notifications), form.allowNotifications, { onChange(form.copy(allowNotifications = it)) },
                hint = hints["allowNotifications"],
            )
            SwitchItem(
                stringResource(R.string.field_location), form.allowLocation, { onChange(form.copy(allowLocation = it)) },
                hint = hints["allowLocation"],
            )
        }
    }

    /** The Appearance section: the header color, and the custom color when that is chosen. */
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

    /**
     * The Behavior section: the user agent, the native context menu, ad blocking, app banners, dark mode,
     * running while hidden, and pausing in battery saver.
     */
    @Composable
    fun Browser() {
        SettingsSection(stringResource(R.string.section_browser)) {
            DropdownItem(
                stringResource(R.string.field_user_agent),
                form.userAgentMode,
                // R3.3: no Auto; the form arrives with this device's layout in its place (ProfileForm.siteLayoutFor).
                listOf(
                    UaMode.DESKTOP to stringResource(R.string.ua_desktop),
                    UaMode.MOBILE to stringResource(R.string.ua_mobile),
                ),
                { onChange(form.copy(userAgentMode = it)) },
                summary = stringResource(if (form.userAgentMode == UaMode.MOBILE) R.string.ua_mobile_detail else R.string.ua_desktop_detail),
            )
            // Whose menu a long press or right-click opens on a link or image: Shrimpl's or the site's.
            DropdownItem(
                stringResource(R.string.field_context_menu),
                form.nativeContextMenu,
                listOf(true to stringResource(R.string.context_menu_shrimpl), false to stringResource(R.string.context_menu_site)),
                { onChange(form.copy(nativeContextMenu = it)) },
                summary = stringResource(if (form.nativeContextMenu) R.string.context_menu_shrimpl_detail else R.string.context_menu_site_detail),
            )
            // T6.19: which lists the app blocks with; a live setting (R11.10), like the rest of Behavior.
            DropdownItem(
                stringResource(R.string.field_ad_blocking),
                form.adBlocking,
                listOf(
                    AdBlocking.OFF to stringResource(R.string.ad_blocking_off),
                    AdBlocking.ADS to stringResource(R.string.ad_blocking_standard),
                    AdBlocking.ADS_AND_TRACKERS to stringResource(R.string.ad_blocking_strict),
                ),
                { onChange(form.copy(adBlocking = it)) },
                summary = stringResource(
                    when (form.adBlocking) {
                        AdBlocking.OFF -> R.string.ad_blocking_off_detail
                        AdBlocking.ADS -> R.string.ad_blocking_standard_detail
                        AdBlocking.ADS_AND_TRACKERS -> R.string.ad_blocking_strict_detail
                    },
                ),
            )
            // T6.22: AdGuard's list, apart from the ad blocking level, so it works with ads let through.
            SwitchItem(
                stringResource(R.string.field_app_banners), form.hideAppBanners, { onChange(form.copy(hideAppBanners = it)) },
                summary = stringResource(R.string.field_app_banners_detail),
            )
            // T6.21: one switch; set to match the device, it follows the device again (DarkMode.of).
            val configuration = LocalConfiguration.current
            val deviceDark = remember(configuration) {
                Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            }
            SwitchItem(
                stringResource(R.string.field_dark_mode),
                form.darkMode.isOn(deviceDark),
                { onChange(form.copy(darkMode = DarkMode.of(it, deviceDark))) },
                summary = stringResource(R.string.field_dark_mode_detail),
            )
            SwitchItem(
                stringResource(R.string.field_keep_alive), form.keepAliveWhenHidden, { onChange(form.copy(keepAliveWhenHidden = it)) },
                summary = stringResource(R.string.field_keep_alive_detail),
            )
            SwitchItem(
                stringResource(R.string.field_pause_power_save), form.pauseOnPowerSave, { onChange(form.copy(pauseOnPowerSave = it)) },
                summary = stringResource(R.string.field_pause_power_save_detail),
            )
        }
    }

    /** The Window section: multi-window and the default and minimum window sizes. */
    @Composable
    fun Window() {
        var editing by rememberSaveable { mutableStateOf<Int?>(null) }
        SettingsSection(stringResource(R.string.section_window)) {
            SwitchItem(
                stringResource(R.string.field_multi_window), form.enableMultiWindow, { onChange(form.copy(enableMultiWindow = it)) },
                summary = stringResource(R.string.field_multi_window_detail),
            )
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

/** The color dialog's presets, each with the name TalkBack reads. */
private val PRESETS = listOf(
    "#F44336" to R.string.color_red, "#E91E63" to R.string.color_pink, "#9C27B0" to R.string.color_purple,
    "#673AB7" to R.string.color_deep_purple, "#3F51B5" to R.string.color_indigo, "#2196F3" to R.string.color_blue,
    "#03A9F4" to R.string.color_light_blue, "#00BCD4" to R.string.color_cyan, "#009688" to R.string.color_teal,
    "#4CAF50" to R.string.color_green, "#8BC34A" to R.string.color_light_green, "#FFC107" to R.string.color_amber,
    "#FF9800" to R.string.color_orange, "#FF5722" to R.string.color_deep_orange, "#795548" to R.string.color_brown,
    "#607D8B" to R.string.color_blue_grey, "#FFFFFF" to R.string.color_white, "#9E9E9E" to R.string.color_grey,
    "#212121" to R.string.color_dark_grey, "#000000" to R.string.color_black,
)

/** A preset's touch area (the swatch is 36 dp). */
private val PRESET_TOUCH = 48.dp

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
                // Each preset has a 48 dp touch area around its 36 dp swatch, a name for TalkBack, and
                // reads as selected when it is the color in the field.
                FlowRow {
                    PRESETS.forEach { (hex, name) ->
                        val label = stringResource(name)
                        Box(
                            Modifier
                                .size(PRESET_TOUCH)
                                .selectable(selected = text.equals(hex, ignoreCase = true), role = Role.RadioButton) { text = hex }
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) { ColorSwatch(swatch(hex), size = 36) }
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

