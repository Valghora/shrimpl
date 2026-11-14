package com.valghora.shrimpl.update

import android.content.Context
import androidx.core.content.edit
import com.valghora.shrimpl.R
import com.valghora.shrimpl.core.update.Release
import com.valghora.shrimpl.core.update.Releases
import com.valghora.shrimpl.install.InstallResult
import com.valghora.shrimpl.install.Installers
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the latest check found (R14.1). */
sealed interface UpdateResult {
    data object UpToDate : UpdateResult

    data class Available(val release: Release) : UpdateResult

    /** [reason] is shown under "Couldn't check for updates" or as the update's failure. */
    data class Failed(val reason: String) : UpdateResult
}

/** What Shrimpl is doing about updates right now. */
enum class UpdateWork { CHECKING, DOWNLOADING, INSTALLING }

/**
 * The update status (R14.3): the latest check's [result] and when it ran ([lastChecked], epoch ms), a
 * check or update in progress ([work]), why the last update didn't install ([installError]), and the
 * version whose Home notice was dismissed.
 */
data class UpdateStatus(
    val lastChecked: Long? = null,
    val result: UpdateResult? = null,
    val work: UpdateWork? = null,
    val installError: String? = null,
    val dismissedCode: Int = 0,
) {
    /** The release the Home notice offers: one found and not dismissed (R14.4). */
    val notice: Release? get() = (result as? UpdateResult.Available)?.release?.takeIf { it.code > dismissedCode }
}

/**
 * Shrimpl's own updates (docs/SHRIMPL_SPEC.md R14): checks GitHub's latest release, keeps what it
 * found across restarts, and downloads, checks and installs a newer one with the chosen install method.
 * [installedCode] is this build's version code (R11.8); [debug] builds can't take a release, which is
 * signed with another key.
 */
class Updates(
    context: Context,
    private val installers: Installers,
    private val installedCode: Int,
    private val debug: Boolean,
    private val fetch: (String) -> Fetched = ::httpGet,
) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("shrimpl_updates", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _status = MutableStateFlow(load())

    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    /** The version code the background check last notified about, so each release notifies once (R14.5). */
    var notifiedCode: Int
        get() = prefs.getInt(KEY_NOTIFIED, 0)
        set(value) = prefs.edit { putInt(KEY_NOTIFIED, value) }

    /** Asks GitHub for the latest release and records what it found (R14.1); returns it. */
    suspend fun check(): UpdateResult {
        if (_status.value.work != null) return _status.value.result ?: UpdateResult.UpToDate
        _status.update { it.copy(work = UpdateWork.CHECKING) }
        val result = withContext(Dispatchers.IO) { lookUp() }
        record(result, System.currentTimeMillis())
        return result
    }

    /** Starts [check] in Shrimpl's own scope, so leaving the screen doesn't stop it. */
    fun checkNow() {
        scope.launch { check() }
    }

    /** Hides [release]'s Home notice until a newer release (R14.4). */
    fun dismiss(release: Release) {
        prefs.edit { putInt(KEY_DISMISSED, release.code) }
        _status.update { it.copy(dismissedCode = release.code) }
    }

    /**
     * Downloads [release], checks it against its published SHA-256, and installs it with the chosen
     * method (R14.6). Shrimpl is replaced when the install succeeds; a failure is kept in [UpdateStatus.installError].
     */
    fun install(release: Release) {
        if (_status.value.work != null) return
        scope.launch {
            val failure = when {
                debug -> appContext.getString(R.string.update_error_debug)
                else -> {
                    _status.update { it.copy(work = UpdateWork.DOWNLOADING, installError = null) }
                    when (val download = withContext(Dispatchers.IO) { download(release) }) {
                        is Downloaded.Ready -> {
                            _status.update { it.copy(work = UpdateWork.INSTALLING) }
                            when (val result = installers.preferred().install(download.apk)) {
                                InstallResult.Success, InstallResult.Cancelled -> null
                                InstallResult.NeedsUnknownSources -> appContext.getString(R.string.update_error_unknown_sources)
                                is InstallResult.Failure -> result.message
                            }
                        }
                        is Downloaded.Failed -> download.reason
                    }
                }
            }
            // The release stays on offer; the failure shows under it until the next try.
            _status.update { it.copy(work = null, installError = failure) }
        }
    }

    private fun lookUp(): UpdateResult {
        val answer = try {
            fetch(Releases.LATEST)
        } catch (_: IOException) {
            return UpdateResult.Failed(appContext.getString(R.string.update_error_unreachable))
        }
        return when {
            answer.status == HttpURLConnection.HTTP_NOT_FOUND -> UpdateResult.Failed(appContext.getString(R.string.update_error_no_release))
            answer.status != HttpURLConnection.HTTP_OK -> UpdateResult.Failed(appContext.getString(R.string.update_error_http, answer.status))
            else -> when (val release = Releases.parse(answer.body.decodeToString())) {
                null -> UpdateResult.Failed(appContext.getString(R.string.update_error_no_apk))
                else -> if (release.code > installedCode) UpdateResult.Available(release) else UpdateResult.UpToDate
            }
        }
    }

    private sealed interface Downloaded {
        data class Ready(val apk: File) : Downloaded

        data class Failed(val reason: String) : Downloaded
    }

    private fun download(release: Release): Downloaded {
        val folder = File(appContext.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
        val apk = File(folder, Releases.apkName(release.version))
        return try {
            val sums = fetch(release.checksumUrl).takeIf { it.status == HttpURLConnection.HTTP_OK }
                ?: return Downloaded.Failed(appContext.getString(R.string.update_error_download))
            val expected = Releases.checksumFor(sums.body.decodeToString(), apk.name)
                ?: return Downloaded.Failed(appContext.getString(R.string.update_error_checksum))
            val file = fetch(release.apkUrl).takeIf { it.status == HttpURLConnection.HTTP_OK }
                ?: return Downloaded.Failed(appContext.getString(R.string.update_error_download))
            apk.writeBytes(file.body)
            if (apk.inputStream().use(Releases::sha256) != expected) {
                apk.delete()
                Downloaded.Failed(appContext.getString(R.string.update_error_checksum))
            } else {
                Downloaded.Ready(apk)
            }
        } catch (_: IOException) {
            Downloaded.Failed(appContext.getString(R.string.update_error_download))
        }
    }

    /** Records a check's [result] at [time]; also how tests put the status in a given state. */
    fun record(result: UpdateResult, time: Long) {
        prefs.edit {
            putLong(KEY_CHECKED, time)
            when (result) {
                UpdateResult.UpToDate -> putString(KEY_KIND, KIND_UP_TO_DATE)
                is UpdateResult.Available -> {
                    putString(KEY_KIND, KIND_AVAILABLE)
                    putString(KEY_VERSION, result.release.version)
                    putInt(KEY_CODE, result.release.code)
                    putString(KEY_APK, result.release.apkUrl)
                    putString(KEY_SUMS, result.release.checksumUrl)
                    putString(KEY_PAGE, result.release.pageUrl)
                }
                is UpdateResult.Failed -> putString(KEY_KIND, KIND_FAILED).putString(KEY_FAILURE, result.reason)
            }
        }
        _status.update { it.copy(lastChecked = time, result = result, work = null) }
    }

    private fun load(): UpdateStatus {
        val result = when (prefs.getString(KEY_KIND, null)) {
            KIND_UP_TO_DATE -> UpdateResult.UpToDate
            KIND_AVAILABLE -> Release(
                version = prefs.getString(KEY_VERSION, null).orEmpty(),
                code = prefs.getInt(KEY_CODE, 0),
                apkUrl = prefs.getString(KEY_APK, null).orEmpty(),
                checksumUrl = prefs.getString(KEY_SUMS, null).orEmpty(),
                pageUrl = prefs.getString(KEY_PAGE, null).orEmpty(),
            ).takeIf { it.code > installedCode }?.let(UpdateResult::Available) ?: UpdateResult.UpToDate
            KIND_FAILED -> UpdateResult.Failed(prefs.getString(KEY_FAILURE, null).orEmpty())
            else -> null
        }
        return UpdateStatus(
            lastChecked = prefs.getLong(KEY_CHECKED, 0L).takeIf { it > 0 },
            result = result,
            dismissedCode = prefs.getInt(KEY_DISMISSED, 0),
        )
    }

    /** A fetched URL: its HTTP [status] and [body]. */
    class Fetched(val status: Int, val body: ByteArray)

    companion object {
        /** GitHub's API asks clients to name themselves; a generic name, as no user detail is sent. */
        private const val USER_AGENT = "Shrimpl"
        private const val TIMEOUT_MS = 15_000

        private fun httpGet(url: String): Fetched {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (url.startsWith("https://api.github.com/")) connection.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                val status = connection.responseCode
                val body = if (status == HttpURLConnection.HTTP_OK) connection.inputStream.use { it.readBytes() } else ByteArray(0)
                return Fetched(status, body)
            } finally {
                connection.disconnect()
            }
        }

        private const val KEY_CHECKED = "last_checked"
        private const val KEY_KIND = "result"
        private const val KEY_VERSION = "version"
        private const val KEY_CODE = "code"
        private const val KEY_APK = "apk_url"
        private const val KEY_SUMS = "checksum_url"
        private const val KEY_PAGE = "page_url"
        private const val KEY_FAILURE = "failure"
        private const val KEY_DISMISSED = "dismissed_code"
        private const val KEY_NOTIFIED = "notified_code"
        private const val KIND_UP_TO_DATE = "up_to_date"
        private const val KIND_AVAILABLE = "available"
        private const val KIND_FAILED = "failed"
    }
}
