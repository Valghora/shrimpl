package com.valghora.shrimpl.ui.icons

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valghora.shrimpl.R
import com.valghora.shrimpl.container
import com.valghora.shrimpl.icons.IconImages
import com.valghora.shrimpl.icons.IconPack
import com.valghora.shrimpl.icons.IconPacks
import com.valghora.shrimpl.ui.common.TooltipIconButton
import com.valghora.shrimpl.ui.common.isMediumWidth
import com.valghora.shrimpl.core.discovery.SimpleIcon
import com.valghora.shrimpl.core.records.IconRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Source(val label: Int) {
    SITE(R.string.icon_source_site),
    BRAND(R.string.icon_source_brand),
    LETTER(R.string.icon_source_letter),
    PHOTO(R.string.icon_source_photo),
    PACK(R.string.icon_source_pack),
}

/**
 * Picks a web app's icon (docs/SHRIMPL_SPEC.md R10): the site's own icon, a simple-icons glyph, a
 * letter, a photo, or an icon from an installed icon pack, with colors and a live mask preview.
 * The selection in progress is held by the caller's ViewModel ([selection], [onChange]) so it
 * survives rotation and resizing. Full screen on compact windows; a sized dialog on wider ones, so it
 * doesn't cover the page behind it (Tier 2 UI_Secondary_Elements).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconEditor(
    selection: IconSelection,
    onChange: (IconSelection) -> Unit,
    appName: String,
    siteIcon: IconSelection?,
    storedImage: (() -> ByteArray)?,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    var source by rememberSaveable {
        mutableStateOf(
            when (selection.icon.kind) {
                IconRecord.Kind.GLYPH -> Source.BRAND
                IconRecord.Kind.LETTER -> Source.LETTER
                IconRecord.Kind.IMAGE -> if (siteIcon != null) Source.SITE else Source.PHOTO
            },
        )
    }
    val layers by rememberIconLayers(selection, appName, storedImage)
    val sized = isMediumWidth()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = if (sized) {
                Modifier.widthIn(max = DIALOG_MAX_WIDTH).heightIn(max = DIALOG_MAX_HEIGHT).clip(RoundedCornerShape(28.dp))
            } else {
                Modifier
            },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.icon_editor_title)) },
                    navigationIcon = {
                        val close = stringResource(R.string.action_close)
                        TooltipIconButton(close, onDismiss) { Icon(Icons.Filled.Close, contentDescription = close) }
                    },
                    actions = { TextButton(onClick = onDone) { Text(stringResource(R.string.action_done)) } },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MaskPreviews(layers, Modifier.align(Alignment.CenterHorizontally))
                if (selection.icon.origin.isNotEmpty()) {
                    Text(stringResource(R.string.icon_origin, selection.icon.origin), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Source.entries.filter { it != Source.SITE || siteIcon != null }.forEach {
                        FilterChip(selected = source == it, onClick = { source = it }, label = { Text(stringResource(it.label)) })
                    }
                }
                ColorFields(selection, onChange)
                Box(Modifier.weight(1f)) {
                    when (source) {
                        Source.SITE -> siteIcon?.let { site ->
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.icon_use_site)) },
                                supportingContent = { Text(site.icon.origin) },
                                modifier = Modifier.clickable { onChange(selection.copy(icon = site.icon, image = site.image)) },
                            )
                        }
                        Source.BRAND -> BrandPicker(appName) { icon ->
                            onChange(
                                selection.copy(
                                    icon = IconRecord.glyph(icon.svg, "simple-icons '${icon.slug}'"),
                                    image = null,
                                    background = icon.color,
                                ),
                            )
                        }
                        Source.LETTER -> LetterPicker(selection.icon.letter ?: WebAppLetter.of(appName)) { letter ->
                            onChange(selection.copy(icon = IconRecord.letter(letter), image = null))
                        }
                        Source.PHOTO -> PhotoPicker(selection, onChange)
                        Source.PACK -> PackPicker(appName) { bytes, origin ->
                            onChange(selection.copy(icon = IconRecord.image(maskable = false, origin = origin), image = bytes))
                        }
                    }
                }
            }
        }
    }
}

private object WebAppLetter {
    fun of(name: String) = com.valghora.shrimpl.build.WebAppFactory.letterOf(name).toString()
}

@Composable
private fun ColorFields(selection: IconSelection, onChange: (IconSelection) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = selection.background,
            onValueChange = { onChange(selection.copy(background = it)) },
            label = { Text(stringResource(R.string.field_icon_background)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        if (selection.icon.kind != IconRecord.Kind.IMAGE) {
            OutlinedTextField(
                value = selection.foreground,
                onValueChange = { onChange(selection.copy(foreground = it)) },
                label = { Text(stringResource(R.string.field_icon_foreground)) },
                placeholder = { Text(stringResource(R.string.field_auto)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** R10.1: looks a brand up by name on simple-icons.org; the app name is tried when the page opens. */
@Composable
private fun BrandPicker(appName: String, onPick: (SimpleIcon) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf(appName) }
    var status by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    val notFound = stringResource(R.string.icon_brand_not_found)
    val foundText = stringResource(R.string.icon_brand_found)
    fun search() {
        val name = query.trim()
        if (name.isEmpty() || searching) return
        searching = true
        scope.launch {
            val icon = context.container.discovery.simpleIcon(name)
            searching = false
            status = if (icon == null) notFound.format(name) else foundText.format(icon.title)
            icon?.let(onPick)
        }
    }
    LaunchedEffect(Unit) { search() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.icon_brand_name)) },
            supportingText = { Text(status ?: stringResource(R.string.icon_brand_detail)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            trailingIcon = {
                if (searching) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    TooltipIconButton(stringResource(R.string.action_search), ::search) { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search)) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LetterPicker(current: String, onPick: (Char) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    OutlinedTextField(
        value = text,
        onValueChange = { value ->
            text = value.takeLast(1).uppercase()
            text.firstOrNull()?.takeIf { it.isLetterOrDigit() }?.let(onPick)
        },
        label = { Text(stringResource(R.string.icon_letter)) },
        singleLine = true,
    )
}

@Composable
private fun PhotoPicker(selection: IconSelection, onChange: (IconSelection) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("empty")
                    IconImages.squarePng(bytes)
                }
            }
            result.onSuccess { onChange(selection.copy(icon = IconRecord.image(maskable = true, origin = "Photo"), image = it)) }
                .onFailure { error = it.message }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text(stringResource(R.string.icon_choose_photo))
        }
        Text(stringResource(R.string.icon_photo_crop_note), style = MaterialTheme.typography.bodySmall)
        if (selection.icon.kind == IconRecord.Kind.IMAGE) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.icon_fills_shape)) },
                supportingContent = { Text(stringResource(R.string.icon_fills_shape_detail)) },
                trailingContent = {
                    Switch(checked = selection.icon.maskable, onCheckedChange = { onChange(selection.copy(icon = selection.icon.copy(maskable = it))) })
                },
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PackPicker(appName: String, onPick: (ByteArray, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packs by produceState<List<IconPack>?>(null) { value = withContext(Dispatchers.IO) { IconPacks.installed(context) } }
    // Saved by package name: the pack list is read again after a configuration change.
    var packName by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val list = packs
    val pack = list?.firstOrNull { it.packageName == packName }
    when {
        list == null -> CircularProgressIndicator()
        list.isEmpty() -> Text(stringResource(R.string.icon_no_packs))
        pack == null -> Column {
            list.forEach { p -> ListItem(headlineContent = { Text(p.label) }, modifier = Modifier.clickable { packName = p.packageName }) }
        }
        else -> {
            val chosen = requireNotNull(pack)
            val names by produceState<List<String>?>(null, chosen) {
                value = withContext(Dispatchers.IO) { IconPacks.icons(context, chosen, appName) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { packName = null }) { Text(stringResource(R.string.icon_other_packs)) }
                    Text(chosen.label, style = MaterialTheme.typography.titleSmall)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.icon_search_pack)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val all = names
                if (all == null) {
                    CircularProgressIndicator()
                } else {
                    val q = query.trim().lowercase()
                    val shown = remember(q, all) { (if (q.isEmpty()) all else all.filter { q in it.lowercase() }).take(MAX_RESULTS) }
                    LazyVerticalGrid(GridCells.Adaptive(64.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(shown, key = { it }) { name ->
                            PackCell(chosen, name) {
                                scope.launch {
                                    withContext(Dispatchers.IO) { IconPacks.render(context, chosen, name) }?.let { onPick(it, "Icon pack: ${chosen.label}") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackCell(pack: IconPack, name: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, pack, name) {
        value = withContext(Dispatchers.IO) {
            IconPacks.render(context, pack, name, sizePx = 144)?.let { png ->
                android.graphics.BitmapFactory.decodeByteArray(png, 0, png.size)?.asImageBitmap()
            }
        }
    }
    Box(
        Modifier.padding(4.dp).size(56.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        image?.let { Image(it, contentDescription = name, modifier = Modifier.size(48.dp)) }
    }
}

private const val MAX_RESULTS = 240
private val DIALOG_MAX_WIDTH = 720.dp
private val DIALOG_MAX_HEIGHT = 880.dp
