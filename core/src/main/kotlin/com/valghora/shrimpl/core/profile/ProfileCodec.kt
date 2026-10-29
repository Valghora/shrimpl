package com.valghora.shrimpl.core.profile

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ProfileException(message: String, val errors: List<FieldError> = emptyList()) : Exception(message)

/** Reads and writes profiles as JSON (docs/SHRIMPL_SPEC.md R7.1, R11.1). */
object ProfileCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
        // Unknown keys are errors: a typo must not silently fall back to a default.
        ignoreUnknownKeys = false
    }

    /** Parses and validates; throws [ProfileException] describing every problem. */
    fun decode(text: String): Profile {
        val profile = try {
            json.decodeFromJsonElement(Profile.serializer(), upgrade(json.parseToJsonElement(text)))
        } catch (e: SerializationException) {
            throw ProfileException("invalid profile JSON: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw ProfileException("invalid profile JSON: ${e.message}")
        }
        val errors = ProfileValidator.validate(profile)
        if (errors.isNotEmpty()) {
            throw ProfileException(
                "invalid profile:\n" + errors.joinToString("\n") { "  - ${it.field}: ${it.message}" },
                errors,
            )
        }
        return profile
    }

    fun encode(profile: Profile): String = json.encodeToString(Profile.serializer(), profile)

    /**
     * Profiles written before header modes had only `headerColor`: `"system"`, or a color that was
     * always used. They get the matching `headerMode`; anything else is returned unchanged.
     */
    fun upgrade(element: JsonElement): JsonElement {
        if (element !is JsonObject || "headerMode" in element) return element
        val header = (element["headerColor"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return element
        val upgraded = element.toMutableMap()
        if (header == Profile.SYSTEM) {
            upgraded["headerMode"] = JsonPrimitive("system")
            upgraded["headerColor"] = JsonPrimitive(Profile.DEFAULT_HEADER_COLOR)
        } else {
            upgraded["headerMode"] = JsonPrimitive("custom")
        }
        return JsonObject(upgraded)
    }
}
