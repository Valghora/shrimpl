package com.valghora.wrapper

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Dialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.ContextMenu
import android.view.DragEvent
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.valghora.wrapper.core.DesktopSessionController
import com.valghora.wrapper.core.HitType
import com.valghora.wrapper.core.KeyAction
import com.valghora.wrapper.core.MenuAction
import com.valghora.wrapper.core.Modifier
import com.valghora.wrapper.core.UaMode
import com.valghora.wrapper.core.UrlClass
import com.valghora.wrapper.core.WakeEvent
import com.valghora.wrapper.core.profile.HeaderMode
import com.valghora.wrapper.core.profile.newWindowShortcutSpec
import com.valghora.wrapper.core.profile.sessionConfig
import com.valghora.wrapper.core.profile.uaFeaturesActive
import java.net.URISyntaxException
import java.util.Collections
import java.util.WeakHashMap

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SessionPrefs
    private lateinit var controller: DesktopSessionController
    private lateinit var root: FrameLayout
    private lateinit var webView: DesktopWebView
    private lateinit var workspaceMonitor: WorkspaceMonitor

    /** Resolved once in onCreate; changed only by the store-redirect fallback and the UA toggle. */
    private lateinit var resolvedMode: UaMode
    private var isLargeOrPc = false
    private var webViewAlive = false
    private var nightMode = Configuration.UI_MODE_NIGHT_UNDEFINED
    private val barsBackground = BarsBackground()

    /** The window has a caption bar (a desktop window); the Dynamic header applies only without one. */
    private var hasCaptionBar = false

    /** The page's top and bottom edge colors, once the Dynamic header's script reported them. */
    private var pageColors: Pair<Int, Int>? = null
    private val popups = mutableListOf<Popup>()

    private var originalUaMetadata: UserAgentMetadata? = null
    private val uaMetadataChanged: MutableSet<WebView> = Collections.newSetFromMap(WeakHashMap())
    private var uaMetadataState = "default"
    private var uaMetadataUnsupportedLogged = false

    private var storeWindowArmed = true
    private var storeWindowOpenedAt = 0L

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var pendingPermissionResources: List<String> = emptyList()

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            webView.goBack()
        }
    }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback ?: return@registerForActivityResult
            fileChooserCallback = null
            callback.onReceiveValue(if (result.resultCode == RESULT_OK) collectUris(result.data) else null)
        }

    private val mediaPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val request = pendingPermissionRequest ?: return@registerForActivityResult
            pendingPermissionRequest = null
            grantPermittedResources(request, pendingPermissionResources)
        }

    /** A page's location request waiting for Android's location permission prompt. */
    private var pendingGeolocation: Pair<String, GeolocationPermissions.Callback>? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val (origin, callback) = pendingGeolocation ?: return@registerForActivityResult
            pendingGeolocation = null
            // Precise or approximate, whichever the user allowed.
            callback.invoke(origin, hasLocationPermission(), false)
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // ---------- Lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SessionPrefs(this)
        controller = DesktopSessionController(prefs, prefs, WebApp.profile.sessionConfig())

        isLargeOrPc = packageManager.hasSystemFeature(PackageManager.FEATURE_PC) ||
            resources.configuration.smallestScreenWidthDp >= 600
        val resolution = controller.resolveUa(
            WebApp.profile.userAgentMode,
            isLargeOrPc,
            prefs.userChoice,
            prefs.learnedDesktop,
            WebApp.profile.userAgentOverride,
        )
        resolvedMode = resolution.mode

        webView = DesktopWebView(this)
        root = FrameLayout(this)
        root.addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
        webViewAlive = true

        nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        root.background = barsBackground
        applyHeaderColors()
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.captionBar() or
                    WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            barsBackground.topHeight = bars.top
            val caption = insets.getInsets(WindowInsetsCompat.Type.captionBar()).top > 0
            if (caption != hasCaptionBar) {
                hasCaptionBar = caption
                applyHeaderColors()
            } else {
                barsBackground.invalidateSelf()
            }
            WindowInsetsCompat.CONSUMED
        }

        configureMainWebView()
        Log.i(
            WebApp.profile.appId,
            "UA mode=${resolvedMode.name} reason=${resolution.reason.label} metadata=$uaMetadataState",
        )

        workspaceMonitor = WorkspaceMonitor(this, controller, ::onWakeEvent)
        workspaceMonitor.register()
        publishToggleShortcut()

        if (!handleToggleIntent(intent)) {
            val restored = savedInstanceState?.getBundle(STATE_WEBVIEW)?.let { webView.restoreState(it) }
            if (restored == null) webView.loadUrl(WebApp.profile.startUrl)
        }
        if (savedInstanceState == null && WebApp.profile.allowNotifications) requestNotificationPermissionIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleToggleIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onStart() {
        super.onStart()
        // R8.5: only when the page should not keep running while hidden. Never pauseTimers() here.
        if (!WebApp.profile.keepAliveWhenHidden && webViewAlive) webView.onResume()
    }

    override fun onStop() {
        super.onStop()
        CookieManager.getInstance().flush()
        if (!WebApp.profile.keepAliveWhenHidden && webViewAlive) webView.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (webViewAlive) {
            val state = Bundle()
            webView.saveState(state)
            outState.putBundle(STATE_WEBVIEW, state)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (newNightMode != nightMode) {
            nightMode = newNightMode
            applyHeaderColors()
        }
    }

    override fun onDestroy() {
        workspaceMonitor.unregister()
        popups.toList().forEach { it.destroy() }
        pendingPermissionRequest?.deny()
        pendingPermissionRequest = null
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        CookieManager.getInstance().flush()
        destroyMainWebView()
        super.onDestroy()
    }

    private fun destroyMainWebView() {
        if (!webViewAlive) return
        webViewAlive = false
        root.removeView(webView)
        webView.destroy()
    }

    // ---------- Window, insets and theme (R3) ----------

    /**
     * The window and caption bar color: the custom color, else the system surface color for the
     * current light or dark theme (also for Dynamic, whose page colors only reach the system bars).
     */
    private fun currentHeaderColor(): Int = when {
        WebApp.profile.headerMode == HeaderMode.CUSTOM -> WebApp.profile.headerColor.toColorInt()
        nightMode == Configuration.UI_MODE_NIGHT_YES -> getColor(android.R.color.system_surface_container_dark)
        else -> getColor(android.R.color.system_surface_container_light)
    }

    private fun isDark(color: Int) = DesktopSessionController.isDarkColor("#%06X".format(color and 0xFFFFFF))

    private fun barStyle(color: Int) = if (isDark(color)) SystemBarStyle.dark(TRANSPARENT) else SystemBarStyle.light(TRANSPARENT, TRANSPARENT)

    /** Dynamic header: the page's edge colors arrived or changed. */
    private fun onPageColors(top: Int, bottom: Int) {
        if (pageColors == top to bottom) return
        pageColors = top to bottom
        if (!hasCaptionBar) applyHeaderColors()
    }

    private fun applyHeaderColors() {
        val color = currentHeaderColor()
        val dark = isDark(color)
        // Dynamic, without a caption bar (phones, full-screen tablets): the status bar takes the page's
        // top color and the navigation bar its bottom color, each with readable icons.
        val page = pageColors?.takeIf { WebApp.profile.headerMode == HeaderMode.DYNAMIC && !hasCaptionBar }
        val top = page?.first ?: color
        val bottom = page?.second ?: color

        enableEdgeToEdge(statusBarStyle = barStyle(top), navigationBarStyle = barStyle(bottom))

        barsBackground.top = top
        barsBackground.bottom = bottom
        barsBackground.invalidateSelf()
        webView.setBackgroundColor(color)

        val mask = WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND or
            WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS
        val appearance = WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND or
            if (dark) 0 else WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS
        window.insetsController?.setSystemBarsAppearance(appearance, mask)
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = !isDark(top)
            isAppearanceLightNavigationBars = !isDark(bottom)
        }

        setTaskDescription(
            ActivityManager.TaskDescription.Builder()
                .setLabel(WebApp.profile.name)
                .setPrimaryColor(color)
                .setBackgroundColor(color)
                .build(),
        )
    }

    // ---------- WebView setup (R5, R6) ----------

    private fun configureMainWebView() {
        applyWebSettings(webView, resolvedMode)
        configureCookies(webView)
        webView.webViewClient = MainClient()
        webView.webChromeClient = AppChromeClient(popup = null)
        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            enqueueDownload(url, webView.settings.userAgentString, contentDisposition, mimeType)
        }

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()
        webView.setOnDragListener { _, event ->
            if (event.action == DragEvent.ACTION_DROP) requestDragAndDropPermissions(event)
            false
        }
        if (WebApp.profile.nativeContextMenu) registerForContextMenu(webView)
        onBackPressedDispatcher.addCallback(this, backCallback)
        if (WebApp.profile.allowNotifications) NotificationBridge(this, controller).install(webView)
        if (WebApp.profile.headerMode == HeaderMode.DYNAMIC) PageColors(controller, ::onPageColors).install(webView)
    }

    /** The only place WebView settings and the user agent are configured (R6.1). */
    @SuppressLint("SetJavaScriptEnabled")
    private fun applyWebSettings(webView: WebView, mode: UaMode) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.textZoom = controller.textZoom

        settings.userAgentString = controller.buildUserAgent(
            WebSettings.getDefaultUserAgent(this),
            mode,
            WebApp.profile.userAgentOverride,
        )

        if (mode == UaMode.DESKTOP && WebApp.profile.userAgentOverride.isEmpty()) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
                val current = WebSettingsCompat.getUserAgentMetadata(settings)
                if (webView !in uaMetadataChanged) originalUaMetadata = originalUaMetadata ?: current
                val desktop = UserAgentMetadata.Builder(current)
                    .setPlatform("Linux")
                    .setPlatformVersion("")
                    .setMobile(false)
                    .setModel("")
                    .build()
                WebSettingsCompat.setUserAgentMetadata(settings, desktop)
                uaMetadataChanged += webView
                uaMetadataState = "applied"
            } else {
                if (!uaMetadataUnsupportedLogged) {
                    uaMetadataUnsupportedLogged = true
                    Log.w(WebApp.profile.appId, "WebView lacks USER_AGENT_METADATA; desktop UA metadata not applied")
                }
                uaMetadataState = "unsupported"
            }
            val overview = !isLargeOrPc
            settings.useWideViewPort = overview
            settings.loadWithOverviewMode = overview
        } else {
            val original = originalUaMetadata
            if (webView in uaMetadataChanged && original != null &&
                WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)
            ) {
                WebSettingsCompat.setUserAgentMetadata(settings, original)
            }
            uaMetadataChanged -= webView
            uaMetadataState = "default"
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false
        }
    }

    private fun configureCookies(view: WebView) {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }
    }

    // ---------- Navigation routing (R6.6, R6.10) ----------

    /** Returns true when the main-frame navigation is handled here instead of loading in place. */
    private fun routeMainFrame(url: String): Boolean {
        if (applyStoreRedirectFallback(url)) return true
        return when (controller.classify(url)) {
            UrlClass.INTERNAL, UrlClass.OAUTH, UrlClass.PASSTHROUGH -> false
            UrlClass.EXTERNAL -> {
                openExternal(url)
                true
            }
            UrlClass.SPECIAL_SCHEME -> {
                openSpecialScheme(url)
                true
            }
            UrlClass.BLOCKED -> true
        }
    }

    private fun applyStoreRedirectFallback(url: String): Boolean {
        if (!WebApp.profile.uaStoreRedirectFallback || !WebApp.profile.uaFeaturesActive) return false
        if (resolvedMode != UaMode.MOBILE || prefs.userChoice != null || prefs.learnedDesktop) return false
        val windowOpen = storeWindowOpenedAt != 0L &&
            SystemClock.elapsedRealtime() - storeWindowOpenedAt <= STORE_REDIRECT_WINDOW_MS
        if (!windowOpen || !controller.isStoreRedirect(url)) return false
        prefs.learnedDesktop = true
        resolvedMode = UaMode.DESKTOP
        applyWebSettings(webView, UaMode.DESKTOP)
        webView.loadUrl(WebApp.profile.startUrl)
        publishToggleShortcut()
        Log.i(WebApp.profile.appId, "Store redirect on a mobile UA; switched to desktop UA")
        return true
    }

    private fun openExternal(url: String) {
        startSafely(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    }

    private fun openSpecialScheme(url: String) {
        if (!url.startsWith("intent:", ignoreCase = true)) {
            startSafely(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
            return
        }
        val intent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (_: URISyntaxException) {
            return
        }
        intent.component = null
        intent.selector = null
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val fallback = intent.getStringExtra(EXTRA_BROWSER_FALLBACK_URL)
            if (fallback != null && isHttpUrl(fallback)) {
                when (controller.classify(fallback)) {
                    UrlClass.INTERNAL, UrlClass.OAUTH -> webView.loadUrl(fallback)
                    else -> openExternal(fallback)
                }
            } else {
                toast(R.string.toast_no_app)
            }
        }
    }

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(R.string.toast_no_app)
        }
    }

    private inner class MainClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.isForMainFrame && routeMainFrame(request.url.toString())

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (storeWindowArmed) {
                storeWindowArmed = false
                storeWindowOpenedAt = SystemClock.elapsedRealtime()
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            backCallback.isEnabled = view.canGoBack()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.w(WebApp.profile.appId, "Renderer gone (crashed=${detail.didCrash()}); recreating")
            destroyMainWebView()
            recreate()
            return true
        }
    }

    // ---------- Popups (R6.6) ----------

    private inner class Popup(private val isDialog: Boolean) {
        val view = WebView(this@MainActivity)
        private var dialog: Dialog? = null
        private var decided = false
        private var destroyed = false

        /** Returns true when the navigation must not load in the popup. */
        fun onNavigation(url: String): Boolean {
            if (destroyed) return true
            if (decided) return routePopupNavigation(url)
            if (url == "about:blank") return false
            decided = true
            val urlClass = controller.classify(url)
            return when {
                urlClass == UrlClass.EXTERNAL -> {
                    openExternal(url)
                    destroyLater()
                    true
                }
                urlClass == UrlClass.SPECIAL_SCHEME -> {
                    openSpecialScheme(url)
                    destroyLater()
                    true
                }
                urlClass == UrlClass.INTERNAL || urlClass == UrlClass.OAUTH || isDialog -> {
                    show()
                    false
                }
                else -> {
                    destroyLater()
                    true
                }
            }
        }

        /** Catches a first navigation that never reached shouldOverrideUrlLoading. */
        fun onPageStarted(url: String) {
            if (!decided) onNavigation(url)
        }

        private fun routePopupNavigation(url: String): Boolean = when (controller.classify(url)) {
            UrlClass.INTERNAL, UrlClass.OAUTH, UrlClass.PASSTHROUGH -> false
            UrlClass.EXTERNAL -> {
                openExternal(url)
                true
            }
            UrlClass.SPECIAL_SCHEME -> {
                openSpecialScheme(url)
                true
            }
            UrlClass.BLOCKED -> true
        }

        private fun show() {
            if (dialog != null || isFinishing) return
            dialog = Dialog(this@MainActivity).apply {
                setContentView(view)
                setOnDismissListener { destroy() }
                show()
                window?.setLayout((root.width * POPUP_FRACTION).toInt(), (root.height * POPUP_FRACTION).toInt())
            }
        }

        /** Called from the popup's own WebView callbacks, so the teardown is posted (R6.6). */
        fun dismiss() {
            root.post {
                val shown = dialog
                if (shown != null && shown.isShowing) shown.dismiss() else destroy()
            }
        }

        private fun destroyLater() {
            view.stopLoading()
            root.post { destroy() }
        }

        fun destroy() {
            if (destroyed) return
            destroyed = true
            popups -= this
            dialog?.let {
                it.setOnDismissListener(null)
                if (it.isShowing) it.dismiss()
            }
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
    }

    private inner class PopupClient(private val popup: Popup) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.isForMainFrame && popup.onNavigation(request.url.toString())

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (url != null) popup.onPageStarted(url)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            popup.dismiss()
            return true
        }
    }

    // ---------- Chrome client: popups, permissions, file chooser (R6.4–R6.6) ----------

    private inner class AppChromeClient(private val popup: Popup?) : WebChromeClient() {

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val child = Popup(isDialog)
            applyWebSettings(child.view, resolvedMode)
            configureCookies(child.view)
            child.view.webViewClient = PopupClient(child)
            child.view.webChromeClient = AppChromeClient(child)
            child.view.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                enqueueDownload(url, child.view.settings.userAgentString, contentDisposition, mimeType)
            }
            popups += child
            transport.webView = child.view
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            popup?.dismiss()
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            if (controller.classify(request.origin.toString()) != UrlClass.INTERNAL) {
                request.deny()
                return
            }
            val allowed = request.resources.filter {
                when (it) {
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> WebApp.profile.allowMicrophone
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> WebApp.profile.allowCamera
                    PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> WebApp.profile.allowProtectedMedia
                    else -> false
                }
            }
            if (allowed.isEmpty()) {
                request.deny()
                return
            }
            val missing = allowed.mapNotNull(::runtimePermissionFor).distinct().filterNot(::isGranted)
            if (missing.isEmpty()) {
                grantPermittedResources(request, allowed)
                return
            }
            pendingPermissionRequest?.deny()
            pendingPermissionRequest = request
            pendingPermissionResources = allowed
            mediaPermissionLauncher.launch(missing.toTypedArray())
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            if (pendingPermissionRequest == request) pendingPermissionRequest = null
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            if (!WebApp.profile.allowLocation || controller.classify(origin) != UrlClass.INTERNAL) {
                callback.invoke(origin, false, false)
                return
            }
            if (hasLocationPermission()) {
                callback.invoke(origin, true, false)
                return
            }
            pendingGeolocation?.let { (previous, previousCallback) -> previousCallback.invoke(previous, false, false) }
            pendingGeolocation = origin to callback
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        override fun onGeolocationPermissionsHidePrompt() {
            pendingGeolocation = null
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = filePathCallback
            val intent = fileChooserParams.createIntent()
            if (fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            try {
                fileChooserLauncher.launch(intent)
            } catch (_: ActivityNotFoundException) {
                fileChooserCallback = null
                filePathCallback.onReceiveValue(null)
                toast(R.string.toast_no_app)
            }
            return true
        }
    }

    private fun runtimePermissionFor(resource: String): String? = when (resource) {
        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
        else -> null
    }

    private fun hasLocationPermission(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun grantPermittedResources(request: PermissionRequest, resources: List<String>) {
        val granted = resources.filter { resource -> runtimePermissionFor(resource)?.let(::isGranted) ?: true }
        if (granted.isEmpty()) request.deny() else request.grant(granted.toTypedArray())
    }

    private fun collectUris(data: Intent?): Array<Uri>? {
        if (data == null) return null
        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(uris::add)
        }
        data.data?.let { if (it !in uris) uris += it }
        return uris.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (!isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ---------- Downloads (R6.3) ----------

    private fun enqueueDownload(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        if (!isHttpUrl(url)) {
            toast(R.string.toast_download_unsupported)
            return
        }
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(url.toUri()).apply {
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            addRequestHeader("User-Agent", userAgent)
            if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }
        getSystemService(DownloadManager::class.java).enqueue(request)
        toast(R.string.toast_download_started)
    }

    private fun isHttpUrl(url: String): Boolean {
        val scheme = url.toUri().scheme?.lowercase()
        return scheme == "http" || scheme == "https"
    }

    // ---------- Context menu (R5.5) ----------

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (v !== webView) return
        val result = webView.hitTestResult
        val hit = when (result.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> HitType.ANCHOR
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> HitType.IMAGE_ANCHOR
            WebView.HitTestResult.IMAGE_TYPE -> HitType.IMAGE
            else -> HitType.OTHER
        }
        val extra = result.extra
        controller.contextMenuActions(hit).forEach { action ->
            val title = when (action) {
                MenuAction.COPY_LINK -> R.string.menu_copy_link
                MenuAction.OPEN_LINK -> R.string.menu_open_link
                MenuAction.SAVE_IMAGE -> R.string.menu_save_image
            }
            menu.add(Menu.NONE, action.ordinal, action.ordinal, title).setOnMenuItemClickListener {
                performMenuAction(action, hit, extra)
                true
            }
        }
    }

    private fun performMenuAction(action: MenuAction, hit: HitType, extra: String?) {
        when (action) {
            MenuAction.COPY_LINK -> withLinkUrl(hit, extra) { url ->
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.menu_copy_link), url))
            }
            MenuAction.OPEN_LINK -> withLinkUrl(hit, extra, ::openExternal)
            MenuAction.SAVE_IMAGE ->
                if (extra != null && isHttpUrl(extra)) {
                    enqueueDownload(extra, webView.settings.userAgentString, null, null)
                } else {
                    toast(R.string.toast_download_unsupported)
                }
        }
    }

    /** Anchor links come from the hit test; image links need the anchor href, not the image URL. */
    private fun withLinkUrl(hit: HitType, extra: String?, block: (String) -> Unit) {
        if (hit == HitType.ANCHOR) {
            extra?.takeIf { it.isNotEmpty() }?.let(block)
            return
        }
        val handler = Handler(Looper.getMainLooper()) { message ->
            message.data.getString("url")?.takeIf { it.isNotEmpty() }?.let(block)
            true
        }
        webView.requestFocusNodeHref(handler.obtainMessage())
    }

    // ---------- Keyboard (R5.3, R5.4) ----------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val action = controller.resolveKey(KeyEvent.keyCodeToString(event.keyCode), modifiersOf(event), event.repeatCount)
            if (action != KeyAction.NONE) {
                performKeyAction(action)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun performKeyAction(action: KeyAction) {
        when (action) {
            KeyAction.RELOAD -> webView.reload()
            KeyAction.ZOOM_IN -> webView.settings.textZoom = controller.zoomIn()
            KeyAction.ZOOM_OUT -> webView.settings.textZoom = controller.zoomOut()
            KeyAction.ZOOM_RESET -> webView.settings.textZoom = controller.resetZoom()
            KeyAction.NEW_WINDOW -> startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
            )
            KeyAction.NONE -> Unit
        }
    }

    private fun modifiersOf(event: KeyEvent): Set<Modifier> = buildSet {
        if (event.isCtrlPressed) add(Modifier.CTRL)
        if (event.isShiftPressed) add(Modifier.SHIFT)
        if (event.isAltPressed) add(Modifier.ALT)
        if (event.isMetaPressed) add(Modifier.META)
    }

    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        val appShortcuts = WebApp.profile.appShortcuts.map {
            KeyboardShortcutInfo(it.label, KeyEvent.keyCodeFromString(it.keyName), metaStateOf(it.modifiers))
        }
        data += KeyboardShortcutGroup(WebApp.profile.name, appShortcuts)
        data += KeyboardShortcutGroup(
            getString(R.string.shortcut_group_window),
            listOf(
                KeyboardShortcutInfo(getString(R.string.shortcut_reload), KeyEvent.KEYCODE_R, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo(getString(R.string.shortcut_zoom_in), KeyEvent.KEYCODE_EQUALS, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo(getString(R.string.shortcut_zoom_out), KeyEvent.KEYCODE_MINUS, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo(getString(R.string.shortcut_zoom_reset), KeyEvent.KEYCODE_0, KeyEvent.META_CTRL_ON),
            ) + newWindowShortcutInfo(),
        )
    }

    private fun newWindowShortcutInfo(): List<KeyboardShortcutInfo> {
        val shortcut = WebApp.profile.newWindowShortcutSpec ?: return emptyList()
        if (!WebApp.profile.enableMultiWindow) return emptyList()
        return listOf(
            KeyboardShortcutInfo(
                getString(R.string.shortcut_new_window),
                KeyEvent.keyCodeFromString(shortcut.keyName),
                metaStateOf(shortcut.modifiers),
            ),
        )
    }

    private fun metaStateOf(modifiers: Set<Modifier>): Int = modifiers.fold(0) { state, modifier ->
        state or when (modifier) {
            Modifier.CTRL -> KeyEvent.META_CTRL_ON
            Modifier.SHIFT -> KeyEvent.META_SHIFT_ON
            Modifier.ALT -> KeyEvent.META_ALT_ON
            Modifier.META -> KeyEvent.META_META_ON
        }
    }

    // ---------- Desktop/mobile toggle (R6.11) ----------

    private val uaToggleEnabled: Boolean
        get() = WebApp.profile.uaUserToggle && WebApp.profile.uaFeaturesActive

    private fun publishToggleShortcut() {
        if (!uaToggleEnabled) {
            ShortcutManagerCompat.removeAllDynamicShortcuts(this)
            return
        }
        val toDesktop = resolvedMode == UaMode.MOBILE
        val shortcut = ShortcutInfoCompat.Builder(this, SHORTCUT_TOGGLE_UA)
            .setShortLabel(getString(if (toDesktop) R.string.shortcut_request_desktop else R.string.shortcut_request_mobile))
            .setLongLabel(
                getString(if (toDesktop) R.string.shortcut_request_desktop_long else R.string.shortcut_request_mobile_long),
            )
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(
                Intent(this, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra(EXTRA_TOGGLE_UA, true),
            )
            .build()
        ShortcutManagerCompat.setDynamicShortcuts(this, listOf(shortcut))
    }

    /** Applies a pending toggle from the launcher shortcut; returns true if it loaded START_URL. */
    private fun handleToggleIntent(intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(EXTRA_TOGGLE_UA, false) != true) return false
        intent.removeExtra(EXTRA_TOGGLE_UA)
        setIntent(intent)
        if (!uaToggleEnabled) return false
        ShortcutManagerCompat.reportShortcutUsed(this, SHORTCUT_TOGGLE_UA)
        val target = controller.toggleTarget(resolvedMode)
        prefs.userChoice = target
        resolvedMode = target
        applyWebSettings(webView, target)
        storeWindowArmed = true
        webView.loadUrl(WebApp.profile.startUrl)
        publishToggleShortcut()
        return true
    }

    // ---------- Power and visibility (R8.4) ----------

    private fun onWakeEvent(event: WakeEvent) {
        if (!webViewAlive) return
        when (event) {
            WakeEvent.SLEEP -> {
                CookieManager.getInstance().flush()
                webView.setWorkspaceAwake(false)
                webView.onPause()
                webView.pauseTimers()
            }
            WakeEvent.WAKE -> {
                webView.setWorkspaceAwake(true)
                webView.onResume()
                webView.resumeTimers()
                webView.evaluateJavascript(WAKE_SCRIPT, null)
            }
            WakeEvent.NONE -> Unit
        }
    }

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        const val TRANSPARENT = android.graphics.Color.TRANSPARENT
        const val STATE_WEBVIEW = "webview_state"
        const val SHORTCUT_TOGGLE_UA = "toggle_ua"
        const val EXTRA_TOGGLE_UA = "toggle_ua"
        const val EXTRA_BROWSER_FALLBACK_URL = "browser_fallback_url"
        const val STORE_REDIRECT_WINDOW_MS = 10_000L
        const val POPUP_FRACTION = 0.9f
        const val WAKE_SCRIPT =
            "window.dispatchEvent(new Event('online'));window.dispatchEvent(new Event('focus'));"
    }
}
