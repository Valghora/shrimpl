package com.valghora.shrimpl.webapp

import android.Manifest
import android.annotation.SuppressLint
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
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
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.ContextMenu
import android.view.DragEvent
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.addListener
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.valghora.shrimpl.core.AppOption
import com.valghora.shrimpl.core.CaptureChoice
import com.valghora.shrimpl.core.DesktopSessionController
import com.valghora.shrimpl.core.HitType
import com.valghora.shrimpl.core.KeyAction
import com.valghora.shrimpl.core.MenuAction
import com.valghora.shrimpl.core.Modifier
import com.valghora.shrimpl.core.PermissionPlan
import com.valghora.shrimpl.core.UaMode
import com.valghora.shrimpl.core.UrlClass
import com.valghora.shrimpl.core.WakeEvent
import com.valghora.shrimpl.core.profile.HeaderMode
import com.valghora.shrimpl.core.profile.newWindowShortcutSpec
import com.valghora.shrimpl.core.profile.sessionConfig
import com.valghora.shrimpl.core.profile.uaFeaturesActive
import java.io.File
import java.net.URISyntaxException
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * The web app's one activity (docs/TEMPLATE_SPEC.md T3.1): a root FrameLayout built in code, holding one
 * [DesktopWebView], edge to edge (T3.2), with no action bar or XML layout.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SessionPrefs
    private lateinit var controller: DesktopSessionController

    /** Saves the files pages make themselves (T6.15), for the main window and its popups. */
    private lateinit var pageDownloads: PageDownloadBridge

    /** The Web Share API for the main window and its popups (T6.16). */
    private lateinit var shareBridge: ShareBridge

    /** Open and Save dialogs with file handles for the main window and its popups (T6.17). */
    private lateinit var fileAccess: FileAccessBridge
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

    /** Cross-fades the bars to new page colors. */
    private var barsAnimator: ValueAnimator? = null
    private val popups = mutableListOf<Popup>()

    private var originalUaMetadata: UserAgentMetadata? = null
    private val uaMetadataChanged: MutableSet<WebView> = Collections.newSetFromMap(WeakHashMap())
    private var uaMetadataState = "default"
    private var uaMetadataUnsupportedLogged = false

    private var storeWindowArmed = true
    private var storeWindowOpenedAt = 0L

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    /** The files the camera writes to for the open chooser (T6.4); the one it filled is the result. */
    private var cameraFiles: List<Pair<File, Uri>> = emptyList()

    /** A file chooser waiting for Android's camera permission (T6.4). */
    private var pendingChooser: (() -> Unit)? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var pendingPermissionResources: List<String> = emptyList()

    /** Back goes back in the page history while it can (T5.6); kept in sync in `doUpdateVisitedHistory`. */
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            webView.goBack()
        }
    }

    // ----- Full-screen video (T6.13; desktop app quality: Request_Fullscreen_Mode, Keyboard_Exit) -----

    /** The page's full-screen element (a video, usually) while it is shown over the window. */
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    /** The window itself was made full screen (it was a desktop or split-screen window). */
    private var requestedFullscreenMode = false

    /**
     * A back gesture leaves full screen first (T6.13, T5.6): registered on the window at overlay priority
     * while full screen shows. A Back key is caught in [dispatchKeyEvent] instead, since WebView's
     * full-screen view would swallow it (docs/BUILD_NOTES.md "Back in full screen").
     */
    private val fullscreenBackCallback = OnBackInvokedCallback { leaveFullscreen(notifyPage = true) }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback ?: return@registerForActivityResult
            fileChooserCallback = null
            val shot = cameraFiles.filter { (file, _) -> file.length() > 0 }.map { it.second }
            cameraFiles.forEach { (file, _) -> if (file.length() == 0L) file.delete() }
            cameraFiles = emptyList()
            val picked = if (result.resultCode == RESULT_OK) collectUris(result.data)?.filter { it !in shot } else null
            val uris = picked.orEmpty() + if (result.resultCode == RESULT_OK) shot else emptyList()
            callback.onReceiveValue(uris.takeIf { it.isNotEmpty() }?.toTypedArray())
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { runPendingChooser() }

    private val mediaPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { finishMediaRequest() }

    /** A page's location request waiting for Android's location permission prompt. */
    private var pendingGeolocation: Pair<String, GeolocationPermissions.Callback>? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { finishGeolocation() }

    /** The dialog explaining a permission before Android asks again (T6.5), while it shows. */
    private var permissionDialog: AlertDialog? = null

    /** Permissions whose "off" message this window showed already: once each per window (T6.5). */
    private val offShown = mutableSetOf<String>()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // ----- Lifecycle -----

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = SessionPrefs(this)
        // T5.7: page text starts at the system font size (accessibility skill), not a fixed 100 %.
        controller = DesktopSessionController(prefs, prefs, WebApp.profile.sessionConfig(), fontScalePercent(), optionStore = prefs)
        pageDownloads = PageDownloadBridge(this, controller)
        shareBridge = ShareBridge(this, controller)
        fileAccess = FileAccessBridge(this, controller)

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
        // T3.2: pad for the system bars, caption bar, display cutout and IME, and consume them.
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
        publishOptionsShortcut()

        // T6.8: the start URL loads only when no history was restored.
        val restored = savedInstanceState?.getBundle(STATE_WEBVIEW)?.let { webView.restoreState(it) }
        if (restored == null) webView.loadUrl(WebApp.profile.startUrl)
        handleOptionsIntent(intent)
        if (savedInstanceState == null && WebApp.profile.allowNotifications) requestNotificationPermissionIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOptionsIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // T8.4: only when the page should not keep running while hidden.
        // Invariant: lifecycle methods never call pauseTimers() or resumeTimers(); they are process-wide (T8.4).
        if (!WebApp.profile.keepAliveWhenHidden && webViewAlive) webView.onResume()
    }

    override fun onStop() {
        super.onStop()
        flushCookies()
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
        if (webViewAlive) webView.settings.textZoom = controller.fontScaleChanged(fontScalePercent())
        val newNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (newNightMode != nightMode) {
            nightMode = newNightMode
            applyHeaderColors()
        }
    }

    /** Every WebView is removed from its parent and destroyed (T6.9); pending page requests are turned down. */
    override fun onDestroy() {
        workspaceMonitor.unregister()
        popups.toList().forEach { it.destroy() }
        permissionDialog?.dismiss()
        optionsPanel?.dismiss()
        pendingPermissionRequest?.deny()
        pendingPermissionRequest = null
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        pendingChooser = null
        if (::fileAccess.isInitialized) fileAccess.release()
        destroyMainWebView()
        super.onDestroy()
    }

    private fun destroyMainWebView() {
        if (!webViewAlive) return
        webViewAlive = false
        root.removeView(webView)
        webView.destroy()
    }

    // ----- Window, insets and theme (docs/TEMPLATE_SPEC.md T3) -----

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

    /** Dynamic header: the page's edge colors arrived or changed; without a caption bar, the bars fade to them. */
    private fun onPageColors(top: Int, bottom: Int) {
        if (pageColors == top to bottom) return
        pageColors = top to bottom
        if (!hasCaptionBar) fadeBarsTo(top, bottom)
    }

    private fun fadeBarsTo(top: Int, bottom: Int) {
        val fromTop = barsBackground.top
        val fromBottom = barsBackground.bottom
        barsAnimator?.let {
            barsAnimator = null
            it.cancel()
        }
        val evaluator = ArgbEvaluator()
        barsAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BAR_FADE_MS
            addUpdateListener { animation ->
                barsBackground.top = evaluator.evaluate(animation.animatedFraction, fromTop, top) as Int
                barsBackground.bottom = evaluator.evaluate(animation.animatedFraction, fromBottom, bottom) as Int
                barsBackground.invalidateSelf()
            }
            // At the end, the final colors and the bar icon styles are applied.
            addListener(onEnd = { animation ->
                if (barsAnimator === animation) {
                    barsAnimator = null
                    applyHeaderColors()
                }
            })
            start()
        }
    }

    private fun applyHeaderColors() {
        barsAnimator?.let {
            barsAnimator = null
            it.cancel()
        }
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

        // R7.2: the task's label is the profile's name.
        setTaskDescription(
            ActivityManager.TaskDescription.Builder()
                .setLabel(WebApp.profile.name)
                .setPrimaryColor(color)
                .setBackgroundColor(color)
                .build(),
        )
    }

    // ----- WebView setup (T5, T6) -----

    /** Settings, clients and input for the main WebView: focusable at start (T5.1), drops allowed (T5.2). */
    private fun configureMainWebView() {
        applyWebSettings(webView, resolvedMode)
        configureCookies(webView)
        webView.webViewClient = MainClient()
        webView.webChromeClient = AppChromeClient(popup = null)
        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            download(webView, url, contentDisposition, mimeType)
        }

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()
        webView.setOnDragListener { _, event ->
            if (event.action == DragEvent.ACTION_DROP) requestDragAndDropPermissions(event)
            false
        }
        if (controller.linkMenuOn(WebApp.profile.nativeContextMenu)) registerForContextMenu(webView)
        onBackPressedDispatcher.addCallback(this, backCallback)
        // Ctrl + mouse wheel (or trackpad scroll) zooms like Ctrl +/- (Tier 2 Content_Zoom); pinch zoom
        // is WebView's own.
        webView.setOnGenericMotionListener { _, event ->
            if (event.actionMasked != MotionEvent.ACTION_SCROLL || event.metaState and KeyEvent.META_CTRL_ON == 0) return@setOnGenericMotionListener false
            val scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            when {
                scroll > 0 -> webView.settings.textZoom = controller.zoomIn()
                scroll < 0 -> webView.settings.textZoom = controller.zoomOut()
            }
            true
        }
        if (WebApp.profile.allowNotifications) NotificationBridge(this, controller).install(webView)
        pageDownloads.install(webView)
        shareBridge.install(webView)
        fileAccess.install(webView)
        if (WebApp.profile.headerMode == HeaderMode.DYNAMIC) PageColors(controller, ::onPageColors).install(webView)
    }

    /** The only place WebView settings and the user agent are configured (T6.1). */
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

    // ----- Navigation routing (T6.6, T6.10) -----

    /** Returns true when the main-frame navigation is handled here instead of loading in place (T6.6, T6.10). */
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
        // A page wrote this intent: it may name any app, but not one of this app's components, and it
        // can't pass on access to this app's files (android-intent-security skill; Chrome does the same).
        intent.component = null
        intent.selector = null
        intent.flags = intent.flags and URI_GRANT_FLAGS.inv()
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

        /** T6.7: the crashed WebView is removed and destroyed, and the activity recreated. */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.w(WebApp.profile.appId, "Renderer gone (crashed=${detail.didCrash()}); recreating")
            destroyMainWebView()
            recreate()
            return true
        }
    }

    // ----- Popups (T6.6) -----

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

        /** Called from the popup's own WebView callbacks, so the teardown is posted (T6.6). */
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

    // ----- Chrome client: popups, permissions, file chooser (T6.4–T6.6) -----

    private inner class AppChromeClient(private val popup: Popup?) : WebChromeClient() {

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val child = Popup(isDialog)
            applyWebSettings(child.view, resolvedMode)
            configureCookies(child.view)
            child.view.webViewClient = PopupClient(child)
            child.view.webChromeClient = AppChromeClient(child)
            child.view.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                download(child.view, url, contentDisposition, mimeType)
            }
            pageDownloads.install(child.view)
            shareBridge.install(child.view)
            fileAccess.install(child.view)
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
            val needed = allowed.mapNotNull(::runtimePermissionFor).distinct()
            prefs.clearAsked(needed.filter(::isGranted))
            val missing = needed.filterNot(::isGranted)
            if (missing.isEmpty()) {
                grantPermittedResources(request, allowed)
                return
            }
            pendingPermissionRequest?.deny()
            pendingPermissionRequest = request
            pendingPermissionResources = allowed
            requestForPage(request.origin.host ?: request.origin.toString(), missing, mediaPermissionLauncher::launch) {
                if (pendingPermissionRequest === request) finishMediaRequest()
            }
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            enterFullscreen(view, callback)
        }

        override fun onHideCustomView() {
            leaveFullscreen(notifyPage = false)
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            if (pendingPermissionRequest == request) {
                pendingPermissionRequest = null
                permissionDialog?.dismiss()
            }
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            if (!WebApp.profile.allowLocation || controller.classify(origin) != UrlClass.INTERNAL) {
                callback.invoke(origin, false, false)
                return
            }
            prefs.clearAsked(LOCATION_PERMISSIONS.filter(::isGranted))
            if (hasLocationPermission()) {
                callback.invoke(origin, true, false)
                return
            }
            pendingGeolocation?.let { (previous, previousCallback) -> previousCallback.invoke(previous, false, false) }
            pendingGeolocation = origin to callback
            requestForPage(origin.toUri().host ?: origin, LOCATION_PERMISSIONS, locationPermissionLauncher::launch) {
                if (pendingGeolocation?.second === callback) finishGeolocation()
            }
        }

        override fun onGeolocationPermissionsHidePrompt() {
            if (pendingGeolocation != null) {
                pendingGeolocation = null
                permissionDialog?.dismiss()
            }
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = filePathCallback
            pendingChooser = null
            val picker = fileChooserParams.createIntent()
            if (fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            val choice = CaptureChoice.of(fileChooserParams.acceptTypes.orEmpty().toList(), fileChooserParams.isCaptureEnabled)
            // The page asked for the camera: Android's permission comes first when this app holds one.
            if (choice.direct && cameraNeedsPermission()) {
                pendingChooser = { openFileChooser(picker, choice) }
                requestForPage(webView.url?.toUri()?.host.orEmpty(), listOf(Manifest.permission.CAMERA), { cameraPermissionLauncher.launch(it.single()) }) {
                    runPendingChooser()
                }
                return true
            }
            openFileChooser(picker, choice)
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

    /** Answers the waiting media request with the resources Android now allows. */
    private fun finishMediaRequest() {
        val request = pendingPermissionRequest ?: return
        pendingPermissionRequest = null
        prefs.clearAsked(pendingPermissionResources.mapNotNull(::runtimePermissionFor).filter(::isGranted))
        grantPermittedResources(request, pendingPermissionResources)
    }

    /** Answers the waiting location request: precise or approximate, whichever the user allowed. */
    private fun finishGeolocation() {
        val (origin, callback) = pendingGeolocation ?: return
        pendingGeolocation = null
        prefs.clearAsked(LOCATION_PERMISSIONS.filter(::isGranted))
        callback.invoke(origin, hasLocationPermission(), false)
    }

    // ----- Android permissions for a page (T6.5, android-permissions-security skill) -----

    /**
     * Gets Android's [missing] permissions for a page on [host], then [finish]es its request. The first
     * time Android asks directly; after one refusal a dialog explains first ("Not now" turns the page
     * down); once Android no longer asks, a snackbar says the permission is off, with Settings.
     * [finish] answers this request only, so a dialog replaced by another request's turns its own down.
     */
    private fun requestForPage(host: String, missing: List<String>, launch: (Array<String>) -> Unit, finish: () -> Unit) {
        val plan = PermissionPlan.of(missing, prefs::wasAsked, ::shouldShowRequestPermissionRationale)
        if (plan.blocked.isNotEmpty()) showPermissionOff(plan.blocked)
        if (plan.ask.isEmpty()) {
            finish()
            return
        }
        val ask = {
            prefs.markAsked(plan.ask)
            launch(plan.ask.toTypedArray())
        }
        if (!plan.explainFirst) {
            ask()
            return
        }
        val labels = PermissionLabels.of(plan.ask)
        permissionDialog?.cancel()
        permissionDialog = MaterialAlertDialogBuilder(this)
            .setIcon(labels.icon)
            .setTitle(labels.title)
            .setMessage(getString(if (labels.plural) R.string.permission_explain_many else R.string.permission_explain_one, host, WebApp.profile.name))
            .setNegativeButton(R.string.action_not_now) { _, _ -> finish() }
            .setPositiveButton(R.string.action_continue) { _, _ -> ask() }
            .setOnCancelListener { finish() }
            .setOnDismissListener { dialog -> if (permissionDialog === dialog) permissionDialog = null }
            .show()
    }

    /** Says [permissions] are off for this app, once per window each, with a way to its Settings page. */
    private fun showPermissionOff(permissions: List<String>) {
        if (!offShown.addAll(permissions)) return
        Snackbar.make(root, getString(PermissionLabels.of(permissions).off, WebApp.profile.name), Snackbar.LENGTH_LONG)
            .setAction(R.string.action_settings) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }
            .show()
    }

    /** The words and icon for a set of permissions: camera, microphone, both, or location. */
    private enum class PermissionLabels(val title: Int, val off: Int, val icon: Int, val plural: Boolean) {
        CAMERA(R.string.permission_title_camera, R.string.permission_off_camera, R.drawable.ic_permission_camera, false),
        MICROPHONE(R.string.permission_title_microphone, R.string.permission_off_microphone, R.drawable.ic_permission_microphone, false),
        CAMERA_MICROPHONE(
            R.string.permission_title_camera_microphone,
            R.string.permission_off_camera_microphone,
            R.drawable.ic_permission_camera,
            true,
        ),
        LOCATION(R.string.permission_title_location, R.string.permission_off_location, R.drawable.ic_permission_location, false),
        ;

        companion object {
            fun of(permissions: List<String>): PermissionLabels {
                val camera = Manifest.permission.CAMERA in permissions
                val microphone = Manifest.permission.RECORD_AUDIO in permissions
                return when {
                    camera && microphone -> CAMERA_MICROPHONE
                    camera -> CAMERA
                    microphone -> MICROPHONE
                    else -> LOCATION
                }
            }
        }
    }

    // ----- Camera for file inputs (T6.4) -----

    /** Opens the file chooser left waiting for the camera permission, once. */
    private fun runPendingChooser() {
        val chooser = pendingChooser ?: return
        pendingChooser = null
        chooser()
    }

    /**
     * The file picker, with the camera beside it when the input takes photos or video and the camera
     * can be used now, or the camera alone for an input with `capture` (T6.4).
     */
    private fun openFileChooser(picker: Intent, choice: CaptureChoice) {
        val camera = if (choice.any && !cameraNeedsPermission()) cameraIntents(choice) else emptyList()
        val intent = when {
            camera.isEmpty() -> picker
            choice.direct && camera.size == 1 -> camera.single()
            choice.direct -> Intent.createChooser(camera.first(), null).putExtra(Intent.EXTRA_INITIAL_INTENTS, camera.drop(1).toTypedArray())
            else -> Intent.createChooser(picker, null).putExtra(Intent.EXTRA_INITIAL_INTENTS, camera.toTypedArray())
        }
        try {
            fileChooserLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            cameraFiles.forEach { (file, _) -> file.delete() }
            cameraFiles = emptyList()
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
            toast(R.string.toast_no_app)
        }
    }

    /**
     * An app that declares the camera permission must hold it to start the camera app; one without it
     * (the web app doesn't use the camera, R7.3) can start it freely, as the camera app takes the photo.
     */
    private fun cameraNeedsPermission(): Boolean {
        val declared = packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            .requestedPermissions.orEmpty()
        return Manifest.permission.CAMERA in declared && !isGranted(Manifest.permission.CAMERA)
    }

    /**
     * Camera intents that write a full-size photo or video to this app's cache, shared through its
     * FileProvider; files a day old are cleared first, since pages read theirs soon after picking.
     */
    private fun cameraIntents(choice: CaptureChoice): List<Intent> {
        val dir = File(cacheDir, CAMERA_DIR).apply { mkdirs() }
        val stale = System.currentTimeMillis() - CAMERA_FILE_LIFETIME_MS
        dir.listFiles()?.forEach { if (it.lastModified() < stale) it.delete() }
        val stamp = System.currentTimeMillis()
        val kinds = listOfNotNull(
            if (choice.photo) Triple(MediaStore.ACTION_IMAGE_CAPTURE, "photo-$stamp.jpg", "image/jpeg") else null,
            if (choice.video) Triple(MediaStore.ACTION_VIDEO_CAPTURE, "video-$stamp.mp4", "video/mp4") else null,
        )
        cameraFiles = kinds.map { (_, name, _) ->
            val file = File(dir, name)
            file to FileProvider.getUriForFile(this, "$packageName$FILE_PROVIDER_SUFFIX", file)
        }
        return kinds.zip(cameraFiles).map { (kind, output) ->
            Intent(kind.first)
                .putExtra(MediaStore.EXTRA_OUTPUT, output.second)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // The grant travels with clip data, including through the chooser's initial intents.
                .apply { clipData = ClipData.newRawUri(null, output.second) }
        }
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

    /** Asks for POST_NOTIFICATIONS on a fresh launch when it isn't granted (T7.5). */
    private fun requestNotificationPermissionIfNeeded() {
        if (!isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ----- Downloads (T6.3, T6.15) -----

    /**
     * Hands an http(s) download to the system download manager (T6.3) and a file the page made itself
     * (`blob:` or `data:`) to [pageDownloads] (T6.15); other schemes, or a page that can't send the file,
     * get a toast.
     */
    private fun download(view: WebView, url: String, contentDisposition: String?, mimeType: String?) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            pageDownloads.save(view, url) { sent ->
                toast(if (sent) R.string.toast_download_started else R.string.toast_download_unsupported)
            }
            return
        }
        if (!isHttpUrl(url)) {
            toast(R.string.toast_download_unsupported)
            return
        }
        enqueueDownload(url, view.settings.userAgentString, contentDisposition, mimeType)
    }

    private fun enqueueDownload(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
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

    // ----- Context menu (T5.5) -----

    /** The native context menu on links and images (T5.5), when the profile turns it on. */
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
                if (extra != null) download(webView, extra, null, null) else toast(R.string.toast_download_unsupported)
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

    // ----- Keyboard (T5.3, T5.4) -----

    /** Keys reach the page unchanged unless they are the app's own shortcuts (T5.3); Esc first leaves full-screen video. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Esc leaves full-screen video (Tier 2 Keyboard_Exit), and so does a Back key: it arrives here as a
        // key event, not through the back callbacks, and WebView's full-screen view would swallow it
        // (docs/BUILD_NOTES.md "Back in full screen").
        if (fullscreenView != null && (event.keyCode == KeyEvent.KEYCODE_ESCAPE || event.keyCode == KeyEvent.KEYCODE_BACK)) {
            if (event.action == KeyEvent.ACTION_UP) leaveFullscreen(notifyPage = true)
            return true
        }
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
            // T4.5: another window in its own task; the manifest's launch mode allows several.
            KeyAction.NEW_WINDOW -> startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
            )
            KeyAction.PRINT -> printPage()
            KeyAction.APP_OPTIONS -> showAppOptions()
            KeyAction.NONE -> Unit
        }
    }

    /** Ctrl+P: the system print dialog for the page, which can also save it as a PDF (T6.14; desktop Printing_Support). */
    private fun printPage() {
        val name = webView.title?.takeIf(String::isNotBlank) ?: WebApp.profile.name
        getSystemService(PrintManager::class.java).print(name, webView.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
    }

    /**
     * Shows the page's full-screen element over the whole window with the system bars hidden (T6.13).
     * A desktop or split-screen window first asks to become full screen itself, so the video isn't
     * confined to the window (desktop Request_Fullscreen_Mode).
     */
    private fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden()
            return
        }
        fullscreenView = view
        fullscreenCallback = callback
        view.setBackgroundColor(android.graphics.Color.BLACK)
        (window.decorView as ViewGroup).addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        WindowInsetsControllerCompat(window, view).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        if (isInMultiWindowMode) {
            requestFullscreenMode(FULLSCREEN_MODE_REQUEST_ENTER, null)
            requestedFullscreenMode = true
        }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, fullscreenBackCallback)
    }

    /** Restores the window; [notifyPage] when the user (Back, Esc) rather than the page ended full screen. */
    private fun leaveFullscreen(notifyPage: Boolean) {
        val view = fullscreenView ?: return
        val callback = fullscreenCallback
        fullscreenView = null
        fullscreenCallback = null
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(fullscreenBackCallback)
        (window.decorView as ViewGroup).removeView(view)
        WindowInsetsControllerCompat(window, root).show(WindowInsetsCompat.Type.systemBars())
        if (requestedFullscreenMode) {
            requestedFullscreenMode = false
            requestFullscreenMode(FULLSCREEN_MODE_REQUEST_EXIT, null)
        }
        if (notifyPage) callback?.onCustomViewHidden()
        webView.requestFocus()
    }

    private fun modifiersOf(event: KeyEvent): Set<Modifier> = buildSet {
        if (event.isCtrlPressed) add(Modifier.CTRL)
        if (event.isShiftPressed) add(Modifier.SHIFT)
        if (event.isAltPressed) add(Modifier.ALT)
        if (event.isMetaPressed) add(Modifier.META)
    }

    /** The Keyboard Shortcuts Helper's two groups: the site's shortcuts and Window (T5.4). */
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
                KeyboardShortcutInfo(getString(R.string.shortcut_print), KeyEvent.KEYCODE_P, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo(getString(R.string.app_options), KeyEvent.KEYCODE_COMMA, KeyEvent.META_CTRL_ON),
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

    // ----- App options (T5.8) and the desktop/mobile toggle (T6.11) -----

    private val uaToggleEnabled: Boolean
        get() = WebApp.profile.uaUserToggle && WebApp.profile.uaFeaturesActive

    private var optionsPanel: Dialog? = null

    /** Opens the App options panel (T5.8) with the switches this app has, replacing one already open. */
    private fun showAppOptions() {
        optionsPanel?.dismiss()
        val switches = controller.appOptions(uaToggleEnabled).map { option ->
            when (option) {
                AppOption.DESKTOP_SITE -> OptionSwitch(
                    getString(R.string.option_desktop_site),
                    getString(R.string.option_desktop_site_detail),
                    resolvedMode == UaMode.DESKTOP,
                ) { on -> switchSite(if (on) UaMode.DESKTOP else UaMode.MOBILE, remember = true) }
                AppOption.LINK_MENU -> OptionSwitch(
                    getString(R.string.option_link_menu),
                    getString(R.string.option_link_menu_detail),
                    controller.linkMenuOn(WebApp.profile.nativeContextMenu),
                ) { on ->
                    controller.chooseLinkMenu(on)
                    applyLinkMenu()
                }
            }
        }
        optionsPanel = AppOptionsPanel.show(this, switches, ::resetAppOptions).apply {
            setOnDismissListener { if (optionsPanel === it) optionsPanel = null }
        }
    }

    /** Reset to defaults (T5.8): the panel's changes are forgotten and the profile's settings apply again. */
    private fun resetAppOptions() {
        controller.resetAppOptions()
        val mode = controller.resolveUa(
            WebApp.profile.userAgentMode,
            isLargeOrPc,
            prefs.userChoice,
            prefs.learnedDesktop,
            WebApp.profile.userAgentOverride,
        ).mode
        switchSite(mode, remember = false)
        applyLinkMenu()
    }

    /**
     * T5.5: the template's link and image menu takes long presses and right-clicks only while it is on;
     * off, they reach the page, for text selection and the site's own menu.
     */
    private fun applyLinkMenu() {
        if (controller.linkMenuOn(WebApp.profile.nativeContextMenu)) registerForContextMenu(webView) else unregisterForContextMenu(webView)
    }

    /**
     * T6.11: shows the site as [mode] asks, reloading the start URL; [remember] keeps the choice, which
     * otherwise follows the profile again.
     */
    private fun switchSite(mode: UaMode, remember: Boolean) {
        if (remember) prefs.userChoice = mode
        if (mode == resolvedMode) return
        resolvedMode = mode
        applyWebSettings(webView, mode)
        storeWindowArmed = true
        webView.loadUrl(WebApp.profile.startUrl)
    }

    /** The launcher shortcut that opens App options (T5.8), in the app icon's menu. */
    private fun publishOptionsShortcut() {
        val shortcut = ShortcutInfoCompat.Builder(this, SHORTCUT_APP_OPTIONS)
            .setShortLabel(getString(R.string.app_options))
            .setLongLabel(getString(R.string.app_options))
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(
                Intent(this, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra(EXTRA_APP_OPTIONS, prefs.shortcutToken),
            )
            .build()
        ShortcutManagerCompat.setDynamicShortcuts(this, listOf(shortcut))
    }

    /**
     * Opens App options when the launcher shortcut started this activity. The activity is exported (it
     * is the launcher's), so only the shortcut's own token counts (android-intent-security skill).
     */
    private fun handleOptionsIntent(intent: Intent?) {
        val token = intent?.getStringExtra(EXTRA_APP_OPTIONS) ?: return
        intent.removeExtra(EXTRA_APP_OPTIONS)
        setIntent(intent)
        if (token != prefs.shortcutToken) return
        ShortcutManagerCompat.reportShortcutUsed(this, SHORTCUT_APP_OPTIONS)
        showAppOptions()
    }

    // ----- Power and visibility (T8.3) -----

    /** Pauses the page and its timers while the workspace sleeps, and resumes them on wake (T8.3). */
    private fun onWakeEvent(event: WakeEvent) {
        if (!webViewAlive) return
        when (event) {
            WakeEvent.SLEEP -> {
                flushCookies()
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

    /**
     * T6.2: writes cookies to disk on [cookieWriter], since `flush()` blocks its caller on file I/O
     * (anr-audit-and-fix skill).
     */
    private fun flushCookies() {
        val cookies = CookieManager.getInstance()
        cookieWriter.execute { cookies.flush() }
    }

    /** The system font size as a percentage (1.3 is 130). */
    private fun fontScalePercent(): Int = (resources.configuration.fontScale * 100).roundToInt()

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** Where camera photos and videos for file inputs go, in the cache (res/xml/shared_files.xml). */
        const val CAMERA_DIR = "camera"
        const val CAMERA_FILE_LIFETIME_MS = 24 * 60 * 60 * 1000L

        /**
         * The FileProvider's authority after the package name. Invariant: it matches the manifest's
         * `applicationId` + ".files", which Shrimpl's patcher renames with the package (R8.2).
         */
        const val FILE_PROVIDER_SUFFIX = ".files"
        const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        const val TRANSPARENT = android.graphics.Color.TRANSPARENT
        const val BAR_FADE_MS = 250L
        const val STATE_WEBVIEW = "webview_state"
        const val SHORTCUT_APP_OPTIONS = "app_options"
        const val EXTRA_APP_OPTIONS = "app_options"
        const val EXTRA_BROWSER_FALLBACK_URL = "browser_fallback_url"
        val LOCATION_PERMISSIONS = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        const val URI_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

        /** One background thread for cookie writes, shared by every window. */
        val cookieWriter: ExecutorService = Executors.newSingleThreadExecutor()
        const val STORE_REDIRECT_WINDOW_MS = 10_000L
        const val POPUP_FRACTION = 0.9f
        const val WAKE_SCRIPT =
            "window.dispatchEvent(new Event('online'));window.dispatchEvent(new Event('focus'));"
    }
}
