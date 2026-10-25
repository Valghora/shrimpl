package com.valghora.wrapper.core

/** The controller's configuration from this build's generated [AppConfig]. */
fun AppConfig.sessionConfig() = SessionConfig(
    internalDomains = INTERNAL_DOMAINS,
    oauthDomains = OAUTH_DOMAINS,
    pauseOnPowerSave = PAUSE_ON_POWER_SAVE,
    enableMultiWindow = ENABLE_MULTI_WINDOW,
    appShortcuts = APP_SHORTCUTS,
    newWindowShortcut = NEW_WINDOW_SHORTCUT,
)
