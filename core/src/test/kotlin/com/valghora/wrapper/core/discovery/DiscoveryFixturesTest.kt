package com.valghora.wrapper.core.discovery

import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Whole drafts for four real sites. The fixtures were recorded from the retired Python discovery (the
 * Phase 4 parity gate): the evidence its collector saw, every response it fetched, and its draft. The
 * expectations have since moved with the rules: brand colors for the icon background, simple-icons
 * looked up by name, and no header color.
 */
class DiscoveryFixturesTest {
    private val psl = PublicSuffixList(resource("public_suffix_list.dat"))
    private val brands = Json.parseToJsonElement(resource("simple-icons.json")).jsonArray.associate { entry ->
        val o = entry.jsonObject
        SimpleIcons.titleToSlug(o.getValue("title").jsonPrimitive.content) to (o.getValue("title").jsonPrimitive.content to o.getValue("hex").jsonPrimitive.content)
    }

    private fun resource(name: String) = javaClass.getResource("/discovery/$name")!!.readText()

    /**
     * Replays recorded fetches in order, and fails on any URL Python did not fetch. simple-icons
     * lookups answer from the recorded glyph files and the brand list, as cdn.simpleicons.org would.
     */
    private inner class Recorded(json: JsonObject) : IconSources {
        private val responses = json.mapValues { (_, v) -> if (v is JsonNull) null else Base64.getDecoder().decode(v.jsonPrimitive.content) }
        val fetched = mutableListOf<String>()
        val lookups = mutableListOf<String>()

        override fun simpleIcon(slug: String): SimpleIcon? {
            lookups += slug
            val (title, hex) = brands[slug] ?: return null
            val svg = responses[GLYPHS + "$slug.svg"] ?: return null
            return SimpleIcon(slug, title, hex, svg)
        }

        override fun fetch(url: String): ByteArray? {
            fetched += url
            check(url in responses) { "fetched $url, which the Python version did not" }
            return responses[url]
        }

        /** What Python fetched, in order, except simple-icons glyphs; data: URLs are decoded locally. */
        fun recordInOrder() = responses.keys.filterNot { it.startsWith("data:") || it.startsWith(GLYPHS) }
    }

    private fun check(name: String) {
        val expected = Json.parseToJsonElement(resource("$name/expected.json")).jsonObject
        val evidence = Evidence.decode(resource("$name/evidence.json"))
        val sources = Recorded(Json.parseToJsonElement(resource("$name/responses.json")).jsonObject)
        val draft = Discovery.draft(evidence, expected.getValue("requestedUrl").jsonPrimitive.content, psl, sources)

        assertEquals("$name appId", expected.getValue("appId").jsonPrimitive.content, draft.appId)
        val fields = expected.getValue("fields").jsonObject
        mapOf(
            "APP_NAME" to draft.name, "START_URL" to draft.startUrl, "INTERNAL_DOMAINS" to draft.internalDomains,
            "ICON_BACKGROUND_HEX" to draft.iconBackground, "ALLOW_MICROPHONE" to draft.allowMicrophone,
            "ALLOW_CAMERA" to draft.allowCamera, "ALLOW_NOTIFICATIONS" to draft.allowNotifications,
        ).forEach { (key, field) ->
            val want = fields.getValue(key).jsonObject
            assertEquals("$name $key value", plain(want.getValue("value")), field.value)
            assertEquals("$name $key source", want.getValue("source").jsonPrimitive.content, field.source)
            assertEquals("$name $key confidence", want.getValue("confidence").jsonPrimitive.content, field.confidence.name.lowercase())
        }

        val icon = expected.getValue("icon").jsonObject
        assertEquals("$name icon source", icon.getValue("source").jsonPrimitive.content, draft.icon.source)
        assertEquals("$name icon confidence", icon.getValue("confidence").jsonPrimitive.content, draft.icon.confidence.name.lowercase())
        when (val choice = draft.icon) {
            is IconChoice.Glyph -> {
                assertEquals("$name icon mode", "svg", icon.getValue("mode").jsonPrimitive.content)
                assertEquals("$name icon color", icon["color"]?.jsonPrimitive?.content, choice.color)
                assertEquals("$name icon bytes", icon.getValue("sha256").jsonPrimitive.content, sha256(choice.svg))
            }
            is IconChoice.Raster -> {
                assertEquals("$name icon mode", "raster", icon.getValue("mode").jsonPrimitive.content)
                assertEquals("$name icon format", icon.getValue("ext").jsonPrimitive.content, choice.format)
                assertEquals("$name icon maskable", icon.getValue("maskable").jsonPrimitive.boolean, choice.maskable)
                assertEquals("$name icon bytes", icon.getValue("sha256").jsonPrimitive.content, sha256(choice.bytes))
            }
            is IconChoice.Letter -> {
                assertEquals("$name icon mode", "letter", icon.getValue("mode").jsonPrimitive.content)
                assertEquals("$name icon letter", icon.getValue("letter").jsonPrimitive.content, choice.letter.toString())
            }
        }
        val notes = (expected.getValue("notes") as JsonArray).map {
            Note(Note.Level.valueOf(it.jsonObject.getValue("level").jsonPrimitive.content.uppercase()), it.jsonObject.getValue("text").jsonPrimitive.content)
        }
        assertEquals("$name notes", notes, draft.notes)
        assertEquals("$name fetch order", sources.recordInOrder(), sources.fetched)
    }

    private companion object {
        const val GLYPHS = "https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/"
    }

    private fun plain(e: JsonElement): Any? = when (e) {
        is JsonArray -> e.map(::plain)
        is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.content
        else -> e.toString()
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun exampleDotCom() = check("example")

    @Test
    fun github() = check("github")

    @Test
    fun squoosh() = check("squoosh")

    @Test
    fun discord() = check("discord")
}
