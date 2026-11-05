package com.valghora.shrimpl.core.files

import com.valghora.shrimpl.core.download.PageDownloads
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A request from a page's file pickers and file handles (docs/TEMPLATE_SPEC.md T6.17). Handles are
 * the keys the template gave out for the files the user picked.
 */
sealed interface FileRequest {
    val id: Int

    /** Show Android's Open dialog, for one file or several. */
    data class Open(override val id: Int, val multiple: Boolean) : FileRequest

    /** Show Android's Save dialog with [name] filled in, for a file of [mimeType]. */
    data class Save(override val id: Int, val name: String, val mimeType: String) : FileRequest

    /** Send the file behind [handle] to the page. */
    data class Read(override val id: Int, val handle: String) : FileRequest

    /** The next part of what write [id] puts in [handle]'s file. */
    class Write(override val id: Int, val handle: String, val bytes: ByteArray) : FileRequest

    /** Write [id] is complete: replace [handle]'s file with everything sent for it. */
    data class Commit(override val id: Int, val handle: String) : FileRequest

    /** A part of write [id] that couldn't be read; the write must fail, not save a partial file. */
    data class Invalid(override val id: Int) : FileRequest
}

/** Reads the page's file requests (T6.17). */
object FileAccess {
    private const val DEFAULT_TYPE = "application/octet-stream"
    private val JSON = Json { ignoreUnknownKeys = true }
    private val MIME = Regex("^[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*$")

    @Serializable
    private class Message(
        val t: String,
        val id: Int,
        val multiple: Boolean = false,
        val name: String = "",
        val type: String = "",
        val handle: String = "",
        val data: String = "",
    )

    /** The request in [message], or null when it is malformed or unknown. */
    fun parse(message: String): FileRequest? {
        val parsed = try {
            JSON.decodeFromString<Message>(message)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return when (parsed.t) {
            "open" -> FileRequest.Open(parsed.id, parsed.multiple)
            "save" -> saveTypeOf(parsed.type).let { type -> FileRequest.Save(parsed.id, PageDownloads.fileName(parsed.name, type), type) }
            "read" -> parsed.handle.takeIf { it.isNotEmpty() }?.let { FileRequest.Read(parsed.id, it) }
            "write" -> parsed.handle.takeIf { it.isNotEmpty() }?.let { handle ->
                try {
                    FileRequest.Write(parsed.id, handle, Base64.getDecoder().decode(parsed.data))
                } catch (_: IllegalArgumentException) {
                    FileRequest.Invalid(parsed.id)
                }
            }
            "commit" -> parsed.handle.takeIf { it.isNotEmpty() }?.let { FileRequest.Commit(parsed.id, it) }
            else -> null
        }
    }

    /**
     * The type the Save dialog creates the file as: the page's own when it is a plain MIME type, else a
     * generic one, which leaves the name as given (a known type can make Android add its extension).
     */
    private fun saveTypeOf(type: String): String = type.trim().lowercase().takeIf { MIME.matches(it) } ?: DEFAULT_TYPE
}
