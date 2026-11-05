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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

/**
 * File System Access (docs/TEMPLATE_SPEC.md T6.17, T6.18), which WebView lacks: a document-start script
 * on the internal origins adds `showOpenFilePicker`, `showSaveFilePicker` and `showDirectoryPicker`,
 * whose handles work through a `WebMessageListener`. The pickers are Android's Open, Save and folder
 * dialogs; each file or folder the user picks, and each entry found in such a folder, gets a handle key
 * for this session, so the site can save back to the same file. A write arrives whole when the site
 * closes its stream, goes to a temporary file, and only then replaces the file, so a failed save leaves
 * it as it was. Folder work goes through Android's document API (`DocumentsContract`) on the IO thread.
 */
class FileAccessBridge(private val activity: ComponentActivity, private val controller: DesktopSessionController) {

    private val main = Handler(Looper.getMainLooper())

    /** Reads and writes run here, in the order the page sends them. */
    private val io = Executors.newSingleThreadExecutor()

    /**
     * A handle's target: a picked file ([tree] null), or a file or folder inside a picked folder's
     * [tree], with its folder's handle as [parent] (null for the picked folder itself).
     */
    private data class Entry(val uri: Uri, val directory: Boolean, val tree: Uri?, val parent: String?)

    /** Every handle given out this session, and the key of each document, so a document keeps one key. */
    private val entries = ConcurrentHashMap<String, Entry>()
    private val keys = ConcurrentHashMap<String, String>()

    /** Writes in progress, by id, and those that went wrong; IO thread only. */
    private val writesDir = File(activity.cacheDir, WRITES_DIR)
    private val failedWrites = mutableSetOf<Int>()

    /** The picker the page is waiting on; one at a time, as in a browser. */
    private class Pending(val id: Int, val reply: JavaScriptReplyProxy)

    private var pending: Pending? = null

    private val openLauncher = activity.activityResultRegistry.register(KEY_OPEN, ActivityResultContracts.StartActivityForResult(), ::onPicked)
    private val saveLauncher = activity.activityResultRegistry.register(KEY_SAVE, ActivityResultContracts.StartActivityForResult(), ::onPicked)
    private val folderLauncher = activity.activityResultRegistry.register(KEY_FOLDER, ActivityResultContracts.StartActivityForResult(), ::onFolderPicked)

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
        folderLauncher.unregister()
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
            is FileRequest.PickFolder -> pick(request.id, reply) {
                folderLauncher.launch(
                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
                )
            }
            is FileRequest.Read -> withFile(request.id, request.handle, reply) { uri -> io.execute { read(request.id, uri, reply) } }
            is FileRequest.Write -> withFile(request.id, request.handle, reply) { io.execute { append(request.id, request.bytes) } }
            is FileRequest.Invalid -> io.execute { failedWrites += request.id }
            is FileRequest.Commit -> withFile(request.id, request.handle, reply) { uri -> io.execute { commit(request.id, uri, reply) } }
            is FileRequest.ListFolder -> folderWork(request.id, request.handle, reply) { key, folder -> list(request.id, key, folder, reply) }
            is FileRequest.Child -> folderWork(request.id, request.handle, reply) { key, folder ->
                child(request.id, key, folder, request.name, request.directory, request.create, reply)
            }
            is FileRequest.Remove -> folderWork(request.id, request.handle, reply) { _, folder ->
                remove(request.id, folder, request.name, request.recursive, reply)
            }
            is FileRequest.Move -> io.execute { guarded(request.id, reply) { move(request, reply) } }
            is FileRequest.Rejected -> fail(reply, request.id, request.error, request.message)
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
            val key = keyFor(Entry(uri, directory = false, tree = null, parent = null))
            picked.put(JSONObject().put("key", key).put("name", displayName(uri)))
        }
        waiting.reply.postMessage(JSONObject().put("id", waiting.id).put("ok", true).put("handles", picked).toString())
    }

    /** Gives the page a handle for the folder the user picked (T6.18), or an AbortError. */
    private fun onFolderPicked(result: ActivityResult) {
        val waiting = pending ?: return
        pending = null
        val tree = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || tree == null) {
            fail(waiting.reply, waiting.id, "AbortError", "The user aborted a request")
            return
        }
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val key = keyFor(Entry(root, directory = true, tree = tree, parent = null))
        val picked = JSONArray().put(JSONObject().put("key", key).put("name", displayName(root)))
        waiting.reply.postMessage(JSONObject().put("id", waiting.id).put("ok", true).put("handles", picked).toString())
    }

    /** The key for [entry]'s document: the one it already has, so `isSameEntry` holds, or a new one. */
    private fun keyFor(entry: Entry): String {
        val document = entry.uri.toString()
        keys[document]?.let { key ->
            entries[key] = entry
            return key
        }
        val key = UUID.randomUUID().toString()
        entries[key] = entry
        keys[document] = key
        return key
    }

    private fun withFile(id: Int, handle: String, reply: JavaScriptReplyProxy, block: (Uri) -> Unit) {
        val entry = entries[handle]
        when {
            entry == null -> fail(reply, id, "NotFoundError", "The file is no longer available; pick it again")
            entry.directory -> fail(reply, id, "TypeMismatchError", "This is a folder")
            else -> block(entry.uri)
        }
    }

    /** Runs [work] on the IO thread for [handle]'s folder, with Android's errors answered as the standard's. */
    private fun folderWork(id: Int, handle: String, reply: JavaScriptReplyProxy, work: (String, Entry) -> Unit) {
        val entry = entries[handle]
        when {
            entry == null -> fail(reply, id, "NotFoundError", "The folder is no longer available; pick it again")
            !entry.directory || entry.tree == null -> fail(reply, id, "TypeMismatchError", "This is not a folder")
            else -> io.execute { guarded(id, reply) { work(handle, entry) } }
        }
    }

    private fun guarded(id: Int, reply: JavaScriptReplyProxy, work: () -> Unit) {
        try {
            work()
        } catch (e: FileNotFoundException) {
            main.post { fail(reply, id, "NotFoundError", e.message ?: "Not found") }
        } catch (_: SecurityException) {
            main.post { fail(reply, id, "NotAllowedError", "Access to the folder was lost; pick it again") }
        } catch (_: UnsupportedOperationException) {
            main.post { fail(reply, id, "NotSupportedError", "The folder's storage doesn't allow this") }
        } catch (_: IllegalArgumentException) {
            main.post { fail(reply, id, "NotFoundError", "Not found") }
        } catch (e: IllegalStateException) {
            main.post { fail(reply, id, "InvalidModificationError", e.message ?: "Can't change this entry") }
        }
    }

    /** A folder's entries: document, name and whether it is a folder. */
    private data class Child(val uri: Uri, val name: String, val directory: Boolean)

    private fun children(folder: Entry): List<Child> {
        val tree = requireNotNull(folder.tree)
        val listing = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(folder.uri))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        return activity.contentResolver.query(listing, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                    add(Child(uri, cursor.getString(1).orEmpty(), cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR))
                }
            }
        } ?: throw FileNotFoundException("The folder can't be read")
    }

    private fun handleJson(folderKey: String, folder: Entry, child: Child): JSONObject {
        val key = keyFor(Entry(child.uri, child.directory, folder.tree, folderKey))
        return JSONObject().put("key", key).put("name", child.name).put("kind", if (child.directory) "directory" else "file")
    }

    private fun list(id: Int, key: String, folder: Entry, reply: JavaScriptReplyProxy) {
        val found = JSONArray()
        children(folder).forEach { found.put(handleJson(key, folder, it)) }
        post(reply, JSONObject().put("id", id).put("ok", true).put("entries", found))
    }

    private fun child(id: Int, key: String, folder: Entry, name: String, directory: Boolean, create: Boolean, reply: JavaScriptReplyProxy) {
        val existing = children(folder).firstOrNull { it.name == name }
        val found = when {
            existing != null && existing.directory != directory -> {
                main.post { fail(reply, id, "TypeMismatchError", if (directory) "$name is a file" else "$name is a folder") }
                return
            }
            existing != null -> existing
            !create -> throw FileNotFoundException("$name was not found")
            else -> {
                // A generic type keeps the name exactly as given; a known one can make Android add its extension.
                val type = if (directory) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream"
                val created = DocumentsContract.createDocument(activity.contentResolver, folder.uri, type, name)
                    ?: throw IllegalStateException("$name can't be created here")
                Child(created, displayName(created), directory)
            }
        }
        post(reply, JSONObject().put("id", id).put("ok", true).put("handles", JSONArray().put(handleJson(key, folder, found))))
    }

    private fun remove(id: Int, folder: Entry, name: String, recursive: Boolean, reply: JavaScriptReplyProxy) {
        val target = children(folder).firstOrNull { it.name == name } ?: throw FileNotFoundException("$name was not found")
        if (target.directory && !recursive && children(Entry(target.uri, true, folder.tree, null)).isNotEmpty()) {
            throw IllegalStateException("$name isn't empty")
        }
        if (!DocumentsContract.deleteDocument(activity.contentResolver, target.uri)) throw IllegalStateException("$name can't be removed")
        keys.remove(target.uri.toString())?.let(entries::remove)
        post(reply, JSONObject().put("id", id).put("ok", true))
    }

    /**
     * Moves an entry into another folder of the same picked folder, then renames it; Android may give
     * it a new document, which keeps the handle's key. An existing entry with the new name isn't replaced.
     */
    private fun move(request: FileRequest.Move, reply: JavaScriptReplyProxy) {
        val entry = entries[request.handle] ?: throw FileNotFoundException("The entry is no longer available")
        val parentKey = entry.parent ?: throw IllegalStateException("A picked file or folder can't be moved")
        val targetKey = request.parent ?: parentKey
        val target = entries[targetKey] ?: throw FileNotFoundException("The folder is no longer available")
        if (!target.directory || target.tree != entry.tree) throw IllegalStateException("It can only move within the picked folder")
        val resolver = activity.contentResolver
        val currentName = displayName(entry.uri)
        if (children(target).any { it.name == request.name && it.uri != entry.uri }) throw IllegalStateException("${request.name} already exists")
        var uri = entry.uri
        if (targetKey != parentKey) {
            val source = entries[parentKey] ?: throw FileNotFoundException("The folder is no longer available")
            uri = DocumentsContract.moveDocument(resolver, uri, source.uri, target.uri) ?: throw IllegalStateException("It can't be moved")
        }
        if (request.name != currentName) {
            uri = DocumentsContract.renameDocument(resolver, uri, request.name) ?: uri
        }
        keys.remove(entry.uri.toString())
        entries[request.handle] = entry.copy(uri = uri, parent = targetKey)
        keys[uri.toString()] = request.handle
        post(reply, JSONObject().put("id", request.id).put("ok", true).put("name", displayName(uri)))
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
        const val KEY_FOLDER = "file-access-folder"
        const val WRITES_DIR = "file-writes"
        const val CHUNK = 512 * 1024

        /**
         * `showOpenFilePicker`, `showSaveFilePicker` and `showDirectoryPicker`. File handles: `getFile`
         * reads the file, `createWritable` gives a WritableStream (so `blob.stream().pipeTo(writable)`
         * works) that also has `write`, `seek` and `truncate`, and sends the result on close. Folder
         * handles list, find, create and remove entries; handles keep their path from the picked folder
         * for `resolve`, and `move` renames or moves them within it.
         */
        const val SCRIPT = """
(function () {
  if (window.showOpenFilePicker || window.showSaveFilePicker || window.showDirectoryPicker || !window.AndroidFiles) return;
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
    } else if (m.error === 'TypeError') {
      w.reject(new TypeError(m.message));
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
  class ShrimplHandle {
    constructor(kind, key, name, root, path) {
      this.kind = kind;
      this.name = name;
      Object.defineProperty(this, '_key', { value: key });
      Object.defineProperty(this, '_root', { value: root || key });
      Object.defineProperty(this, '_path', { value: path || [], writable: true });
    }
    isSameEntry(other) { return Promise.resolve(!!other && other._key === this._key); }
    queryPermission() { return Promise.resolve('granted'); }
    requestPermission() { return Promise.resolve('granted'); }
    move(a, b) {
      var self = this;
      var parent = typeof a === 'string' ? null : a;
      var name = typeof a === 'string' ? a : (b === undefined ? this.name : b);
      return request({ t: 'move', handle: this._key, parent: parent ? parent._key : '', name: String(name) }).then(function (answer) {
        self.name = answer.name;
        self._path = (parent ? parent._path : self._path.slice(0, -1)).concat(answer.name);
      });
    }
  }
  function entryOf(h, folder) {
    var path = folder._path.concat(h.name);
    return h.kind === 'directory'
      ? new ShrimplDirectoryHandle(h.key, h.name, folder._root, path)
      : new ShrimplFileHandle(h.key, h.name, folder._root, path);
  }
  class ShrimplDirectoryHandle extends ShrimplHandle {
    constructor(key, name, root, path) { super('directory', key, name, root, path); }
    async getFileHandle(name, options) { return this._child(name, false, options); }
    async getDirectoryHandle(name, options) { return this._child(name, true, options); }
    async _child(name, directory, options) {
      var answer = await request({ t: 'child', handle: this._key, name: String(name), directory: directory, create: !!(options && options.create) });
      return entryOf(answer.handles[0], this);
    }
    removeEntry(name, options) {
      return request({ t: 'remove', handle: this._key, name: String(name), recursive: !!(options && options.recursive) }).then(function () {});
    }
    resolve(other) {
      if (!other || other._root !== this._root) return Promise.resolve(null);
      var mine = this._path;
      var theirs = other._path;
      for (var i = 0; i < mine.length; i++) if (mine[i] !== theirs[i]) return Promise.resolve(null);
      return Promise.resolve(theirs.length >= mine.length ? theirs.slice(mine.length) : null);
    }
    async *entries() {
      var answer = await request({ t: 'list', handle: this._key });
      for (var i = 0; i < answer.entries.length; i++) {
        var entry = entryOf(answer.entries[i], this);
        yield [entry.name, entry];
      }
    }
    async *keys() { for await (var pair of this.entries()) yield pair[0]; }
    async *values() { for await (var pair of this.entries()) yield pair[1]; }
    [Symbol.asyncIterator]() { return this.entries(); }
  }
  class ShrimplFileHandle extends ShrimplHandle {
    constructor(key, name, root, path) { super('file', key, name, root, path || [name]); }
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
  window.showDirectoryPicker = function () {
    return request({ t: 'folder' }).then(function (answer) {
      var h = answer.handles[0];
      return new ShrimplDirectoryHandle(h.key, h.name, h.key, []);
    });
  };
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
