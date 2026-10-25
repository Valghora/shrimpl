package com.valghora.uniwrap.discovery

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** A downloaded document: where it ended up, how it got there, and what came back. */
class Fetched(
    val finalUrl: String,
    /** Every URL requested, the original first. */
    val redirectChain: List<String>,
    val status: Int,
    /** Response headers with lowercase names. */
    val headers: Map<String, String>,
    val body: ByteArray,
)

/**
 * Discovery's HTTP client (docs/UNIWRAP_SPEC.md R9.4): HTTPS only, a 15 second timeout per request,
 * a size cap per response, redirects followed by hand so the chain is known, and no cookies.
 */
class Network(private val userAgent: String) {
    fun get(url: String, maxBytes: Int = MAX_BYTES): Fetched {
        var current = url
        val chain = mutableListOf<String>()
        repeat(MAX_REDIRECTS) {
            if (!current.startsWith("https://")) throw IOException("Only https:// URLs are fetched ($current)")
            chain += current
            val connection = URL(current).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Accept-Language", "en")
                val status = connection.responseCode
                val location = connection.getHeaderField("Location")
                if (status in 300..399 && location != null) {
                    current = URI(current).resolve(location.trim()).toString()
                    return@repeat
                }
                val headers = connection.headerFields
                    .filterKeys { it != null }
                    .mapKeys { (key, _) -> key.lowercase() }
                    .mapValues { (_, values) -> values.joinToString(", ") }
                val stream = if (status >= 400) connection.errorStream else connection.inputStream
                val body = stream?.use { read(it, maxBytes) } ?: ByteArray(0)
                return Fetched(current, chain, status, headers, body)
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many redirects from $url")
    }

    /** The body of a successful (2xx) response, or null on any failure. */
    fun bodyOrNull(url: String, maxBytes: Int = MAX_BYTES): ByteArray? = try {
        get(url, maxBytes).takeIf { it.status in 200..299 }?.body
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun read(stream: java.io.InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            if (out.size() + n > maxBytes) throw IOException("Response larger than ${maxBytes / 1024} KB")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        const val TIMEOUT_MS = 15_000
        const val MAX_BYTES = 5 * 1024 * 1024
        private const val MAX_REDIRECTS = 10
    }
}
