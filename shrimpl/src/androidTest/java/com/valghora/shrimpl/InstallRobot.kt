package com.valghora.shrimpl

import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import java.util.regex.Pattern
import kotlin.concurrent.thread

/**
 * Does what a user does at the system installer's screens, for tests that install and uninstall web
 * apps: Android asks to confirm each install, each update (docs/SHRIMPL_SPEC.md R2.4) and each
 * uninstall. Taps Install or Update anywhere they show, and OK or Uninstall only in the installer's own
 * package (Shrimpl has an OK button of its own), until interrupted.
 */
class InstallRobot(private val device: UiDevice) {
    /** Lets Shrimpl install apps, as a user does once in system settings ("Install unknown apps"). */
    fun allowInstalls(shrimplPackage: String) {
        device.executeShellCommand("appops set $shrimplPackage REQUEST_INSTALL_PACKAGES allow")
    }

    /** Starts answering the installer's screens; interrupt the returned thread when the work is done. */
    fun acceptSystemDialogs(): Thread = thread(name = "install-confirmer") {
        var idle = 0
        try {
            while (true) {
                val button = device.findObject(By.text(INSTALL_OR_UPDATE).clickable(true))
                    ?: device.findObject(By.pkg(INSTALLER_PACKAGE).text(UNINSTALL_OR_OK).clickable(true))
                if (button != null) {
                    button.click()
                    idle = 0
                } else if (++idle % 15 == 0) {
                    // Say what is on screen, so a stuck run explains itself.
                    val seen = device.findObjects(By.clickable(true)).mapNotNull { it.text }.joinToString(" | ")
                    Log.w(TAG, "no installer button after ${idle}s; clickable on screen: $seen")
                }
                Thread.sleep(1_000)
            }
        } catch (_: InterruptedException) {
            // The work finished; stop looking.
        }
    }

    private companion object {
        const val TAG = "SHRIMPL_INSTALL"
        val INSTALL_OR_UPDATE: Pattern = Pattern.compile("(?i)^(install|update)$")
        val UNINSTALL_OR_OK: Pattern = Pattern.compile("(?i)^(uninstall|ok)$")
        val INSTALLER_PACKAGE: Pattern = Pattern.compile(".*packageinstaller.*")
    }
}

/** Polls [condition] until it holds, failing with a message naming [what] after [timeoutMs]. */
fun awaitUntil(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(500)
    }
    throw AssertionError("Timed out after ${timeoutMs / 1000} s waiting for $what")
}
