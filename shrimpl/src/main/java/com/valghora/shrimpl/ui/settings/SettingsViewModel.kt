package com.valghora.shrimpl.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valghora.shrimpl.AppContainer
import com.valghora.shrimpl.data.BadgeTheme
import com.valghora.shrimpl.data.ClickAction
import com.valghora.shrimpl.data.InstallMethod
import com.valghora.shrimpl.data.SigningKeyStore
import com.valghora.shrimpl.data.ThemeSetting
import com.valghora.shrimpl.install.Availability
import com.valghora.shrimpl.core.build.SigningKey
import com.valghora.shrimpl.core.build.SigningKeyException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shrimpl's settings (docs/SHRIMPL_SPEC.md R2.5, R5.2, R6.2). */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val installMethod: StateFlow<InstallMethod> = container.settings.installMethod
    val theme: StateFlow<ThemeSetting> = container.settings.theme
    val clickAction: StateFlow<ClickAction> = container.settings.clickAction
    val resolveOnTap: StateFlow<Boolean> = container.settings.resolveOnTap
    val hapticFeedback: StateFlow<Boolean> = container.settings.hapticFeedback
    val badgeTheme: StateFlow<BadgeTheme> = container.settings.badgeTheme

    /** Where a root or Shevery access request stands (the Request Access button's states). */
    enum class Access { NOT_REQUESTED, REQUESTING, GRANTED, FAILED }

    private val _rootAccess = MutableStateFlow(Access.NOT_REQUESTED)
    val rootAccess: StateFlow<Access> = _rootAccess.asStateFlow()

    // Shevery can be checked without a prompt, so a permission granted earlier shows as Success.
    private val _sheveryAccess = MutableStateFlow(
        if (container.installers.shevery.state.value == Availability.AVAILABLE) Access.GRANTED else Access.NOT_REQUESTED,
    )
    val sheveryAccess: StateFlow<Access> = _sheveryAccess.asStateFlow()

    private val _fingerprint = MutableStateFlow<String?>(null)

    /** The signing certificate's SHA-256, or null before the first web app. */
    val fingerprint: StateFlow<String?> = _fingerprint.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** A backup that holds a different key while web apps exist: the user must confirm (R5.2). */
    private val _pendingRestore = MutableStateFlow<SigningKey?>(null)
    val pendingRestore: StateFlow<SigningKey?> = _pendingRestore.asStateFlow()

    val templateVersion: Int get() = container.template.version

    // The password dialog's text and the password waiting for the export file picker. Held here, not
    // in saved state, so they survive configuration changes but never reach disk.
    var password by mutableStateOf("")
    var passwordRepeat by mutableStateOf("")
    private var exportPassword: CharArray? = null

    /** Forgets the typed passwords once a dialog closes. */
    fun clearPasswordDrafts() {
        password = ""
        passwordRepeat = ""
    }

    /** Holds the export password while the user picks where to save, wiping any held before. */
    fun holdExportPassword(value: CharArray) {
        exportPassword?.fill('\u0000')
        exportPassword = value
    }

    /** The held export password, handed over once. */
    fun takeExportPassword(): CharArray? = exportPassword.also { exportPassword = null }

    override fun onCleared() {
        exportPassword?.fill('\u0000')
    }

    init {
        loadFingerprint()
        viewModelScope.launch {
            container.installers.shevery.permissionResults.collect { granted ->
                if (_sheveryAccess.value == Access.REQUESTING) _sheveryAccess.value = if (granted) Access.GRANTED else Access.FAILED
            }
        }
    }

    /** Picks how web apps are installed; root and Shevery access are requested with [requestAccess]. */
    fun selectMethod(method: InstallMethod) = container.settings.setInstallMethod(method)

    /** Asks for root (the root manager's prompt) or Shevery (its permission dialog). */
    fun requestAccess(method: InstallMethod) {
        when (method) {
            InstallMethod.SESSION -> Unit
            InstallMethod.ROOT -> viewModelScope.launch {
                _rootAccess.value = Access.REQUESTING
                val granted = container.installers.root.availability() == Availability.AVAILABLE
                _rootAccess.value = if (granted) Access.GRANTED else Access.FAILED
            }
            InstallMethod.SHEVERY -> viewModelScope.launch {
                val shevery = container.installers.shevery
                when (shevery.availability()) {
                    Availability.AVAILABLE -> _sheveryAccess.value = Access.GRANTED
                    Availability.NEEDS_PERMISSION -> {
                        _sheveryAccess.value = Access.REQUESTING
                        shevery.requestPermission()
                    }
                    Availability.UNAVAILABLE -> _sheveryAccess.value = Access.FAILED
                }
            }
        }
    }

    /** Saves the theme (R2.5). */
    fun setTheme(theme: ThemeSetting) = container.settings.setTheme(theme)

    /** Saves the default click action (R2.5). */
    fun setClickAction(action: ClickAction) = container.settings.setClickAction(action)

    /** Saves the Resolve on tap switch (R2.5). */
    fun setResolveOnTap(resolve: Boolean) = container.settings.setResolveOnTap(resolve)

    /** Saves the Badge theme setting (R2.5). */
    fun setBadgeTheme(theme: BadgeTheme) = container.settings.setBadgeTheme(theme)

    /** Saves the Haptic feedback switch (R2.5). */
    fun setHapticFeedback(enabled: Boolean) = container.settings.setHapticFeedback(enabled)

    /**
     * Writes the key as a PKCS12 file protected by [password] to [uri]
     * (R5.2); the password is wiped either way.
     */
    fun export(resolver: ContentResolver, uri: Uri, password: CharArray) {
        viewModelScope.launch {
            _message.value = withContext(Dispatchers.IO) {
                try {
                    val bytes = container.keys.export(password)
                    requireNotNull(resolver.openOutputStream(uri, "wt")) { "The file can't be opened" }.use { it.write(bytes) }
                    container.settings.setKeyBackedUp(true)
                    "Signing key saved"
                } catch (e: IOException) {
                    "Saving failed: ${e.message}"
                } catch (e: IllegalArgumentException) {
                    e.message
                } catch (e: SigningKeyException) {
                    e.message
                } finally {
                    password.fill('\u0000')
                }
            }
            loadFingerprint()
        }
    }

    /**
     * Reads a key backup from [uri] (R5.2). The same key only counts as backed up; a
     * different key while apps exist waits for the user to confirm ([pendingRestore]);
     * otherwise it replaces the key. The password is wiped either way.
     */
    fun restore(resolver: ContentResolver, uri: Uri, password: CharArray) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                try {
                    val bytes = requireNotNull(resolver.openInputStream(uri)) { "The file can't be opened" }.use { it.readBytes() }
                    val key = container.keys.read(bytes, password)
                    val current = if (container.keys.exists()) container.keys.current() else null
                    when {
                        current?.fingerprint == key.fingerprint -> {
                            container.settings.setKeyBackedUp(true)
                            "This backup holds the key already in use"
                        }
                        current != null && container.store.records.value.isNotEmpty() -> {
                            _pendingRestore.value = key
                            null
                        }
                        else -> {
                            container.keys.replace(key)
                            container.settings.setKeyBackedUp(true)
                            "Signing key restored"
                        }
                    }
                } catch (e: IOException) {
                    "Reading failed: ${e.message}"
                } catch (e: IllegalArgumentException) {
                    e.message
                } catch (e: SigningKeyException) {
                    e.message
                } finally {
                    password.fill('\u0000')
                }
            }
            if (outcome != null) _message.value = outcome
            loadFingerprint()
        }
    }

    /** Replaces the key although existing web apps were signed with another one. */
    fun confirmRestore() {
        val key = _pendingRestore.value ?: return
        _pendingRestore.value = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.keys.replace(key)
                container.settings.setKeyBackedUp(true)
            }
            _message.value = "Signing key restored. Apps signed with the old key must be uninstalled before they can be updated."
            loadFingerprint()
        }
    }

    /** Keeps the current key; the backup that held another one is dropped. */
    fun cancelRestore() {
        _pendingRestore.value = null
    }

    /** The message was shown; it isn't shown again. */
    fun messageShown() {
        _message.value = null
    }

    private fun loadFingerprint() {
        viewModelScope.launch {
            _fingerprint.value = withContext(Dispatchers.IO) {
                try {
                    container.keys.current()?.fingerprint
                } catch (_: SigningKeyException) {
                    null
                }
            }
        }
    }

    companion object {
        const val MIN_PASSWORD = SigningKeyStore.MIN_PASSWORD
    }
}
