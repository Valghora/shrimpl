package com.valghora.uniwrap.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.valghora.uniwrap.data.ThemeSetting

/** Material 3 with the wallpaper's dynamic colors (always available at minSdk 37), light or dark per settings. */
@Composable
fun UniWrapTheme(setting: ThemeSetting, content: @Composable () -> Unit) {
    val dark = when (setting) {
        ThemeSetting.SYSTEM -> isSystemInDarkTheme()
        ThemeSetting.LIGHT -> false
        ThemeSetting.DARK -> true
    }
    val context = LocalContext.current
    MaterialTheme(
        colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
        content = content,
    )
}
