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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Phase 4 gate (docs/UNIWRAP_SPEC.md): the Kotlin rules reproduce the retired Python discovery's
 * drafts. Fixtures were recorded by scripts/dev/make_discovery_fixtures.py: the evidence collect.js saw,
 * every response Python's derive() fetched, and the draft it produced.
 */
class DiscoveryParityTest {
    private val psl = PublicSuffixList(resource("public_suffix_list.dat"))
    private val icons = SimpleIcon.parseList(resource("simple-icons.json"))

    private fun resource(name: String) = javaClass.getResource("/discovery/$name")!!.readText()

    /** Replays recorded fetches in order, and fails on any URL Python did not fetch. */
    private class Recorded(json: JsonObject) : IconSources {
        private val responses = json.mapValues { (_, v) -> if (v is JsonNull) null else Base64.getDecoder().decode(v.jsonPrimitive.content) }
        val fetched = mutableListOf<String>()

        override fun simpleIconSvg(slug: String) = fetch("https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/$slug.svg")

        override fun fetch(url: String): ByteArray? {
            fetched += url
            check(url in responses) { "fetched $url, which the Python version did not" }
            return responses[url]
        }

        /** What Python fetched, in order; data: URLs are decoded locally, not fetched. */
        fun recordInOrder() = responses.keys.filterNot { it.startsWith("data:") }
    }

    private fun check(name: String) {
        val expected = Json.parseToJsonElement(resource("$name/expected.json")).jsonObject
        val evidence = Evidence.decode(resource("$name/evidence.json"))
        val sources = Recorded(Json.parseToJsonElement(resource("$name/responses.json")).jsonObject)
        val draft = Discovery.draft(evidence, expected.getValue("requestedUrl").jsonPrimitive.content, psl, icons, sources)

        assertEquals("$name appId", expected.getValue("appId").jsonPrimitive.content, draft.appId)
        val fields = expected.getValue("fields").jsonObject
        mapOf(
            "APP_NAME" to draft.name, "START_URL" to draft.startUrl, "INTERNAL_DOMAINS" to draft.internalDomains,
            "HEADER_SURFACE_HEX" to draft.headerColor, "HEADER_SURFACE_HEX_NIGHT" to draft.headerColorNight,
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
        assertEquals("$name notes", (expected.getValue("notes") as JsonArray).map { it.jsonPrimitive.content }, draft.notes)
        assertEquals("$name fetch order", sources.recordInOrder(), sources.fetched)
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
