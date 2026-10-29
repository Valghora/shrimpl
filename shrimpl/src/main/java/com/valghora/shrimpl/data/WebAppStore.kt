package com.valghora.shrimpl.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.valghora.shrimpl.core.records.IconRecord
import com.valghora.shrimpl.core.records.WebAppRecord
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The web apps Shrimpl built (docs/SHRIMPL_SPEC.md R11.1): one directory per app under
 * `files/webapps/<appId>/` with `record.json` (written atomically) and, for image icons, the image.
 */
class WebAppStore(context: Context) {
    private val root = File(context.filesDir, "webapps")
    private val _records = MutableStateFlow(load())

    /** Every record, sorted by name. */
    val records: StateFlow<List<WebAppRecord>> = _records.asStateFlow()

    fun get(appId: String): WebAppRecord? = _records.value.firstOrNull { it.appId == appId }

    fun takenIds(): Set<String> = _records.value.mapTo(mutableSetOf()) { it.appId }

    /** The record's icon image, for image icons. */
    fun image(appId: String): ByteArray = File(directory(appId), IconRecord.IMAGE_FILE).readBytes()

    /** Saves [record], and [image] as its icon image when given. */
    suspend fun save(record: WebAppRecord, image: ByteArray?) = withContext(Dispatchers.IO) {
        val dir = directory(record.appId).apply { mkdirs() }
        if (image != null) write(File(dir, IconRecord.IMAGE_FILE), image)
        write(File(dir, RECORD), WebAppRecord.encode(record).toByteArray())
        _records.value = (_records.value.filter { it.appId != record.appId } + record).sortedBy { it.profile.name.lowercase() }
    }

    suspend fun delete(appId: String) = withContext(Dispatchers.IO) {
        directory(appId).deleteRecursively()
        _records.value = _records.value.filter { it.appId != appId }
    }

    private fun directory(appId: String): File {
        require(APP_ID.matches(appId)) { "invalid app id $appId" }
        return File(root, appId)
    }

    private fun write(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(bytes)
            atomic.finishWrite(out)
        } catch (e: IOException) {
            atomic.failWrite(out)
            throw e
        }
    }

    private fun load(): List<WebAppRecord> = root.listFiles().orEmpty()
        .filter { it.isDirectory && APP_ID.matches(it.name) }
        .mapNotNull { dir ->
            try {
                WebAppRecord.decode(AtomicFile(File(dir, RECORD)).readFully().decodeToString())
            } catch (e: IOException) {
                Log.w(TAG, "Skipping ${dir.name}: ${e.message}")
                null
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Skipping ${dir.name}: ${e.message}")
                null
            }
        }
        .sortedBy { it.profile.name.lowercase() }

    private companion object {
        const val TAG = "Shrimpl"
        const val RECORD = "record.json"
        val APP_ID = Regex("[a-z][a-z0-9]*")
    }
}
