package com.valghora.shrimpl.webapp

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.valghora.shrimpl.core.profile.Profile
import java.util.regex.Pattern

/**
 * Runs [block] with the template using [profile] instead of the neutral one it ships with, then puts the
 * neutral one back. The template reads its profile once, at application start (R7.1), into
 * [WebApp.profile], and everything else reads it from there, so swapping it before an activity starts
 * gives that activity, its bridges and its permission checks the test's profile. Production code is left
 * as it is: the swap goes through reflection on the property's backing field.
 */
fun <T> withProfile(profile: Profile, block: () -> T): T {
    val field = WebApp::class.java.getDeclaredField("profile").apply { isAccessible = true }
    val shipped = field.get(null)
    field.set(null, profile)
    try {
        return block()
    } finally {
        field.set(null, shipped)
    }
}

/** The neutral profile with every capability a page can ask for allowed (T6.5, T7). */
fun profileAllowingEverything(): Profile = WebApp.profile.copy(
    allowMicrophone = true,
    allowCamera = true,
    allowLocation = true,
    allowNotifications = true,
    allowProtectedMedia = true,
)

/**
 * Android's own permission prompt, answered by tapping the button whose text matches [button], such as
 * "Allow" or "Don't allow". Returns false when no prompt shows within [timeoutMs].
 */
fun answerPermissionPrompt(button: String, timeoutMs: Long = 10_000): Boolean {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val selector = By.pkg(Pattern.compile(".*permissioncontroller.*")).text(Pattern.compile(button, Pattern.CASE_INSENSITIVE)).clickable(true)
    val target = device.wait(Until.findObject(selector), timeoutMs) ?: return false
    target.click()
    device.wait(Until.gone(selector), timeoutMs)
    return true
}

/** Waits for [text] anywhere on screen, such as the template's own dialogs and snackbars. */
fun awaitText(text: String, timeoutMs: Long = 10_000): Boolean =
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).wait(Until.hasObject(By.text(text)), timeoutMs) == true

/** Taps the on-screen text [text], such as a button of the template's own dialog. */
fun tapText(text: String, timeoutMs: Long = 10_000) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    requireNotNull(device.wait(Until.findObject(By.text(text)), timeoutMs)) { "\"$text\" isn't on screen" }.click()
}
