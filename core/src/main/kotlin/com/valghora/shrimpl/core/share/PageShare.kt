package com.valghora.shrimpl.core.share

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * What a page shares through `navigator.share` (docs/TEMPLATE_SPEC.md T6.16), ready for Android's share
 * sheet: [subject] from the title, [text] the page's text with its link after it.
 */
data class ShareContent(val id: Int, val subject: String, val text: String)

/** Reads a share request the page posted (T6.16). */
object PageShare {
    private const val MAX_TEXT = 100_000
    private const val MAX_SUBJECT = 500

    private val JSON = Json { ignoreUnknownKeys = true }

    @Serializable
    private class Request(val id: Int, val title: String = "", val text: String = "", val url: String = "")

    /**
     * The content of [message], or null when it is malformed or has nothing to share. The link goes on
     * its own line after the text, unless the text already contains it; with neither, the title is the
     * text, since many share targets read only the text.
     */
    fun parse(message: String): ShareContent? {
        val request = try {
            JSON.decodeFromString<Request>(message)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        val title = request.title.trim()
        val text = request.text.trim()
        val url = request.url.trim()
        val body = listOf(text, url.takeUnless { it.isEmpty() || it in text }.orEmpty())
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .ifEmpty { title }
        if (body.isEmpty()) return null
        return ShareContent(request.id, title.take(MAX_SUBJECT), body.take(MAX_TEXT))
    }
}
