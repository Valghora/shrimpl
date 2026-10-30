package com.valghora.shrimpl.core.discovery

import com.valghora.shrimpl.core.icons.IconException
import com.valghora.shrimpl.core.icons.SvgGlyph
import java.net.URLDecoder
import java.util.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Where icon bytes come from: Shrimpl downloads them over HTTPS. */
interface IconSources {
    /** The body of an http(s) URL, or null on any failure (including 404). */
    fun fetch(url: String): ByteArray?

    /** The simple-icons brand for [slug], or null if there is none. */
    fun simpleIcon(slug: String): SimpleIcon? = fetch(SimpleIcons.url(slug))?.let { SimpleIcons.parse(slug, it) }
}

/** The icon discovery picked (docs/SHRIMPL_SPEC.md R9.2). */
sealed interface IconChoice {
    val source: String
    val confidence: Confidence

    data class Glyph(
        val svg: ByteArray,
        val glyph: SvgGlyph,
        /** The brand or mask-icon color, if known. */
        val color: String?,
        val colorSource: String,
        override val source: String,
        override val confidence: Confidence,
    ) : IconChoice

    data class Raster(
        val bytes: ByteArray,
        val format: String,
        val size: Int,
        val maskable: Boolean,
        override val source: String,
        override val confidence: Confidence,
    ) : IconChoice

    data class Letter(
        val letter: Char,
        override val source: String,
        override val confidence: Confidence,
    ) : IconChoice
}

/** Width and height read from a PNG or WebP header */
data class RasterInfo(val format: String, val width: Int, val height: Int) {
    companion object {
        private val PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)

        fun of(data: ByteArray): RasterInfo {
            fun ascii(from: Int, to: Int) = if (data.size >= to) String(data, from, to - from, Charsets.ISO_8859_1) else ""
            fun le(from: Int, bytes: Int): Int = (0 until bytes).fold(0) { acc, i -> acc or ((data[from + i].toInt() and 0xFF) shl (8 * i)) }
            fun be32(from: Int): Int = (0 until 4).fold(0) { acc, i -> (acc shl 8) or (data[from + i].toInt() and 0xFF) }
            if (data.size >= 24 && data.copyOfRange(0, 8).contentEquals(PNG) && ascii(12, 16) == "IHDR") {
                return RasterInfo("png", be32(16), be32(20))
            }
            if (ascii(0, 4) == "RIFF" && ascii(8, 12) == "WEBP") {
                return when (ascii(12, 16)) {
                    "VP8X" -> if (data.size >= 30) RasterInfo("webp", 1 + le(24, 3), 1 + le(27, 3)) else null
                    "VP8L" -> if (data.size >= 25) {
                        val bits = le(21, 4)
                        RasterInfo("webp", 1 + (bits and 0x3FFF), 1 + ((bits ushr 14) and 0x3FFF))
                    } else {
                        null
                    }
                    "VP8 " -> if (data.size >= 30) RasterInfo("webp", le(26, 2) and 0x3FFF, le(28, 2) and 0x3FFF) else null
                    else -> throw IconException("Unsupported WebP variant")
                } ?: throw IconException("Truncated WebP header")
            }
            throw IconException("Not a PNG or WebP image")
        }
    }
}

/** One of the site's own icons, not yet fetched: a web manifest icon, an icon link or apple-touch-icon. */
data class WebsiteIcon(val url: String, val size: Int, val maskable: Boolean, val source: String)

/** Picks the best icon: simple-icons glyph > mask-icon glyph > raster > letter (R9.2). */
object IconChooser {
    const val MIN_RASTER_PX = 192

    // Where the site's own icons come from; an icon's source starts with one of these.
    private const val MANIFEST_ICON = "web manifest icon"
    private const val APPLE_TOUCH_ICON = "apple-touch-icon"
    private const val ICON_LINK = "page icon link"
    private const val DEFAULT_TOUCH_ICON = "/apple-touch-icon.png"

    /** True for an icon [source] (or record origin) that names one of the site's own icons. */
    fun isWebsiteSource(source: String): Boolean =
        listOf(MANIFEST_ICON, APPLE_TOUCH_ICON, ICON_LINK, DEFAULT_TOUCH_ICON).any(source::startsWith)

    /** [simple] is the brand found by name; [simpleConfidence] says how sure that match is. */
    fun choose(
        simple: SimpleIcon?,
        simpleConfidence: Confidence,
        links: List<Link>,
        manifest: JsonObject,
        manifestUrl: String,
        pageUrl: String,
        sources: IconSources,
    ): IconChoice? {
        if (simple != null) {
            val glyph = tryGlyph(simple.svg)
            if (glyph != null) {
                return IconChoice.Glyph(
                    svg = simple.svg,
                    glyph = glyph,
                    color = simple.color,
                    colorSource = "simple-icons brand color (${simple.title})",
                    source = "simple-icons '${simple.slug}'",
                    confidence = simpleConfidence,
                )
            }
        }
        for (link in links) {
            if ("mask-icon" !in link.rel.split(Regex("\\s+"))) continue
            val svg = load(link.href, sources) ?: continue
            val glyph = tryGlyph(svg) ?: continue
            return IconChoice.Glyph(
                svg = svg,
                glyph = glyph,
                color = Colors.parse(link.color),
                colorSource = "mask-icon color",
                source = "Safari pinned-tab icon (mask-icon)",
                confidence = Confidence.MEDIUM,
            )
        }

        return firstRaster(websiteIcons(links, manifest, manifestUrl, pageUrl), sources)
            // null: the caller falls back to a letter, which needs the app name
    }

    /** The site's own icons, best first (maskable, then larger); nothing is fetched. */
    fun websiteIcons(links: List<Link>, manifest: JsonObject, manifestUrl: String, pageUrl: String): List<WebsiteIcon> {
        val candidates = mutableListOf<WebsiteIcon>()
        for (icon in (manifest["icons"] as? JsonArray).orEmpty()) {
            val obj = icon as? JsonObject ?: continue
            val src = obj.string("src")?.takeIf(String::isNotEmpty) ?: continue
            val sizes = obj.string("sizes").orEmpty().split(Regex("\\s+")).mapNotNull { s ->
                Regex("(\\d+)x(\\d+)").matchEntire(s)?.takeIf { it.groupValues[1] == it.groupValues[2] }?.groupValues?.get(1)?.toIntOrNull()
            }
            val purpose = (obj.string("purpose") ?: "any").split(Regex("\\s+"))
            candidates += WebsiteIcon(Urls.join(manifestUrl, src), sizes.maxOrNull() ?: 0, "maskable" in purpose, MANIFEST_ICON)
        }
        for (link in links) {
            val rels = link.rel.split(Regex("\\s+"))
            if ("apple-touch-icon" !in rels && "icon" !in rels) continue
            val sizes = link.sizes.split(Regex("\\s+")).mapNotNull { s ->
                Regex("(\\d+)x\\1").matchEntire(s)?.groupValues?.get(1)?.toIntOrNull()
            }
            val source = if ("apple-touch-icon" in rels) APPLE_TOUCH_ICON else ICON_LINK
            candidates += WebsiteIcon(link.href, sizes.maxOrNull() ?: 0, false, source)
        }
        candidates += WebsiteIcon(Urls.join(pageUrl, DEFAULT_TOUCH_ICON), 180, false, DEFAULT_TOUCH_ICON)
        return candidates.sortedWith(compareByDescending<WebsiteIcon> { it.maskable }.thenByDescending { it.size })
    }

    /** The first of [candidates] that loads as a square PNG or WebP of at least [MIN_RASTER_PX]. */
    fun firstRaster(candidates: List<WebsiteIcon>, sources: IconSources): IconChoice.Raster? {
        for (candidate in candidates) {
            val data = load(candidate.url, sources) ?: continue
            val info = try {
                RasterInfo.of(data)
            } catch (_: IconException) {
                continue
            }
            if (info.width == info.height && info.width >= MIN_RASTER_PX) {
                val kind = if (candidate.maskable) "maskable " else ""
                return IconChoice.Raster(
                    bytes = data,
                    format = info.format,
                    size = info.width,
                    maskable = candidate.maskable,
                    source = "${candidate.source} ($kind${info.width}px ${info.format})",
                    confidence = Confidence.MEDIUM,
                )
            }
        }
        return null
    }

    private fun tryGlyph(svg: ByteArray): SvgGlyph? = try {
        SvgGlyph.parse(svg)
    } catch (_: IconException) {
        null
    }

    /** data: URLs are decoded here; everything else goes to [IconSources.fetch]. */
    internal fun load(url: String, sources: IconSources): ByteArray? {
        if (!url.startsWith("data:")) return sources.fetch(url)
        val header = url.substringBefore(',')
        val payload = url.substringAfter(',', "")
        return try {
            if (";base64" in header) {
                Base64.getMimeDecoder().decode(payload)
            } else {
                URLDecoder.decode(payload.replace("+", "%2B"), "ISO-8859-1").toByteArray(Charsets.ISO_8859_1)
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
