plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

/**
 * A version `x.y` as its code, x × 100 + y (docs/SHRIMPL_SPEC.md R11.8): Android's versionCode and the
 * number Shrimpl compares, so every version, and every version before this scheme (1 to 19), orders.
 */
fun versionCodeOf(name: String): Int {
    val parts = name.split('.').map { it.toIntOrNull() }
    val (major, minor) = parts.takeIf { it.size == 2 && it.all { part -> part != null } }?.map { it!! }
        ?: error("version $name isn't major.minor")
    require(major >= 1 && minor in 0..99) { "version $name: the minor version runs 0 to 99 (R11.8)" }
    return major * 100 + minor
}

extra["shrimplVersionCode"] = versionCodeOf(providers.gradleProperty("shrimpl.appVersion").get())
extra["templateVersionCode"] = versionCodeOf(providers.gradleProperty("shrimpl.templateVersion").get())
