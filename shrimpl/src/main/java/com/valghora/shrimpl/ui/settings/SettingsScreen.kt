package com.valghora.shrimpl.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.valghora.shrimpl.BuildConfig
import com.valghora.shrimpl.R
import com.valghora.shrimpl.container
import com.valghora.shrimpl.data.ClickAction
import com.valghora.shrimpl.data.InstallMethod
import com.valghora.shrimpl.data.ThemeSetting
import com.valghora.shrimpl.ui.common.AppTextField
import com.valghora.shrimpl.ui.common.DropdownItem
import com.valghora.shrimpl.ui.common.FormColumnLayout
import com.valghora.shrimpl.ui.common.NavigationItem
import com.valghora.shrimpl.ui.common.PageTopBar
import com.valghora.shrimpl.ui.common.SettingsItem
import com.valghora.shrimpl.ui.common.SettingsSection
import com.valghora.shrimpl.ui.common.SwitchItem
import com.valghora.shrimpl.ui.common.Tone
import com.valghora.shrimpl.ui.common.TooltipIconButton
import com.valghora.shrimpl.ui.common.withoutBottom

/** Shrimpl's settings page (docs/SHRIMPL_SPEC.md R2.5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(inPane: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val model = viewModel { SettingsViewModel(context.container) }
    val method by model.installMethod.collectAsStateWithLifecycle()
    val theme by model.theme.collectAsStateWithLifecycle()
    val clickAction by model.clickAction.collectAsStateWithLifecycle()
    val resolveOnTap by model.resolveOnTap.collectAsStateWithLifecycle()
    val rootAccess by model.rootAccess.collectAsStateWithLifecycle()
    val shizukuAccess by model.shizukuAccess.collectAsStateWithLifecycle()
    val fingerprint by model.fingerprint.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val pendingRestore by model.pendingRestore.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Export: password first, then where to save. Restore: the file first, then its password.
    var askExportPassword by rememberSaveable { mutableStateOf(false) }
    var restoreUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PKCS12_TYPE)) { uri ->
        val password = model.takeExportPassword()
        if (uri != null && password != null) model.export(context.contentResolver, uri, password) else password?.fill('\u0000')
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            model.messageShown()
        }
    }

    // The same column, title and top bar layout as an app's settings page (R2.5, R3.5).
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val paneWidth = maxWidth
        val scroll = rememberScrollState()
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                PageTopBar(
                    title = stringResource(R.string.settings_title),
                    paneWidth = paneWidth,
                    scroll = scroll,
                    floatingButton = false,
                    // Beside the list the arrow closes the detail pane (docs/ADAPTIVE.md deviations).
                    navigation = {
                        TooltipIconButton(stringResource(R.string.action_back), onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            FormColumnLayout(
                Modifier.padding(padding.withoutBottom()),
                scrollState = scroll,
                floatingButton = false,
                fullWidthBottom = 32.dp + padding.calculateBottomPadding(),
                columnBottom = 32.dp + padding.calculateBottomPadding(),
            ) {
                SettingsSection(stringResource(R.string.settings_appearance)) {
                    DropdownItem(
                        stringResource(R.string.theme_mode),
                        theme,
                        listOf(
                            ThemeSetting.SYSTEM to stringResource(R.string.theme_system),
                            ThemeSetting.LIGHT to stringResource(R.string.theme_light),
                            ThemeSetting.DARK to stringResource(R.string.theme_dark),
                        ),
                        model::setTheme,
                        summary = stringResource(
                            when (theme) {
                                ThemeSetting.SYSTEM -> R.string.theme_system_detail
                                ThemeSetting.DARK -> R.string.theme_dark_detail
                                ThemeSetting.LIGHT -> R.string.theme_light_detail
                            },
                        ),
                    )
                }

                SettingsSection(stringResource(R.string.settings_install)) {
                    DropdownItem(
                        stringResource(R.string.install_method),
                        method,
                        listOf(
                            InstallMethod.SESSION to stringResource(R.string.install_session),
                            InstallMethod.SHIZUKU to stringResource(R.string.install_shizuku),
                            InstallMethod.ROOT to stringResource(R.string.install_root),
                        ),
                        model::selectMethod,
                        summary = stringResource(methodDetail(method)),
                    )
                    when (method) {
                        InstallMethod.SESSION -> Unit
                        InstallMethod.SHIZUKU -> AccessItem(R.string.request_shizuku, R.string.request_shizuku_failed, shizukuAccess) {
                            model.requestAccess(InstallMethod.SHIZUKU)
                        }
                        InstallMethod.ROOT -> AccessItem(R.string.request_root, R.string.request_root_failed, rootAccess) {
                            model.requestAccess(InstallMethod.ROOT)
                        }
                    }
                }

                SettingsSection(stringResource(R.string.section_browser)) {
                    DropdownItem(
                        stringResource(R.string.click_action),
                        clickAction,
                        listOf(
                            ClickAction.OPEN to stringResource(R.string.action_open),
                            ClickAction.EDIT to stringResource(R.string.action_edit),
                        ),
                        model::setClickAction,
                        summary = stringResource(
                            when (clickAction) {
                                ClickAction.OPEN -> R.string.click_action_open_detail
                                ClickAction.EDIT -> R.string.click_action_edit_detail
                            },
                        ),
                    )
                    SwitchItem(
                        stringResource(R.string.resolve_on_tap),
                        resolveOnTap,
                        model::setResolveOnTap,
                        summary = stringResource(if (resolveOnTap) R.string.resolve_on_tap_on_detail else R.string.resolve_on_tap_off_detail),
                    )
                }

                SettingsSection(stringResource(R.string.settings_signing)) {
                    // R5.4: the certificate's SHA-256 fingerprint, with a copy button.
                    SettingsItem(
                        stringResource(R.string.signing_certificate),
                        summary = if (fingerprint == null) stringResource(R.string.signing_none) else null,
                        trailing = fingerprint?.let { value ->
                            {
                                TooltipIconButton(stringResource(R.string.action_copy_certificate), { copyFingerprint(context, value) }) {
                                    Icon(CopyIcon, contentDescription = stringResource(R.string.action_copy_certificate))
                                }
                            }
                        },
                    )
                    NavigationItem(stringResource(R.string.action_export_key), { askExportPassword = true }, enabled = fingerprint != null)
                    NavigationItem(stringResource(R.string.action_restore_key), { openFile.launch(arrayOf(PKCS12_TYPE, "application/octet-stream")) })
                }

                SettingsSection(stringResource(R.string.settings_about)) {
                    SettingsItem(
                        stringResource(R.string.app_name),
                        summary = stringResource(R.string.about_app, BuildConfig.VERSION_NAME) + "\n" +
                            stringResource(R.string.about_template, model.templateVersion),
                    )
                }
            }
        }
    }

    if (askExportPassword) {
        PasswordDialog(
            model = model,
            title = R.string.export_title,
            text = R.string.export_text,
            confirm = true,
            onDone = { password ->
                askExportPassword = false
                model.holdExportPassword(password)
                createFile.launch("shrimpl-signing-key.p12")
            },
            onDismiss = { askExportPassword = false },
        )
    }
    restoreUri?.let { uri ->
        PasswordDialog(
            model = model,
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

/**
 * "Request Root" or "Request Shizuku": a Request button that turns into Success (success tone), or
 * Retry (error tone) with the failure described below the title.
 */
@Composable
private fun AccessItem(title: Int, failed: Int, access: SettingsViewModel.Access, onRequest: () -> Unit) {
    val (background, content) = when (access) {
        SettingsViewModel.Access.GRANTED -> Tone.SUCCESS.container to Tone.SUCCESS.content
        SettingsViewModel.Access.FAILED -> Tone.ERROR.container to Tone.ERROR.content
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    SettingsItem(
        stringResource(title),
        summary = if (access == SettingsViewModel.Access.NOT_REQUESTED || access == SettingsViewModel.Access.REQUESTING) {
            stringResource(R.string.request_access_detail)
        } else {
            null
        },
        error = if (access == SettingsViewModel.Access.FAILED) stringResource(failed) else null,
        trailing = {
            Button(
                onClick = onRequest,
                enabled = access != SettingsViewModel.Access.REQUESTING,
                colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = content),
            ) {
                if (access == SettingsViewModel.Access.REQUESTING) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(
                            when (access) {
                                SettingsViewModel.Access.GRANTED -> R.string.access_success
                                SettingsViewModel.Access.FAILED -> R.string.action_retry
                                else -> R.string.action_request_access
                            },
                        ),
                    )
                }
            }
        },
    )
}

/** Copies the certificate's SHA-256 fingerprint; Android confirms the copy itself. */
private fun copyFingerprint(context: Context, fingerprint: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.signing_certificate), fingerprint))
}

/** Material's "content copy" symbol; material-icons-core doesn't include it. */
private val CopyIcon: ImageVector = ImageVector.Builder("ContentCopy", 24.dp, 24.dp, 24f, 24f)
    .addPath(
        PathParser().parsePathString(
            "M16,1L4,1c-1.1,0 -2,0.9 -2,2v14h2L4,3h12L16,1zM19,5L8,5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2L21,7c0,-1.1 -0.9,-2 -2,-2zM19,21L8,21L8,7h11v14z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    )
    .build()

private fun methodDetail(method: InstallMethod) = when (method) {
    InstallMethod.SESSION -> R.string.install_session_detail
    InstallMethod.SHIZUKU -> R.string.install_shizuku_detail
    InstallMethod.ROOT -> R.string.install_root_detail
}

/**
 * Asks for a certificate password; with [confirm], twice. The typed text lives in [model], so it
 * survives rotation and resizing without being written to saved state. The array is the caller's
 * to clear.
 */
@Composable
private fun PasswordDialog(model: SettingsViewModel, title: Int, text: Int, confirm: Boolean, onDone: (CharArray) -> Unit, onDismiss: () -> Unit) {
    val password = model.password
    val repeat = model.passwordRepeat
    DisposableEffect(Unit) { onDispose(model::clearPasswordDrafts) }
    val tooShort = password.length < SettingsViewModel.MIN_PASSWORD
    val mismatch = confirm && repeat != password
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(text))
                AppTextField(
                    value = password,
                    onValueChange = { model.password = it },
                    label = stringResource(R.string.field_password),
                    supportingText = if (confirm) pluralStringResource(R.plurals.password_min, SettingsViewModel.MIN_PASSWORD, SettingsViewModel.MIN_PASSWORD) else null,
                    inDialog = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (confirm) {
                    val showMismatch = repeat.isNotEmpty() && mismatch
                    AppTextField(
                        value = repeat,
                        onValueChange = { model.passwordRepeat = it },
                        label = stringResource(R.string.field_password_repeat),
                        isError = showMismatch,
                        supportingText = if (showMismatch) stringResource(R.string.password_mismatch) else null,
                        inDialog = true,
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
