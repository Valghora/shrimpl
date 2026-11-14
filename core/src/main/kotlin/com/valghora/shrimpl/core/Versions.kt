package com.valghora.shrimpl.core

/**
 * Versions as `major.minor.patch` (docs/SHRIMPL_SPEC.md R11.8), each change raising the last part, so
 * 1.1.9 is followed by 1.2.0. Stored as one code, major × 100 + minor × 10 + patch, so they compare
 * as numbers: the codes are those of the earlier `major.minor` names (1.1 and 1.0.1 are both 101), and
 * the whole-number versions from before either (1 to 19) are below every code, so an app built with
 * one is older than any template since (R11.4).
 */
object Versions {
    /** The code of `major.minor.patch` (`1.0.0` → 100, `1.2.3` → 123), or null if [name] isn't a valid version. */
    fun code(name: String): Int? {
        val parts = name.split('.')
        if (parts.size != 3) return null
        val major = parts[0].toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val minor = parts[1].toIntOrNull()?.takeIf { it in 0..9 } ?: return null
        val patch = parts[2].toIntOrNull()?.takeIf { it in 0..9 } ?: return null
        return major * 100 + minor * 10 + patch
    }

    /** The name of [code] (100 → `1.0.0`, 123 → `1.2.3`). */
    fun name(code: Int): String = "${code / 100}.${code % 100 / 10}.${code % 10}"

    /** A web app's versionName (R8.2): its template's version, then its build number (`1.0.0.12`). */
    fun webAppVersionName(templateCode: Int, build: Int): String = "${name(templateCode)}.$build"

    /**
     * The template code in an installed web app's versionName, from any of its forms: `1.2.3.12` → 123;
     * `1.12.4`, from the `major.minor` names → 112; and `19.12`, from before either → 19. Null otherwise.
     */
    fun templateCodeOf(webAppVersionName: String): Int? {
        val parts = webAppVersionName.split('.')
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        return when (numbers.size) {
            4 -> code(parts.take(3).joinToString("."))
            3 -> numbers[0].takeIf { it >= 1 && numbers[1] in 0..99 }?.let { it * 100 + numbers[1] }
            2 -> numbers[0].takeIf { it in 1..99 }
            else -> null
        }
    }
}
