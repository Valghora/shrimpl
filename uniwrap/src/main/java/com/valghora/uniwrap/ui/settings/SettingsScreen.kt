package com.valghora.uniwrap.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.valghora.uniwrap.BuildConfig
import com.valghora.uniwrap.R
import com.valghora.uniwrap.container
import com.valghora.uniwrap.data.InstallMethod
import com.valghora.uniwrap.data.ThemeSetting
import com.valghora.uniwrap.install.Availability
import com.valghora.uniwrap.ui.common.DropdownItem
import com.valghora.uniwrap.ui.common.NavigationItem
import com.valghora.uniwrap.ui.common.SettingsItem
import com.valghora.uniwrap.ui.common.SettingsSection

/** UniWrap's settings page (docs/UNIWRAP_SPEC.md R2.5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val model = viewModel { SettingsViewModel(context.container) }
    val method by model.installMethod.collectAsStateWithLifecycle()
    val theme by model.theme.collectAsStateWithLifecycle()
    val shizuku by model.shizuku.collectAsStateWithLifecycle()
    val root by model.root.collectAsStateWithLifecycle()
    val fingerprint by model.fingerprint.collectAsStateWithLifecycle()
    val backedUp by model.keyBackedUp.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val pendingRestore by model.pendingRestore.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Export: password first, then where to save. Restore: the file first, then its password.
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }
    var askExportPassword by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PKCS12_TYPE)) { uri ->
        val password = exportPassword
        exportPassword = null
        if (uri != null && password != null) model.export(context.contentResolver, uri, password) else password?.fill('\u0000')
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            model.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                SettingsSection(stringResource(R.string.settings_install)) {
                    DropdownItem(
                        stringResource(R.string.install_method),
                        method,
                        listOf(
                            null to stringResource(R.string.install_automatic),
                            InstallMethod.SESSION to stringResource(R.string.install_session),
                            InstallMethod.SHIZUKU to stringResource(R.string.install_shizuku),
                            InstallMethod.ROOT to stringResource(R.string.install_root),
                        ),
                        model::setInstallMethod,
                        summary = stringResource(methodDetail(method)),
                    )
                    SettingsItem(
                        stringResource(R.string.install_shizuku),
                        summary = stringResource(shizukuDetail(shizuku)),
                        trailing = {
                            if (shizuku == Availability.NEEDS_PERMISSION) {
                                FilledTonalButton(onClick = model::requestShizuku) { Text(stringResource(R.string.action_grant)) }
                            }
                        },
                    )
                    SettingsItem(
                        stringResource(R.string.install_root),
                        summary = stringResource(rootDetail(root)),
                        trailing = {
                            if (root != Availability.AVAILABLE) {
                                FilledTonalButton(onClick = model::checkRoot) { Text(stringResource(R.string.action_check)) }
                            }
                        },
                    )
                }

                SettingsSection(stringResource(R.string.settings_signing)) {
                    SettingsItem(
                        stringResource(R.string.signing_fingerprint),
                        summary = fingerprint ?: stringResource(R.string.signing_none),
                    )
                    SettingsItem(
                        stringResource(R.string.signing_backup),
                        summary = stringResource(if (backedUp) R.string.signing_backed_up else R.string.signing_not_backed_up),
                        error = if (!backedUp && fingerprint != null) stringResource(R.string.signing_backup_warning) else null,
                    )
                    NavigationItem(stringResource(R.string.action_export_key), { askExportPassword = true }, enabled = fingerprint != null)
                    NavigationItem(stringResource(R.string.action_restore_key), { openFile.launch(arrayOf(PKCS12_TYPE, "application/octet-stream")) })
                }

                SettingsSection(stringResource(R.string.settings_theme)) {
                    DropdownItem(
                        stringResource(R.string.theme_mode),
                        theme,
                        listOf(
                            ThemeSetting.SYSTEM to stringResource(R.string.theme_system),
                            ThemeSetting.LIGHT to stringResource(R.string.theme_light),
                            ThemeSetting.DARK to stringResource(R.string.theme_dark),
                        ),
                        model::setTheme,
                        summary = stringResource(R.string.theme_dynamic_detail),
                    )
                }

                SettingsSection(stringResource(R.string.settings_about)) {
                    SettingsItem(
                        stringResource(R.string.app_name),
                        summary = stringResource(R.string.about_versions, BuildConfig.VERSION_NAME, model.templateVersion),
                    )
                }
            }
        }
    }

    if (askExportPassword) {
        PasswordDialog(
            title = R.string.export_title,
            text = R.string.export_text,
            confirm = true,
            onDone = { password ->
                askExportPassword = false
                exportPassword = password
                createFile.launch("uniwrap-signing-key.p12")
            },
            onDismiss = { askExportPassword = false },
        )
    }
    restoreUri?.let { uri ->
        PasswordDialog(
            title = R.string.restore_title,
            text = R.string.restore_text,
            confirm = false,
            onDone = { password ->
                restoreUri = null
                model.restore(context.contentResolver, uri, password)
            },
            onDismiss = { restoreUri = null },
        )
    }
    if (pendingRestore != null) {
        AlertDialog(
            onDismissRequest = model::cancelRestore,
            title = { Text(stringResource(R.string.restore_replace_title)) },
            text = { Text(stringResource(R.string.restore_replace_text)) },
            confirmButton = { TextButton(onClick = model::confirmRestore) { Text(stringResource(R.string.action_replace_key)) } },
            dismissButton = { TextButton(onClick = model::cancelRestore) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun methodDetail(method: InstallMethod?) = when (method) {
    null -> R.string.install_automatic_detail
    InstallMethod.SESSION -> R.string.install_session_detail
    InstallMethod.SHIZUKU -> R.string.install_shizuku_detail
    InstallMethod.ROOT -> R.string.install_root_detail
}

private fun shizukuDetail(state: Availability) = when (state) {
    Availability.AVAILABLE -> R.string.install_shizuku_ready
    Availability.NEEDS_PERMISSION -> R.string.install_shizuku_permission
    Availability.UNAVAILABLE -> R.string.install_shizuku_missing
}

private fun rootDetail(state: Availability?) = when (state) {
    null -> R.string.install_root_unchecked
    Availability.AVAILABLE -> R.string.install_root_ready
    else -> R.string.install_root_missing
}

/** Asks for a backup password; with [confirm], twice. The array is the caller's to clear. */
@Composable
private fun PasswordDialog(title: Int, text: Int, confirm: Boolean, onDone: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val tooShort = password.length < SettingsViewModel.MIN_PASSWORD
    val mismatch = confirm && repeat != password
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(text))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.field_password)) },
                    supportingText = if (confirm) ({ Text(pluralStringResource(R.plurals.password_min, SettingsViewModel.MIN_PASSWORD, SettingsViewModel.MIN_PASSWORD)) }) else null,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (confirm) {
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = { Text(stringResource(R.string.field_password_repeat)) },
                        isError = repeat.isNotEmpty() && mismatch,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(password.toCharArray()) }, enabled = !(confirm && (tooShort || mismatch)) && password.isNotEmpty()) {
                Text(stringResource(R.string.action_continue))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val PKCS12_TYPE = "application/x-pkcs12"
