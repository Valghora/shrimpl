package com.valghora.shrimpl

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import com.valghora.shrimpl.build.WebAppFactory
import com.valghora.shrimpl.data.AppSettings
import com.valghora.shrimpl.data.InstalledApps
import com.valghora.shrimpl.data.SigningKeyStore
import com.valghora.shrimpl.data.TemplateProvider
import com.valghora.shrimpl.data.WebAppRecovery
import com.valghora.shrimpl.data.WebAppStore
import com.valghora.shrimpl.discovery.SiteDiscovery
import com.valghora.shrimpl.install.Installers
import com.valghora.shrimpl.update.UpdateSchedule
import com.valghora.shrimpl.update.Updates

/** Shrimpl's long-lived objects, created once with the application. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Whether [packageName] is installed now; a binder call, so not on the main thread. */
    fun isInstalled(packageName: String) = InstalledApps.isInstalled(appContext, packageName)

    val settings = AppSettings(context)
    val store = WebAppStore(context)
    val keys = SigningKeyStore(context)
    val template = TemplateProvider(context)
    val installers = Installers(context, settings)
    val factory = WebAppFactory(context, store, keys, template)
    val discovery = SiteDiscovery(context)
    val recovery = WebAppRecovery(context, store, settings)
    val updates = Updates(context, installers, installedCode = BuildConfig.VERSION_CODE, debug = BuildConfig.DEBUG)

    /** Schedules, or cancels, the background update check from the settings (R14.2). */
    fun scheduleUpdateChecks() = UpdateSchedule.apply(appContext, settings.updateFrequency.value)
}

/**
 * Shrimpl's application; it creates the [AppContainer] every screen uses. It also configures
 * WorkManager, which then starts on first use (for the update check, R14.2) instead of at launch.
 */
class ShrimplApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scheduleUpdateChecks()
    }
}

/** The container from any context in this app. */
val Context.container: AppContainer get() = (applicationContext as ShrimplApp).container
