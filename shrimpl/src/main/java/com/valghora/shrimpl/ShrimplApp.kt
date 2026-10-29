package com.valghora.shrimpl

import android.app.Application
import android.content.Context
import com.valghora.shrimpl.build.WebAppFactory
import com.valghora.shrimpl.data.AppSettings
import com.valghora.shrimpl.data.SigningKeyStore
import com.valghora.shrimpl.data.TemplateProvider
import com.valghora.shrimpl.data.WebAppStore
import com.valghora.shrimpl.discovery.SiteDiscovery
import com.valghora.shrimpl.install.Installers

/** Shrimpl's long-lived objects, created once with the application. */
class AppContainer(context: Context) {
    val settings = AppSettings(context)
    val store = WebAppStore(context)
    val keys = SigningKeyStore(context)
    val template = TemplateProvider(context)
    val installers = Installers(context, settings)
    val factory = WebAppFactory(context, store, keys, template)
    val discovery = SiteDiscovery(context)
}

class ShrimplApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** The container from any context in this app. */
val Context.container: AppContainer get() = (applicationContext as ShrimplApp).container
