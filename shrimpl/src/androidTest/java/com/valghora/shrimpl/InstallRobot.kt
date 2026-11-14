package com.valghora.shrimpl

import android.os.SystemClock
import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlin.concurrent.thread

/**
 * Does what a user does at the system installer's screens, for tests that install and uninstall web
 * apps: Android asks to confirm each install, each update (docs/SHRIMPL_SPEC.md R2.4) and each
 * uninstall. Taps Install or Update anywhere they show, and OK or Uninstall only in the installer's own
 * package (Shrimpl has an OK button of its own), until stopped.
 */
class InstallRobot(private val device: UiDevice) {
    private val started = mutableListOf<Confirmer>()

    /** Lets Shrimpl install apps, as a user does once in system settings ("Install unknown apps"). */
    fun allowInstalls(shrimplPackage: String) {
        device.executeShellCommand("appops set $shrimplPackage REQUEST_INSTALL_PACKAGES allow")
    }

    /**
     * Answers the installer's screens on a thread of its own until [Confirmer.stop]. It stops on a flag,
     * not an interrupt: UiAutomator's waits swallow interrupts, and a confirmer that missed one went on
     * tapping dialogs in the tests after it.
     */
    fun acceptSystemDialogs(): Confirmer {
        val stopped = AtomicBoolean(false)
        val worker = thread(name = "install-confirmer") {
            var idle = 0
            while (!stopped.get()) {
                try {
                    // A system app that is slow to answer can put "isn't responding" over everything: keep waiting.
                    device.findObject(By.text(WAIT).clickable(true))?.takeIf { device.hasObject(By.text(CLOSE_APP)) }?.click()
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
                } catch (_: StaleObjectException) {
                    // The screen changed between finding a button and using it; look again.
                }
                if (!stopped.get()) SystemClock.sleep(1_000)
            }
        }
        return Confirmer(worker, stopped).also { started += it }
    }

    /** Stops every confirmer this robot started; tests call it in `@After`, so a failed test leaves none running. */
    fun stopAll() {
        started.forEach(Confirmer::stop)
        started.clear()
    }

    /** A running [acceptSystemDialogs]; [stop] ends it before the test goes on. */
    class Confirmer(private val worker: Thread, private val stopped: AtomicBoolean) {
        /** Ends the confirmer and waits for it; safe to call again. */
        fun stop() {
            stopped.set(true)
            worker.join(STOP_TIMEOUT_MS)
            check(!worker.isAlive) { "the install confirmer didn't stop within ${STOP_TIMEOUT_MS / 1000} s" }
        }
    }

    private companion object {
        const val TAG = "SHRIMPL_INSTALL"

        /** Longer than one round's UiAutomator searches on a busy emulator. */
        const val STOP_TIMEOUT_MS = 30_000L
        val INSTALL_OR_UPDATE: Pattern = Pattern.compile("(?i)^(install|update)$")
        val UNINSTALL_OR_OK: Pattern = Pattern.compile("(?i)^(uninstall|ok)$")
        val INSTALLER_PACKAGE: Pattern = Pattern.compile(".*packageinstaller.*")
        val WAIT: Pattern = Pattern.compile("(?i)^wait$")
        val CLOSE_APP: Pattern = Pattern.compile("(?i)^close app$")
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
