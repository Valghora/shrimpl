package com.valghora.shrimpl.webapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.WebView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.valghora.shrimpl.core.DesktopSessionController
import com.valghora.shrimpl.core.UrlClass
import com.valghora.shrimpl.core.download.PageDownloads
import com.valghora.shrimpl.core.download.PageFileSink
import com.valghora.shrimpl.core.download.TransferEvent
import com.valghora.shrimpl.core.profile.allowedOriginRules
import java.io.OutputStream
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * Saves the files a page makes itself, `blob:` and `data:` downloads (docs/TEMPLATE_SPEC.md T6.15): a
 * document-start script remembers the name each download link gives and, when WebView reports the
 * download, reads the file and posts it in chunks to a `WebMessageListener` limited to the internal
 * origins. The file goes to Downloads through MediaStore, which needs no storage permission, then a
 * notification opens it.
 */
class PageDownloadBridge(context: Context, private val controller: DesktopSessionController) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** Writes run here, in the order the page posts them, off the main thread. */
    private val io = Executors.newSingleThreadExecutor()

    private val downloads = PageDownloads(::openSink)

    private val listener = WebViewCompat.WebMessageListener { _, message, sourceOrigin, _, _ ->
        if (controller.classify(sourceOrigin.toString()) == UrlClass.INTERNAL) {
            message.data?.let { data -> io.execute { report(downloads.handle(data)) } }
        }
    }

    /**
     * Adds the listener and script to [webView] before its first load, if WebView supports both (T6.15);
     * without them, page-made downloads keep showing the unsupported toast.
     */
    fun install(webView: WebView) {
        // Invariant: each androidx.webkit call sits directly inside its own isFeatureSupported check,
        // nested, never `a && b`: lint's RequiresFeature check can't follow anything else (T1.3).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addWebMessageListener(webView, BRIDGE, WebApp.profile.allowedOriginRules, listener)
                WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, WebApp.profile.allowedOriginRules)
            }
        }
    }

    /**
     * Asks the page in [webView] to send the file at [url]; [onResult] gets false when the page can't
     * (the script isn't there: another origin, or a WebView without the features).
     */
    fun save(webView: WebView, url: String, onResult: (Boolean) -> Unit) {
        webView.evaluateJavascript("window.$SAVE ? window.$SAVE(${JSONObject.quote(url)}) : false") { result ->
            onResult(result == "true")
        }
    }

    /** A pending MediaStore entry in Downloads; it appears to other apps only once finished. */
    private inner class DownloadSink(val uri: Uri, private val stream: OutputStream) : PageFileSink {
        override fun write(bytes: ByteArray) = stream.write(bytes)

        override fun finish() {
            stream.close()
            appContext.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        }

        override fun abandon() {
            runCatching { stream.close() }
            appContext.contentResolver.delete(uri, null, null)
        }
    }

    private fun openSink(name: String, mimeType: String): DownloadSink? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = appContext.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        val stream = resolver.openOutputStream(uri)
        if (stream == null) {
            resolver.delete(uri, null, null)
            return null
        }
        return DownloadSink(uri, stream)
    }

    private fun report(event: TransferEvent<DownloadSink>) {
        when (event) {
            is TransferEvent.Saved -> main.post { notifySaved(event.name, event.sink.uri) }
            is TransferEvent.Failed -> main.post { toast(appContext.getString(R.string.toast_save_failed, event.name)) }
            TransferEvent.None -> Unit
        }
    }

    /** A completion notification that opens the file, as the download manager's does; a toast without permission. */
    private fun notifySaved(name: String, uri: Uri) {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            toast(appContext.getString(R.string.toast_saved, name))
            return
        }
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, appContext.getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW),
        )
        val open = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, appContext.contentResolver.getType(uri))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val id = uri.hashCode()
        val contentIntent = PendingIntent.getActivity(appContext, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(appContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setColor(WebApp.profile.iconBackground.toColorInt())
            .setContentTitle(name)
            .setContentText(appContext.getString(R.string.notification_saved))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(appContext).notify(id, notification)
    }

    private fun toast(message: String) = Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val BRIDGE = "AndroidDownload"
        const val SAVE = "__shrimplSaveDownload"
        const val CHANNEL = "downloads"

        /**
         * Remembers each download link's name and, for a `blob:` link, takes hold of the file at once:
         * sites often revoke the link right after the click, before WebView reports the download.
         * Files go out in 512 KiB chunks so a large one never sits whole in one message.
         */
        const val SCRIPT = """
(function () {
  if (window.__shrimplSaveDownload) return;
  var pending = new Map();
  function remember(a) {
    try {
      if (!a || !a.href || !a.hasAttribute('download')) return;
      var href = a.href;
      var entry = { name: a.getAttribute('download') || '' };
      if (href.startsWith('blob:') || href.startsWith('data:')) {
        entry.file = fetch(href).then(function (r) { return r.blob(); });
        entry.file.catch(function () {});
      }
      pending.set(href, entry);
      setTimeout(function () { if (pending.get(href) === entry) pending.delete(href); }, 120000);
    } catch (e) {}
  }
  var click = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () { remember(this); return click.apply(this, arguments); };
  document.addEventListener('click', function (e) {
    var path = e.composedPath ? e.composedPath() : [];
    for (var i = 0; i < path.length; i++) {
      if (path[i] instanceof HTMLAnchorElement) { remember(path[i]); return; }
    }
  }, true);
  var next = 0;
  var CHUNK = 524288;
  function post(message) { AndroidDownload.postMessage(JSON.stringify(message)); }
  function encode(bytes) {
    var s = '';
    for (var i = 0; i < bytes.length; i += 32768) s += String.fromCharCode.apply(null, bytes.subarray(i, i + 32768));
    return btoa(s);
  }
  window.__shrimplSaveDownload = function (href) {
    var id = ++next;
    var entry = pending.get(href) || { name: '' };
    pending.delete(href);
    var file = entry.file || fetch(href).then(function (r) { return r.blob(); });
    file.then(async function (blob) {
      post({ t: 'start', id: id, name: entry.name, type: blob.type || '' });
      for (var offset = 0; offset < blob.size; offset += CHUNK) {
        var bytes = new Uint8Array(await blob.slice(offset, offset + CHUNK).arrayBuffer());
        post({ t: 'chunk', id: id, data: encode(bytes) });
      }
      post({ t: 'end', id: id });
    }).catch(function () { post({ t: 'error', id: id, name: entry.name }); });
    return true;
  };
})();
"""
    }
}
