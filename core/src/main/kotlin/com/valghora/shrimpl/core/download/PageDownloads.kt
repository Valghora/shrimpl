package com.valghora.shrimpl.core.download

import java.io.IOException
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Where a page-made file (docs/TEMPLATE_SPEC.md T6.15) goes: opened once with its name and type,
 * written in order, then finished, or abandoned if the transfer fails.
 */
interface PageFileSink {
    @Throws(IOException::class)
    fun write(bytes: ByteArray)

    @Throws(IOException::class)
    fun finish()

    /** Drops what was written; called at most once, and never after [finish]. */
    fun abandon()
}

/** What a message from the page did to its transfer (T6.15). */
sealed interface TransferEvent<out S> {
    /** The file is complete in [sink], under [name]. */
    data class Saved<S>(val name: String, val sink: S) : TransferEvent<S>

    /** The file named [name] couldn't be saved; whatever was written is gone. */
    data class Failed(val name: String) : TransferEvent<Nothing>

    /** Nothing to report yet (a chunk arrived) or the message wasn't understood. */
    data object None : TransferEvent<Nothing>
}

/**
 * Receives the `blob:` and `data:` downloads a page makes itself (T6.15): the page reads the file and
 * posts it in base64 chunks; each transfer opens its sink with a safe file name ([fileName]) and the
 * file's type. Not thread-safe: the template calls it from one background thread.
 */
class PageDownloads<S : PageFileSink>(private val open: (name: String, mimeType: String) -> S?) {

    private class Transfer<S>(val name: String, val sink: S, var written: Long)

    private val transfers = mutableMapOf<Int, Transfer<S>>()

    /** Handles one message from the page; malformed ones are ignored. */
    fun handle(message: String): TransferEvent<S> {
        val parsed = try {
            JSON.decodeFromString<Message>(message)
        } catch (_: SerializationException) {
            return TransferEvent.None
        } catch (_: IllegalArgumentException) {
            return TransferEvent.None
        }
        return when (parsed.type) {
            START -> start(parsed)
            CHUNK -> chunk(parsed)
            END -> end(parsed.id)
            ERROR -> transfers.remove(parsed.id)?.let(::fail) ?: TransferEvent.Failed(fileName(parsed.name, parsed.mimeType))
            else -> TransferEvent.None
        }
    }

    private fun start(message: Message): TransferEvent<S> {
        transfers.remove(message.id)?.let(::fail)
        val name = fileName(message.name, message.mimeType)
        val sink = open(name, message.mimeType.ifBlank { DEFAULT_TYPE }) ?: return TransferEvent.Failed(name)
        transfers[message.id] = Transfer(name, sink, 0)
        return TransferEvent.None
    }

    private fun chunk(message: Message): TransferEvent<S> {
        val transfer = transfers[message.id] ?: return TransferEvent.None
        val written = try {
            val bytes = Base64.getDecoder().decode(message.data)
            transfer.written += bytes.size
            if (transfer.written <= MAX_BYTES) transfer.sink.write(bytes)
            transfer.written <= MAX_BYTES
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IOException) {
            false
        }
        if (written) return TransferEvent.None
        transfers.remove(message.id)
        return fail(transfer)
    }

    private fun end(id: Int): TransferEvent<S> {
        val transfer = transfers.remove(id) ?: return TransferEvent.None
        return try {
            transfer.sink.finish()
            TransferEvent.Saved(transfer.name, transfer.sink)
        } catch (_: IOException) {
            fail(transfer)
        }
    }

    private fun fail(transfer: Transfer<S>): TransferEvent<S> {
        transfer.sink.abandon()
        return TransferEvent.Failed(transfer.name)
    }

    @Serializable
    private class Message(
        @SerialName("t") val type: String,
        val id: Int,
        val name: String = "",
        @SerialName("type") val mimeType: String = "",
        val data: String = "",
    )

    companion object {
        /** A transfer that grows past this is dropped, so a runaway page can't fill the device. */
        const val MAX_BYTES = 1L shl 30

        private const val START = "start"
        private const val CHUNK = "chunk"
        private const val END = "end"
        private const val ERROR = "error"
        private const val DEFAULT_TYPE = "application/octet-stream"
        private const val FALLBACK_NAME = "download"
        private const val MAX_NAME = 120

        private val JSON = Json { ignoreUnknownKeys = true }

        /** Common types a page saves, for a name the page gave without an extension. */
        private val EXTENSIONS = mapOf(
            "image/png" to "png",
            "image/jpeg" to "jpg",
            "image/gif" to "gif",
            "image/webp" to "webp",
            "image/svg+xml" to "svg",
            "application/pdf" to "pdf",
            "application/json" to "json",
            "application/zip" to "zip",
            "text/plain" to "txt",
            "text/csv" to "csv",
            "text/html" to "html",
            "video/mp4" to "mp4",
            "video/webm" to "webm",
            "audio/mpeg" to "mp3",
        )

        /**
         * A file name that is safe to save: the page's [suggested] name without path separators or
         * characters file systems reject, at most 120 characters with its extension kept, "download"
         * when nothing is left, and an extension from [mimeType] when it has none.
         */
        fun fileName(suggested: String?, mimeType: String?): String {
            val cleaned = suggested.orEmpty()
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .map { if (it.code < 0x20 || it in RESERVED) '_' else it }
                .joinToString("")
                .trim(' ', '.')
            val base = cleaned.ifEmpty { FALLBACK_NAME }
            val type = mimeType.orEmpty().substringBefore(';').trim().lowercase()
            val named = if ('.' !in base) EXTENSIONS[type]?.let { "$base.$it" } ?: base else base
            if (named.length <= MAX_NAME) return named
            val extension = named.substringAfterLast('.', "").take(MAX_EXTENSION)
            val stem = named.dropLast(if (extension.isEmpty()) 0 else extension.length + 1)
            return if (extension.isEmpty()) stem.take(MAX_NAME) else stem.take(MAX_NAME - extension.length - 1) + "." + extension
        }

        private const val MAX_EXTENSION = 10
        private const val RESERVED = ":*?\"<>|"
    }
}
