package com.valghora.shrimpl.core

/**
 * Versions as `major.minor` (docs/SHRIMPL_SPEC.md R11.8), stored as one code, major × 100 + minor, so
 * they compare as numbers. Versions from before this scheme were whole numbers 1 to 19, below every
 * code, so an app built with one is older than any template since (R11.4).
 */
object Versions {
    /** The code of `major.minor` (`1.0` → 100, `1.12` → 112), or null if [name] isn't a valid version. */
    fun code(name: String): Int? {
        val parts = name.split('.')
        if (parts.size != 2) return null
        val major = parts[0].toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val minor = parts[1].toIntOrNull()?.takeIf { it in 0..99 } ?: return null
        return major * 100 + minor
    }

    /** The name of [code] (100 → `1.0`, 112 → `1.12`). */
    fun name(code: Int): String = "${code / 100}.${code % 100}"

    /** A web app's versionName (R8.2): its template's version, then its build number (`1.0.12`). */
    fun webAppVersionName(templateCode: Int, build: Int): String = "${name(templateCode)}.$build"

    /**
     * The template code in an installed web app's versionName: `1.0.12` → 100, and a name from before
     * this scheme, `19.12` → 19. Null for anything else.
     */
    fun templateCodeOf(webAppVersionName: String): Int? {
        val parts = webAppVersionName.split('.')
        return when (parts.size) {
            3 -> code("${parts[0]}.${parts[1]}")?.takeIf { parts[2].toIntOrNull() != null }
            2 -> parts[0].toIntOrNull()?.takeIf { it in 1..99 && parts[1].toIntOrNull() != null }
            else -> null
        }
    }
}
