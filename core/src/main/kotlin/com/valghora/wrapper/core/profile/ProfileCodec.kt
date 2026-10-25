package com.valghora.wrapper.core.profile

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ProfileException(message: String, val errors: List<FieldError> = emptyList()) : Exception(message)

/** Reads and writes profiles as JSON (docs/UNIWRAP_SPEC.md R7.1, R11.1). */
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
            json.decodeFromString(Profile.serializer(), text)
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
}
