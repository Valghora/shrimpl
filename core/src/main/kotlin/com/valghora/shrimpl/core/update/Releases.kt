package com.valghora.shrimpl.core.update

import com.valghora.shrimpl.core.Versions
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * How often the background check runs (docs/SHRIMPL_SPEC.md R14.2): never ([DISABLED], checks only by
 * hand), or every [hours]; once a day unless the user picks another.
 */
enum class UpdateFrequency(val hours: Long) {
    DISABLED(0),
    HOUR(1),
    SIX_HOURS(6),
    TWELVE_HOURS(12),
    DAY(24),
    WEEK(168),
    ;

    companion object {
        val DEFAULT = DAY
    }
}

/**
 * A published Shrimpl release (R14.1): its version and code (R11.8), the APK to install and the file
 * holding the APK's SHA-256, and the release page.
 */
data class Release(
    val version: String,
    val code: Int,
    val apkUrl: String,
    val checksumUrl: String,
    val pageUrl: String,
)

/**
 * Shrimpl's GitHub releases (R14.1). The Release workflow attaches `shrimpl-<version>.apk` and
 * `shrimpl-<version>.apk.sha256` to a release tagged `v<version>`.
 */
object Releases {
    const val LATEST = "https://api.github.com/repos/Valghora/shrimpl/releases/latest"

    fun apkName(version: String) = "shrimpl-$version.apk"

    fun checksumName(version: String) = "${apkName(version)}.sha256"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The release in GitHub's latest-release answer, or null when it isn't a Shrimpl release this
     * scheme knows: a `v<major.minor.patch>` tag with both files attached.
     */
    fun parse(answer: String): Release? {
        val root = runCatching { json.parseToJsonElement(answer).jsonObject }.getOrNull() ?: return null
        val version = root.string("tag_name")?.removePrefix("v") ?: return null
        val code = Versions.code(version) ?: return null
        val assets = (root["assets"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { asset -> asset.string("name")?.let { name -> asset.string("browser_download_url")?.let { name to it } } }
            .toMap()
        return Release(
            version = version,
            code = code,
            apkUrl = assets[apkName(version)] ?: return null,
            checksumUrl = assets[checksumName(version)] ?: return null,
            pageUrl = root.string("html_url").orEmpty(),
        )
    }

    /**
     * The SHA-256 in a checksum file: `<hash>  <name>` as `sha256sum` writes it, or the hash alone.
     * Null when it holds no hash for [fileName].
     */
    fun checksumFor(text: String, fileName: String): String? = text.lineSequence()
        .map { it.trim().split(Regex("\\s+"), limit = 2) }
        .firstOrNull { parts -> parts[0].matches(HASH) && (parts.size == 1 || parts[1].removePrefix("*") == fileName) }
        ?.get(0)?.lowercase()

    /** Lowercase hex SHA-256 of everything [input] holds. */
    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private val HASH = Regex("[0-9a-fA-F]{64}")

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
