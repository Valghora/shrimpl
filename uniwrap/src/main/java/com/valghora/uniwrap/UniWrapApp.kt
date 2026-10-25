package com.valghora.uniwrap

import android.app.Application
import android.content.Context
import com.valghora.uniwrap.build.WebAppFactory
import com.valghora.uniwrap.data.AppSettings
import com.valghora.uniwrap.data.SigningKeyStore
import com.valghora.uniwrap.data.TemplateProvider
import com.valghora.uniwrap.data.WebAppStore
import com.valghora.uniwrap.discovery.SiteDiscovery
import com.valghora.uniwrap.install.Installers

/** UniWrap's long-lived objects, created once with the application. */
class AppContainer(context: Context) {
    val settings = AppSettings(context)
    val store = WebAppStore(context)
    val keys = SigningKeyStore(context)
    val template = TemplateProvider(context)
    val installers = Installers(context, settings)
    val factory = WebAppFactory(context, store, keys, template)
    val discovery = SiteDiscovery(context)
}

class UniWrapApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** The container from any context in this app. */
val Context.container: AppContainer get() = (applicationContext as UniWrapApp).container
