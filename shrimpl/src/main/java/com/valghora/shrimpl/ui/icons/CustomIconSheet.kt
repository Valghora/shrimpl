package com.valghora.shrimpl.ui.icons

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.graphics.drawable.toBitmap
import com.valghora.shrimpl.R
import com.valghora.shrimpl.core.icons.IconException
import com.valghora.shrimpl.icons.IconImages
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An installed icon pack that can hand back one icon on request. */
private data class IconPackApp(val packageName: String, val label: String, val icon: Drawable)

/**
 * Where a custom icon can come from (docs/SHRIMPL_SPEC.md R10.1): Photos and Files for a standalone
 * image, and every installed icon pack that answers the launchers' "pick icon" request, which opens
 * the pack's own picker. [onPicked] gets the image bytes, whether the image fills the icon shape,
 * and where it came from.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomIconSheet(onPicked: (bytes: ByteArray, maskable: Boolean, origin: String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    // Slides the sheet away before it leaves the composition.
    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    val packs by produceState(emptyList<IconPackApp>()) { value = withContext(Dispatchers.IO) { iconPacks(context) } }

    // Photos and files fill the icon shape; they are cropped to a centered square first.
    fun useImage(uri: Uri?, origin: String) {
        if (uri == null) return
        scope.launch {
            val png = withContext(Dispatchers.IO) { readSquare(context, uri) }
            if (png == null) {
                Toast.makeText(context, R.string.icon_unreadable, Toast.LENGTH_SHORT).show()
            } else {
                onPicked(png, true, origin)
                close()
            }
        }
    }
    val photoOrigin = stringResource(R.string.icon_source_photos)
    val fileOrigin = stringResource(R.string.icon_source_files)
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { useImage(it, photoOrigin) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { useImage(it, fileOrigin) }
    // The pack whose picker is open; saved, since the activity may be recreated while it is.
    var pendingPack by rememberSaveable { mutableStateOf<String?>(null) }
    val pack = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val from = pendingPack ?: return@rememberLauncherForActivityResult
        pendingPack = null
        val data = result.data ?: return@rememberLauncherForActivityResult
        scope.launch {
            // Icon packs keep their own shape, so they are not treated as filling it.
            val png = withContext(Dispatchers.IO) { packIcon(context, data) }
            if (png == null) {
                Toast.makeText(context, R.string.icon_unreadable, Toast.LENGTH_SHORT).show()
            } else {
                onPicked(png, false, from)
                close()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text(stringResource(R.string.icon_choose_from), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Source(photoOrigin, onClick = {
                    photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(painterResource(R.drawable.ic_source_photos), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Source(fileOrigin, onClick = { files.launch(arrayOf("image/*")) }) {
                    Icon(painterResource(R.drawable.ic_source_files), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                packs.forEach { app ->
                    Source(app.label, onClick = {
                        pendingPack = app.label
                        pack.launch(Intent(ACTION_PICK_ICON).setPackage(app.packageName))
                    }, tinted = false) {
                        val bitmap = remember(app) { app.icon.toBitmap(144, 144).asImageBitmap() }
                        Image(bitmap, contentDescription = null, modifier = Modifier.size(SOURCE_SIZE))
                    }
                }
            }
        }
    }
}

@Composable
private fun Source(label: String, onClick: () -> Unit, tinted: Boolean = true, icon: @Composable () -> Unit) {
    Column(
        Modifier.width(80.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = if (tinted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
            Box(Modifier.size(SOURCE_SIZE), contentAlignment = Alignment.Center) { icon() }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** The launchers' request that icon packs answer with one icon (ADW, Nova and most packs). */
private const val ACTION_PICK_ICON = "org.adw.launcher.icons.ACTION_PICK_ICON"

private val SOURCE_SIZE = 56.dp

/** Installed icon packs (R10.2): apps that answer [ACTION_PICK_ICON], with their own icon and name. */
private fun iconPacks(context: Context): List<IconPackApp> {
    val pm = context.packageManager
    return pm.queryIntentActivities(Intent(ACTION_PICK_ICON), PackageManager.ResolveInfoFlags.of(0))
        .distinctBy { it.activityInfo.packageName }
        .map { IconPackApp(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
        .sortedBy { it.label.lowercase() }
}

/** A picked image as a centered square PNG, or null when it can't be read. */
private fun readSquare(context: Context, uri: Uri): ByteArray? = try {
    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { IconImages.squarePng(it) }
} catch (_: IOException) {
    null
} catch (_: IconException) {
    null
} catch (_: SecurityException) {
    null
}

/**
 * The icon an icon pack handed back, as a square PNG: a bitmap in the "icon" extra, or an image URI.
 * The pack is another app, so only its own content URIs are read: a file URI, or one of Shrimpl's own
 * providers, would be opened with Shrimpl's access (android-intent-security skill).
 */
private fun packIcon(context: Context, data: Intent): ByteArray? {
    IntentCompat.getParcelableExtra(data, "icon", Bitmap::class.java)?.let { return IconImages.squarePng(it) }
    return data.data
        ?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT && it.authority?.startsWith(context.packageName) == false }
        ?.let { readSquare(context, it) }
}
