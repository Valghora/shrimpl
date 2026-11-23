package com.valghora.shrimpl

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.work.Configuration
import com.valghora.shrimpl.build.WebAppFactory
import com.valghora.shrimpl.data.AppSettings
import com.valghora.shrimpl.data.FilterListSchedule
import com.valghora.shrimpl.data.FilterListStore
import com.valghora.shrimpl.data.InstalledApps
import com.valghora.shrimpl.data.SigningKeyStore
import com.valghora.shrimpl.data.TemplateProvider
import com.valghora.shrimpl.data.WebAppRecovery
import com.valghora.shrimpl.data.WebAppStore
import com.valghora.shrimpl.data.WhatsNew
import com.valghora.shrimpl.discovery.SiteDiscovery
import com.valghora.shrimpl.install.Installers
import com.valghora.shrimpl.update.UpdateSchedule
import com.valghora.shrimpl.update.Updates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
    val filterLists = FilterListStore(context)
    val whatsNew = WhatsNew(context, settings, BuildConfig.VERSION_CODE)

    /**
     * Whether this device is a PC or has a screen at least 600 dp across: the template's rule for the
     * desktop layout (T6.12), which picks a new app's Site layout (R3.3).
     */
    val largeOrPc: Boolean
        get() = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_PC) ||
            appContext.resources.configuration.smallestScreenWidthDp >= 600

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * R15.2: keeps the list downloads in step with the records. A list some app starts using is fetched
     * at once; the daily job runs while any app blocks ads, and stops when none does.
     */
    fun watchFilterLists() {
        scope.launch {
            store.records.map(FilterListStore::needed).distinctUntilChanged().collect { needed ->
                val missing = needed.any { filterLists.file(it) == null }
                FilterListSchedule.apply(appContext, needed, missing)
            }
        }
    }

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
        container.watchFilterLists()
    }
}

/** The container from any context in this app. */
val Context.container: AppContainer get() = (applicationContext as ShrimplApp).container
