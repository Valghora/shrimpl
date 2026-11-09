package com.valghora.shrimpl.webapp

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valghora.shrimpl.core.profile.notificationChannelId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A page's notifications (docs/TEMPLATE_SPEC.md T7): the template asks for Android's notification
 * permission on a fresh launch (T7.5), the polyfill tells the page it may notify (T7.3), and a page's
 * `new Notification(...)` becomes a real notification with its title and body, its tag as the ID (T7.2, T7.4).
 * The neutral profile allows notifications already (it is the profile's default).
 */
@RunWith(AndroidJUnit4::class)
class NotificationsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val notifications = context.getSystemService(NotificationManager::class.java)

    private fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @Test
    fun aPageNotificationBecomesAnAndroidNotification() {
        notifications.cancelAll()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // T7.5: a fresh launch asks. Earlier tests may have left it answered already, so allow it either way.
            if (!granted() && !answerPermissionPrompt("^allow$")) {
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            }
            awaitUntil("the notification permission", 10_000) { granted() }

            val page = PageDriver(scenario).also { it.loadTestPage() }
            assertEquals("granted", page.js("Notification.permission"))
            page.js("new Notification('Hello from the page', { body: 'The body', tag: 'greeting' }); 0")

            // The tag becomes the notification's ID (T7.4), so a later one with the same tag replaces it.
            val id = "greeting".hashCode()
            awaitUntil("the page's notification", 15_000) { notifications.activeNotifications.any { it.id == id } }
            val posted = notifications.activeNotifications.first { it.id == id }.notification
            assertEquals("Hello from the page", posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            assertEquals("The body", posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            assertEquals(WebApp.profile.notificationChannelId, posted.channelId)
        }
        notifications.cancelAll()
    }
}
