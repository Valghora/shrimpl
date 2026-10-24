package com.valghora.wrapper

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.WebView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.valghora.wrapper.core.AppConfig
import com.valghora.wrapper.core.DesktopSessionController
import com.valghora.wrapper.core.UrlClass
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONException
import org.json.JSONObject

/** Web Notification API bridge: page polyfill -> WebMessageListener -> system notification (R7). */
class NotificationBridge(context: Context, private val controller: DesktopSessionController) {

    private val appContext = context.applicationContext
    private val nextId = AtomicInteger(0)

    private val listener = WebViewCompat.WebMessageListener { _, message, sourceOrigin, _, _ ->
        if (controller.classify(sourceOrigin.toString()) == UrlClass.INTERNAL) {
            message.data?.let(::onMessage)
        }
    }

    fun install(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                createChannel()
                WebViewCompat.addWebMessageListener(webView, "AndroidNotify", AppConfig.ALLOWED_ORIGIN_RULES, listener)
                WebViewCompat.addDocumentStartJavaScript(webView, POLYFILL, AppConfig.ALLOWED_ORIGIN_RULES)
                return
            }
        }
        if (!unsupportedLogged) {
            unsupportedLogged = true
            Log.w(AppConfig.APP_ID, "Notification bridge disabled: WebView lacks WEB_MESSAGE_LISTENER or DOCUMENT_START_SCRIPT")
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            AppConfig.NOTIFICATION_CHANNEL_ID,
            appContext.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun onMessage(data: String) {
        val json = try {
            JSONObject(data)
        } catch (_: JSONException) {
            return
        }
        post(json.optString("title").take(200), json.optString("body").take(1000), json.optString("tag"))
    }

    private fun post(title: String, body: String, tag: String) {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val launch = Intent(appContext, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val contentIntent = PendingIntent.getActivity(
            appContext,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(appContext, AppConfig.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(AppConfig.ICON_BACKGROUND_HEX.toColorInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        val id = if (tag.isNotEmpty()) tag.hashCode() else nextId.incrementAndGet()
        NotificationManagerCompat.from(appContext).notify(id, notification)
    }

    private companion object {
        var unsupportedLogged = false

        /** Idempotent Notification API polyfill injected at document start on ALLOWED_ORIGIN_RULES. */
        const val POLYFILL = """
(function () {
  if (window.__androidNotifyInstalled) return;
  window.__androidNotifyInstalled = true;

  function post(title, options) {
    AndroidNotify.postMessage(JSON.stringify({
      title: String(title || ""),
      body: String(options?.body || ""),
      tag: String(options?.tag || "")
    }));
  }

  class AndroidNotification extends EventTarget {
    static permission = "granted";

    static requestPermission(cb) {
      if (typeof cb === "function") cb("granted");
      return Promise.resolve("granted");
    }

    constructor(title, options) {
      super();
      this.title = String(title || "");
      this.body = String(options?.body || "");
      this.tag = String(options?.tag || "");
      post(title, options);
    }

    close() {}
  }

  window.Notification = AndroidNotification;

  if (window.ServiceWorkerRegistration && "showNotification" in ServiceWorkerRegistration.prototype) {
    ServiceWorkerRegistration.prototype.showNotification = function (title, options) {
      post(title, options);
      return Promise.resolve();
    };
  }

  if (navigator.permissions && navigator.permissions.query) {
    const originalQuery = navigator.permissions.query.bind(navigator.permissions);
    navigator.permissions.query = function (descriptor) {
      if (descriptor && descriptor.name === "notifications") {
        return Promise.resolve({ state: "granted" });
      }
      return originalQuery(descriptor);
    };
  }
})();
"""
    }
}
