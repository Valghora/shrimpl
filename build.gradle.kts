plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

/**
 * A version `x.y.z` as its code, x × 100 + y × 10 + z (docs/SHRIMPL_SPEC.md R11.8): Android's
 * versionCode and the number Shrimpl compares, so every version, and every earlier one, orders.
 */
fun versionCodeOf(name: String): Int {
    val parts = name.split('.').map { it.toIntOrNull() }
    val (major, minor, patch) = parts.takeIf { it.size == 3 && it.all { part -> part != null } }?.map { it!! }
        ?: error("version $name isn't major.minor.patch")
    require(major >= 1 && minor in 0..9 && patch in 0..9) { "version $name: minor and patch run 0 to 9 (R11.8)" }
    return major * 100 + minor * 10 + patch
}

extra["shrimplVersionCode"] = versionCodeOf(providers.gradleProperty("shrimpl.appVersion").get())
extra["templateVersionCode"] = versionCodeOf(providers.gradleProperty("shrimpl.templateVersion").get())
