package com.valghora.shrimpl.data

import android.content.Context
import com.valghora.shrimpl.core.update.Changelog
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What's new after an update (docs/SHRIMPL_SPEC.md R14.7): the releases of the bundled change log newer
 * than the last version whose notes were shown, until Done. A first install shows nothing and starts
 * from its own version; an update from a version before this feature shows the newest release.
 */
class WhatsNew(private val context: Context, private val settings: AppSettings, private val versionCode: Int) {
    private val _pending = MutableStateFlow(pending())

    /** The releases to show now; empty when there is nothing new. */
    val pending: StateFlow<List<Changelog.Release>> = _pending.asStateFlow()

    /** Done: the notes of this version are seen, and show again only after the next update. */
    fun dismiss() {
        settings.setNotesSeen(versionCode)
        _pending.value = emptyList()
    }

    private fun pending(): List<Changelog.Release> {
        val seen = settings.notesSeen
        if (seen == null && freshInstall()) {
            settings.setNotesSeen(versionCode)
            return emptyList()
        }
        val text = try {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            return emptyList()
        }
        return Changelog.since(Changelog.parse(text), seen, versionCode)
    }

    /** Android records an install's first and last update times alike until the app is updated. */
    private fun freshInstall(): Boolean {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.firstInstallTime == info.lastUpdateTime
    }

    private companion object {
        const val ASSET = "CHANGELOG.md"
    }
}
