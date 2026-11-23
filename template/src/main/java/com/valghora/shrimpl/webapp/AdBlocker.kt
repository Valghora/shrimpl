package com.valghora.shrimpl.webapp

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.valghora.shrimpl.core.AdBlocking
import com.valghora.shrimpl.core.DesktopSessionController
import com.valghora.shrimpl.core.UrlClass
import com.valghora.shrimpl.core.adblock.FilterEngine
import com.valghora.shrimpl.core.adblock.FilterList
import com.valghora.shrimpl.core.records.AppOptionsSync
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Ad blocking in the app (docs/TEMPLATE_SPEC.md T6.19): the app's own copy of each list it uses, copied
 * from the Shrimpl that built it when Shrimpl has a newer one (docs/SHRIMPL_SPEC.md R15.3), and the
 * filter built from them. One per process, shared by every window; the filter is built off the main
 * thread, and until it exists nothing is blocked.
 */
object AdBlocker {
    private val io = Executors.newSingleThreadExecutor()

    @Volatile
    private var engine: FilterEngine? = null

    /** The level [engine] was built for; it is rebuilt when the profile's level differs. */
    @Volatile
    private var builtFor: AdBlocking = AdBlocking.OFF

    /** Builds the filter from the app's own copies for the profile's level, when it isn't built yet. */
    fun start(context: Context) {
        val app = context.applicationContext
        io.execute { rebuildIfNeeded(app, force = false) }
    }

    /**
     * Copies the lists [versions] says Shrimpl at [authority] has newer than the app's own, then rebuilds
     * the filter if anything changed. From the live-settings answer, after the profile took its level.
     */
    fun update(context: Context, authority: String, versions: Map<FilterList, Long>) {
        val app = context.applicationContext
        io.execute {
            var copied = false
            FilterList.forLevel(WebApp.profile.adBlocking).forEach { list ->
                val offered = versions[list] ?: 0L
                if (offered > ownVersion(app, list)) copied = copy(app, authority, list, offered) || copied
            }
            rebuildIfNeeded(app, force = copied)
        }
    }

    /**
     * The empty 403 that stands in for a blocked request, or null to let it through. The page itself is
     * never blocked, nor anything on a sign-in domain (T2a), so logins and payments keep working; a request
     * to the app's own site is first-party.
     */
    fun intercept(request: WebResourceRequest, controller: DesktopSessionController, pageHost: String): WebResourceResponse? {
        if (request.isForMainFrame || WebApp.profile.adBlocking == AdBlocking.OFF) return null
        val filter = engine ?: return null
        val url = request.url.toString()
        val kind = controller.classify(url)
        if (kind == UrlClass.OAUTH) return null
        if (!filter.shouldBlock(url, pageHost, thirdParty = kind != UrlClass.INTERNAL)) return null
        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    private fun rebuildIfNeeded(context: Context, force: Boolean) {
        val level = WebApp.profile.adBlocking
        if (level == AdBlocking.OFF) {
            engine = null
            builtFor = level
            return
        }
        if (!force && engine != null && builtFor == level) return
        val texts = FilterList.forLevel(level).mapNotNull { list -> listFile(context, list).takeIf { it.exists() }?.readText() }
        if (texts.isEmpty()) return
        engine = FilterEngine.parse(*texts.toTypedArray())
        builtFor = level
        Log.i(WebApp.profile.appId, "Ad blocking: ${engine?.ruleCount} rules for ${level.name}")
    }

    private fun copy(context: Context, authority: String, list: FilterList, version: Long): Boolean = try {
        val uri = Uri.Builder().scheme("content").authority(authority).appendPath(AppOptionsSync.LIST_PATH).appendPath(list.id).build()
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("no stream")
        val part = File(dir(context), "${list.id}.part")
        input.use { stream -> part.outputStream().use { stream.copyTo(it) } }
        if (!part.renameTo(listFile(context, list))) throw IOException("could not store ${list.id}")
        File(dir(context), "${list.id}.version").writeText(version.toString())
        true
    } catch (e: IOException) {
        Log.w(WebApp.profile.appId, "Couldn't copy ${list.id} from Shrimpl: ${e.message}")
        false
    } catch (e: SecurityException) {
        Log.w(WebApp.profile.appId, "Shrimpl refused ${list.id}: ${e.message}")
        false
    }

    private fun ownVersion(context: Context, list: FilterList): Long =
        File(dir(context), "${list.id}.version").takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: 0L

    private fun listFile(context: Context, list: FilterList) = File(dir(context), "${list.id}.txt")

    private fun dir(context: Context) = File(context.noBackupFilesDir, "adblock").apply { mkdirs() }
}
