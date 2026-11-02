package com.valghora.shrimpl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.valghora.shrimpl.data.ThemeSetting

/** Material 3 with the wallpaper's dynamic colors (R1.3; always available at minSdk 37), light or dark per settings. */
@Composable
fun ShrimplTheme(setting: ThemeSetting, content: @Composable () -> Unit) {
    val dark = setting.isDark()
    val context = LocalContext.current
    MaterialTheme(
        colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
        content = content,
    )
}

/** Whether the app is dark with this setting: System follows the device's dark theme. */
@Composable
fun ThemeSetting.isDark(): Boolean = when (this) {
    ThemeSetting.SYSTEM -> isSystemInDarkTheme()
    ThemeSetting.LIGHT -> false
    ThemeSetting.DARK -> true
}
