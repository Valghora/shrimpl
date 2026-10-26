package com.valghora.uniwrap.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valghora.uniwrap.AppContainer
import com.valghora.uniwrap.data.InstallMethod
import com.valghora.uniwrap.data.SigningKeyStore
import com.valghora.uniwrap.data.ThemeSetting
import com.valghora.uniwrap.install.Availability
import com.valghora.wrapper.core.build.SigningKey
import com.valghora.wrapper.core.build.SigningKeyException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UniWrap's settings (docs/UNIWRAP_SPEC.md R2.5, R5.2, R6.2). */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val installMethod: StateFlow<InstallMethod> = container.settings.installMethod
    val theme: StateFlow<ThemeSetting> = container.settings.theme

    /** Where a root or Shizuku access request stands (the Request Access button's states). */
    enum class Access { NOT_REQUESTED, REQUESTING, GRANTED, FAILED }

    private val _rootAccess = MutableStateFlow(Access.NOT_REQUESTED)
    val rootAccess: StateFlow<Access> = _rootAccess.asStateFlow()

    // Shizuku can be checked without a prompt, so a permission granted earlier shows as Success.
    private val _shizukuAccess = MutableStateFlow(
        if (container.installers.shizuku.state.value == Availability.AVAILABLE) Access.GRANTED else Access.NOT_REQUESTED,
    )
    val shizukuAccess: StateFlow<Access> = _shizukuAccess.asStateFlow()

    private val _fingerprint = MutableStateFlow<String?>(null)

    /** The signing certificate's SHA-256, or null before the first web app. */
    val fingerprint: StateFlow<String?> = _fingerprint.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** A backup that holds a different key while web apps exist: the user must confirm (R5.2). */
    private val _pendingRestore = MutableStateFlow<SigningKey?>(null)
    val pendingRestore: StateFlow<SigningKey?> = _pendingRestore.asStateFlow()

    val templateVersion: Int get() = container.template.version

    init {
        loadFingerprint()
        viewModelScope.launch {
            container.installers.shizuku.permissionResults.collect { granted ->
                if (_shizukuAccess.value == Access.REQUESTING) _shizukuAccess.value = if (granted) Access.GRANTED else Access.FAILED
            }
        }
    }

    /** Picks how web apps are installed; root and Shizuku access are requested with [requestAccess]. */
    fun selectMethod(method: InstallMethod) = container.settings.setInstallMethod(method)

    /** Asks for root (the root manager's prompt) or Shizuku (its permission dialog). */
    fun requestAccess(method: InstallMethod) {
        when (method) {
            InstallMethod.SESSION -> Unit
            InstallMethod.ROOT -> viewModelScope.launch {
                _rootAccess.value = Access.REQUESTING
                val granted = container.installers.root.availability() == Availability.AVAILABLE
                _rootAccess.value = if (granted) Access.GRANTED else Access.FAILED
            }
            InstallMethod.SHIZUKU -> viewModelScope.launch {
                val shizuku = container.installers.shizuku
                when (shizuku.availability()) {
                    Availability.AVAILABLE -> _shizukuAccess.value = Access.GRANTED
                    Availability.NEEDS_PERMISSION -> {
                        _shizukuAccess.value = Access.REQUESTING
                        shizuku.requestPermission()
                    }
                    Availability.UNAVAILABLE -> _shizukuAccess.value = Access.FAILED
                }
            }
        }
    }

    fun setTheme(theme: ThemeSetting) = container.settings.setTheme(theme)

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
            _message.value = "Signing key restored. Web apps signed with the old key must be uninstalled before they can be updated."
            loadFingerprint()
        }
    }

    fun cancelRestore() {
        _pendingRestore.value = null
    }

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
