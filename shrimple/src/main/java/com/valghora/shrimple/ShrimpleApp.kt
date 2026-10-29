package com.valghora.shrimple

import android.app.Application
import android.content.Context
import com.valghora.shrimple.build.WebAppFactory
import com.valghora.shrimple.data.AppSettings
import com.valghora.shrimple.data.SigningKeyStore
import com.valghora.shrimple.data.TemplateProvider
import com.valghora.shrimple.data.WebAppStore
import com.valghora.shrimple.discovery.SiteDiscovery
import com.valghora.shrimple.install.Installers

/** Shrimple's long-lived objects, created once with the application. */
class AppContainer(context: Context) {
    val settings = AppSettings(context)
    val store = WebAppStore(context)
    val keys = SigningKeyStore(context)
    val template = TemplateProvider(context)
    val installers = Installers(context, settings)
    val factory = WebAppFactory(context, store, keys, template)
    val discovery = SiteDiscovery(context)
}

class ShrimpleApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** The container from any context in this app. */
val Context.container: AppContainer get() = (applicationContext as ShrimpleApp).container
