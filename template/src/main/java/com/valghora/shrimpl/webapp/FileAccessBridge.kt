package com.valghora.shrimpl.webapp

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.valghora.shrimpl.core.DesktopSessionController
import com.valghora.shrimpl.core.UrlClass
import com.valghora.shrimpl.core.files.FileAccess
import com.valghora.shrimpl.core.files.FileRequest
import com.valghora.shrimpl.core.profile.allowedOriginRules
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single-file File System Access (docs/TEMPLATE_SPEC.md T6.17), which WebView lacks: a document-start
 * script on the internal origins adds `showOpenFilePicker` and `showSaveFilePicker`, whose file handles
 * read and write through a `WebMessageListener`. The pickers are Android's Open and Save dialogs; each
 * file the user picks gets a handle key for this session, so the site can save back to the same file.
 * A write arrives whole when the site closes its stream, goes to a temporary file, and only then
 * replaces the picked file, so a failed save leaves it as it was.
 */
class FileAccessBridge(private val activity: ComponentActivity, private val controller: DesktopSessionController) {

    private val main = Handler(Looper.getMainLooper())

    /** Reads and writes run here, in the order the page sends them. */
    private val io = Executors.newSingleThreadExecutor()

    /** The files the user picked this session, by handle key; main thread only. */
    private val handles = mutableMapOf<String, Uri>()

    /** Writes in progress, by id, and those that went wrong; IO thread only. */
    private val writesDir = File(activity.cacheDir, WRITES_DIR)
    private val failedWrites = mutableSetOf<Int>()

    /** The picker the page is waiting on; one at a time, as in a browser. */
    private class Pending(val id: Int, val reply: JavaScriptReplyProxy)

    private var pending: Pending? = null

    private val openLauncher = activity.activityResultRegistry.register(KEY_OPEN, ActivityResultContracts.StartActivityForResult(), ::onPicked)
    private val saveLauncher = activity.activityResultRegistry.register(KEY_SAVE, ActivityResultContracts.StartActivityForResult(), ::onPicked)

    private val listener = WebViewCompat.WebMessageListener { _, message, sourceOrigin, _, reply ->
        if (controller.classify(sourceOrigin.toString()) == UrlClass.INTERNAL) {
            message.data?.let(FileAccess::parse)?.let { handle(it, reply) }
        }
    }

    init {
        // Writes left by a session that ended mid-save.
        io.execute {
            writesDir.deleteRecursively()
            writesDir.mkdirs()
        }
    }

    /** Adds the listener and script to [webView] before its first load, if WebView supports both (T6.17). */
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

    /** Stops the pickers' result delivery and the IO thread; the activity is going away. */
    fun release() {
        openLauncher.unregister()
        saveLauncher.unregister()
        io.shutdown()
    }

    private fun handle(request: FileRequest, reply: JavaScriptReplyProxy) {
        when (request) {
            is FileRequest.Open -> pick(request.id, reply) {
                openLauncher.launch(
                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        // Every file: sites' own types are often unknown to Android and would hide files.
                        .setType("*/*")
                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, request.multiple),
                )
            }
            is FileRequest.Save -> pick(request.id, reply) {
                saveLauncher.launch(
                    Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(request.mimeType)
                        .putExtra(Intent.EXTRA_TITLE, request.name),
                )
            }
            is FileRequest.Read -> withFile(request.id, request.handle, reply) { uri -> io.execute { read(request.id, uri, reply) } }
            is FileRequest.Write -> withFile(request.id, request.handle, reply) { io.execute { append(request.id, request.bytes) } }
            is FileRequest.Invalid -> io.execute { failedWrites += request.id }
            is FileRequest.Commit -> withFile(request.id, request.handle, reply) { uri -> io.execute { commit(request.id, uri, reply) } }
        }
    }

    private fun pick(id: Int, reply: JavaScriptReplyProxy, launch: () -> Unit) {
        if (pending != null) {
            fail(reply, id, "NotAllowedError", "A file picker is already open")
            return
        }
        pending = Pending(id, reply)
        try {
            launch()
        } catch (_: ActivityNotFoundException) {
            pending = null
            fail(reply, id, "AbortError", "No app can pick files")
        }
    }

    /** Gives the page handles for the files the user picked, or an AbortError when they backed out. */
    private fun onPicked(result: ActivityResult) {
        val waiting = pending ?: return
        pending = null
        val data = result.data
        val uris = buildList {
            data?.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(::add) }
            data?.data?.let { if (it !in this) add(it) }
        }
        if (result.resultCode != Activity.RESULT_OK || uris.isEmpty()) {
            fail(waiting.reply, waiting.id, "AbortError", "The user aborted a request")
            return
        }
        val picked = JSONArray()
        for (uri in uris) {
            val key = UUID.randomUUID().toString()
            handles[key] = uri
            picked.put(JSONObject().put("key", key).put("name", displayName(uri)))
        }
        waiting.reply.postMessage(JSONObject().put("id", waiting.id).put("ok", true).put("handles", picked).toString())
    }

    private fun withFile(id: Int, handle: String, reply: JavaScriptReplyProxy, block: (Uri) -> Unit) {
        val uri = handles[handle]
        if (uri == null) fail(reply, id, "NotFoundError", "The file is no longer available; pick it again") else block(uri)
    }

    /** Sends the file in 512 KiB parts after its details, as the page builds a File from them. */
    private fun read(id: Int, uri: Uri, reply: JavaScriptReplyProxy) {
        try {
            val resolver = activity.contentResolver
            val meta = JSONObject()
                .put("id", id)
                .put("t", "meta")
                .put("name", displayName(uri))
                .put("type", resolver.getType(uri).orEmpty())
                .put("lastModified", lastModified(uri))
            post(reply, meta)
            val stream = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
            stream.use { input ->
                val buffer = ByteArray(CHUNK)
                while (true) {
                    val count = input.readNBytes(buffer, 0, CHUNK)
                    if (count <= 0) break
                    post(reply, JSONObject().put("id", id).put("t", "chunk").put("data", Base64.encodeToString(buffer, 0, count, Base64.NO_WRAP)))
                }
            }
            post(reply, JSONObject().put("id", id).put("t", "end"))
        } catch (e: IOException) {
            main.post { fail(reply, id, "NotReadableError", e.message ?: "The file can't be read") }
        } catch (_: SecurityException) {
            main.post { fail(reply, id, "NotAllowedError", "Access to the file was lost; pick it again") }
        }
    }

    private fun append(id: Int, bytes: ByteArray) {
        if (id in failedWrites) return
        try {
            File(writesDir, id.toString()).appendBytes(bytes)
        } catch (_: IOException) {
            failedWrites += id
        }
    }

    /** Replaces the picked file with the write's content, then answers the page. */
    private fun commit(id: Int, uri: Uri, reply: JavaScriptReplyProxy) {
        val staged = File(writesDir, id.toString())
        try {
            if (failedWrites.remove(id)) throw IOException("Part of the file didn't arrive")
            openForReplace(uri).use { output -> if (staged.exists()) staged.inputStream().use { it.copyTo(output) } }
            post(reply, JSONObject().put("id", id).put("ok", true))
        } catch (e: IOException) {
            main.post { fail(reply, id, "NotReadableError", e.message ?: "The file can't be saved") }
        } catch (_: SecurityException) {
            main.post { fail(reply, id, "NotAllowedError", "Access to the file was lost; pick it again") }
        } finally {
            staged.delete()
        }
    }

    /** Truncating write ("wt"); providers without it get "w", which on those replaces the file too. */
    private fun openForReplace(uri: Uri): OutputStream {
        val resolver = activity.contentResolver
        val stream = try {
            resolver.openOutputStream(uri, "wt")
        } catch (_: IllegalArgumentException) {
            resolver.openOutputStream(uri, "w")
        } catch (_: UnsupportedOperationException) {
            resolver.openOutputStream(uri, "w")
        }
        return stream ?: throw FileNotFoundException(uri.toString())
    }

    private fun displayName(uri: Uri): String =
        activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()

    private fun lastModified(uri: Uri): Long =
        activity.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        } ?: System.currentTimeMillis()

    /** Reply proxies are used on the main thread. */
    private fun post(reply: JavaScriptReplyProxy, message: JSONObject) {
        val text = message.toString()
        main.post { reply.postMessage(text) }
    }

    private fun fail(reply: JavaScriptReplyProxy, id: Int, error: String, message: String) {
        reply.postMessage(JSONObject().put("id", id).put("ok", false).put("error", error).put("message", message).toString())
    }

    private companion object {
        const val BRIDGE = "AndroidFiles"
        const val KEY_OPEN = "file-access-open"
        const val KEY_SAVE = "file-access-save"
        const val WRITES_DIR = "file-writes"
        const val CHUNK = 512 * 1024

        /**
         * `showOpenFilePicker` and `showSaveFilePicker` with file handles: `getFile` reads the file,
         * `createWritable` gives a WritableStream (so `blob.stream().pipeTo(writable)` works) that also
         * has `write`, `seek` and `truncate`, and sends the result on close.
         */
        const val SCRIPT = """
(function () {
  if (window.showOpenFilePicker || window.showSaveFilePicker || !window.AndroidFiles) return;
  var next = 0;
  var waiting = new Map();
  var CHUNK = 524288;
  function send(message) { AndroidFiles.postMessage(JSON.stringify(message)); }
  function request(message) {
    var id = message.id || ++next;
    message.id = id;
    return new Promise(function (resolve, reject) {
      waiting.set(id, { resolve: resolve, reject: reject });
      send(message);
    });
  }
  function decode(text) {
    var s = atob(text);
    var bytes = new Uint8Array(s.length);
    for (var i = 0; i < s.length; i++) bytes[i] = s.charCodeAt(i);
    return bytes;
  }
  function encode(bytes) {
    var s = '';
    for (var i = 0; i < bytes.length; i += 32768) s += String.fromCharCode.apply(null, bytes.subarray(i, i + 32768));
    return btoa(s);
  }
  AndroidFiles.onmessage = function (event) {
    var m = JSON.parse(event.data);
    var w = waiting.get(m.id);
    if (!w) return;
    if (m.t === 'meta') { w.meta = m; w.parts = []; return; }
    if (m.t === 'chunk') { w.parts.push(decode(m.data)); return; }
    waiting.delete(m.id);
    if (m.t === 'end') {
      w.resolve(new File(w.parts, w.meta.name, { type: w.meta.type, lastModified: w.meta.lastModified }));
    } else if (m.ok) {
      w.resolve(m);
    } else {
      w.reject(new DOMException(m.message || m.error, m.error || 'AbortError'));
    }
  };
  async function bytesOf(value) {
    if (typeof value === 'string') return new TextEncoder().encode(value);
    if (value instanceof Blob) return new Uint8Array(await value.arrayBuffer());
    if (value instanceof ArrayBuffer) return new Uint8Array(value);
    if (ArrayBuffer.isView(value)) return new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
    throw new TypeError('Unsupported data for write()');
  }
  function isData(value) {
    return typeof value === 'string' || value instanceof Blob || value instanceof ArrayBuffer || ArrayBuffer.isView(value);
  }
  class ShrimplFileHandle {
    constructor(key, name) {
      this.kind = 'file';
      this.name = name;
      Object.defineProperty(this, '_key', { value: key });
    }
    isSameEntry(other) { return Promise.resolve(!!other && other._key === this._key); }
    queryPermission() { return Promise.resolve('granted'); }
    requestPermission() { return Promise.resolve('granted'); }
    getFile() { return request({ t: 'read', handle: this._key }); }
    async createWritable(options) {
      var key = this._key;
      var data = new Uint8Array(0);
      var length = 0;
      var position = 0;
      if (options && options.keepExistingData) {
        data = new Uint8Array(await (await this.getFile()).arrayBuffer());
        length = data.length;
      }
      function ensure(size) {
        if (size <= data.length) return;
        var grown = new Uint8Array(Math.max(size, data.length * 2, 1024));
        grown.set(data.subarray(0, length));
        data = grown;
      }
      async function apply(chunk) {
        if (!isData(chunk) && chunk && typeof chunk === 'object') {
          if (chunk.type === 'seek') { position = chunk.position; return; }
          if (chunk.type === 'truncate') {
            ensure(chunk.size);
            if (chunk.size > length) data.fill(0, length, chunk.size);
            length = chunk.size;
            if (position > length) position = length;
            return;
          }
          if (chunk.type !== 'write') throw new TypeError('Unknown write() command');
          if (chunk.position !== undefined && chunk.position !== null) position = chunk.position;
          chunk = chunk.data;
        }
        var bytes = await bytesOf(chunk);
        ensure(position + bytes.length);
        if (position > length) data.fill(0, length, position);
        data.set(bytes, position);
        position += bytes.length;
        if (position > length) length = position;
      }
      var stream = new WritableStream({
        write: apply,
        close: function () {
          var id = ++next;
          for (var offset = 0; offset < length; offset += CHUNK) {
            send({ t: 'write', id: id, handle: key, data: encode(data.subarray(offset, Math.min(length, offset + CHUNK))) });
          }
          return request({ t: 'commit', id: id, handle: key }).then(function () {});
        }
      });
      stream.write = function (value) {
        var writer = stream.getWriter();
        return writer.write(value).finally(function () { writer.releaseLock(); });
      };
      stream.seek = function (offset) { return stream.write({ type: 'seek', position: offset }); };
      stream.truncate = function (size) { return stream.write({ type: 'truncate', size: size }); };
      return stream;
    }
  }
  function handlesOf(answer) {
    return answer.handles.map(function (h) { return new ShrimplFileHandle(h.key, h.name); });
  }
  window.showOpenFilePicker = function (options) {
    return request({ t: 'open', multiple: !!(options && options.multiple) }).then(handlesOf);
  };
  window.showSaveFilePicker = function (options) {
    var type = '';
    ((options && options.types) || []).some(function (t) {
      return Object.keys((t && t.accept) || {}).some(function (k) {
        if (k.indexOf('*') >= 0) return false;
        type = k;
        return true;
      });
    });
    var name = String((options && options.suggestedName) || '');
    return request({ t: 'save', name: name, type: type }).then(function (answer) { return handlesOf(answer)[0]; });
  };
})();
"""
    }
}
