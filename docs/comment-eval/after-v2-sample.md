# Rubric sample: after-v2

Seed `comment-eval-v1`, 40 of 721 comment blocks in main sources.

## 0d55cc9683 `core/src/main/kotlin/com/valghora/shrimpl/core/CoreTypes.kt:33` (kdoc)

```
Why a UA mode was chosen; [label] is the value logged at startup (docs/TEMPLATE_SPEC.md T6.12).
```

Followed by:

```kotlin
enum class UaReason(val label: String) {
    OVERRIDE("override"),
    USER("user"),
    LEARNED("learned"),
    CONFIG("config"),
    AUTO_LARGE("auto-large"),
    AUTO_COMPACT("auto-compact"),
}

data class UaResolution(val mode: UaMode, val reason: UaReason)
```

## 022c644978 `core/src/main/kotlin/com/valghora/shrimpl/core/build/SigningKeys.kt:72` (kdoc)

```
A password-protected PKCS12 file holding the key (the backup format, R5.2).
```

Followed by:

```kotlin
    fun export(key: SigningKey, password: CharArray): ByteArray {
        val store = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        store.setKeyEntry(ALIAS, key.privateKey, password, arrayOf(key.certificate))
        return ByteArrayOutputStream().also { store.store(it, password) }.toByteArray()
    }

    /** Reads a PKCS12 file written by [export] (or any PKCS12 with one key entry). */
    fun import(pkcs12: ByteArray, password: CharArray): SigningKey {
        val store = try {
            KeyStore.getInstance("PKCS12").apply { load(ByteArrayInputStream(pkcs12), password) }
```

## 06fc21b98b `core/src/main/kotlin/com/valghora/shrimpl/core/discovery/Discovery.kt:204` (line)

```
----- Icon -----
```

Followed by:

```kotlin
        val appId = forcedId ?: AppIds.fromName(name)
        val icon = IconChooser.choose(simple, simpleConfidence, page.links, manifest, manifestUrl, finalUrl, sources)
            ?: IconChoice.Letter(
                letter = name.uppercase().firstOrNull { it.code < 128 && it.isLetterOrDigit() } ?: 'A',
                source = "no usable brand icon found",
                confidence = Confidence.LOW,
            )

        // ----- Brand color, for the icon background -----
        val iconBackground = brandColor(icon, page, manifest)
```

## 0d10010b09 `core/src/main/kotlin/com/valghora/shrimpl/core/discovery/Evidence.kt:41` (kdoc)

```
Everything the page requested, with a resource type ("document", "script", "xhr", "fetch", ...).
```

Followed by:

```kotlin
    val requests: List<Request> = emptyList(),
    /** API use found in the page's scripts, by [ScriptSignals] name. */
    val signals: Map<String, Boolean> = emptyMap(),
    val page: PageInfo? = null,
    val manifestUrl: String? = null,
    /** The web manifest as parsed JSON, when it was found and valid. */
    val manifest: JsonObject? = null,
)

@Serializable
```

## 08a58060d9 `core/src/main/kotlin/com/valghora/shrimpl/core/discovery/Urls.kt:13` (kdoc)

```
Lowercase host without user info or port, or null (urllib's `hostname`).
```

Followed by:

```kotlin
        val hostname: String?
            get() {
                val hostPort = netloc.substringAfterLast('@')
                val host = if (hostPort.startsWith("[")) hostPort.substringBefore(']').drop(1) else hostPort.substringBefore(':')
                return host.lowercase().takeIf { it.isNotEmpty() }
            }
    }

    fun split(url: String): Parts {
        val m = SPLIT.matchEntire(url) ?: return Parts("", "", url, "")
```

## 020d5d79c9 `core/src/main/kotlin/com/valghora/shrimpl/core/profile/Profile.kt:72` (kdoc)

```
How the web app colors its header (window background, caption bar and system bars).
```

Followed by:

```kotlin
@Serializable
enum class HeaderMode {
    /** The system's surface color for the current light or dark theme. */
    @SerialName("system") SYSTEM,

    /**
     * The site's own colors: on phones and other windows without a caption bar, the status bar takes
     * the color at the top of the page and the navigation bar the color at the bottom. Desktop
     * windows (with a caption bar) use the system color.
     */
```

## 0f8c104357 `core/src/main/kotlin/com/valghora/shrimpl/core/records/EmbeddedRecord.kt:10` (kdoc)

```
What a built web app carries so a reinstalled Shrimpl can list it again (docs/SHRIMPL_SPEC.md R11.6):
besides its profile, the icon options as `assets/shrimpl/icons.json` and the options' images next to
it. [recover] turns an installed APK back into a record.
```

Followed by:

```kotlin
object EmbeddedRecord {
    const val ICONS = "assets/shrimpl/icons.json"
    private const val DIRECTORY = "assets/shrimpl/"

    /** Marks the icon of an app recovered from its drawn layers (one built before icons.json). */
    const val DRAWN_ORIGIN = "Installed app"

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
```

## 0d71da1a80 `core/src/main/kotlin/com/valghora/shrimpl/core/records/WebAppRecord.kt:130` (kdoc)

```
The icon the last build used.
```

Followed by:

```kotlin
    val icon: IconRecord,
    /** Every icon option; null in records saved before options existed (see [iconOptions]). */
    val icons: IconOptions? = null,
    /** The build installed last; the next build is [nextBuildNumber]. */
    val buildNumber: Int,
    /** The template version that build used (R11.4). */
    val templateVersion: Int,
    /** SHA-256 of the signing certificate, so a key change can be detected. */
    val signerFingerprint: String,
    val createdAt: Long,
```

## 03de46c0c8 `core/src/main/kotlin/com/valghora/shrimpl/core/testing/TestTags.kt:26` (kdoc)

```
A simulated install needs "Install unknown apps" (R6.3).
```

Followed by:

```kotlin
    UNKNOWN_SOURCES("unknown-sources"),
}

/** How a test search address ends (R13); [stage] is the step it fails at, counted from 0. */
enum class TestSearch(val host: String, val stage: Int) {
    NOT_FOUND("404.test", 0),
    DENIED("403.test", 2),
    UNAVAILABLE("500.test", 0),
    ERROR("400.test", 0),
    OFFLINE("offline.test", 0),
```

## 0b494f2902 `shrimpl/src/main/java/com/valghora/shrimpl/build/WebAppFactory.kt:48` (kdoc)

```
Makes and installs a web app (docs/SHRIMPL_SPEC.md R8, R6): renders the icon, builds and signs from
the template, installs, and only then saves the record, so a failed install never uses up a build
number (R11.3). It runs off the main thread, can be cancelled before the install, and reports each
stage (R8.6).
```

Followed by:

```kotlin
class WebAppFactory(
    private val context: Context,
    private val store: WebAppStore,
    private val keys: SigningKeyStore,
    private val template: TemplateProvider,
) {
    enum class Stage { RENDERING_ICON, BUILDING, SIGNING, VERIFYING, INSTALLING }

    suspend fun build(request: BuildRequest, installer: Installer, onStage: (Stage) -> Unit): BuildOutcome {
        val stages = StagePacer(onStage)
```

## 0262be7c8e `shrimpl/src/main/java/com/valghora/shrimpl/build/WebAppFactory.kt:89` (line)

```
Reported from the builder's own (IO) thread, so these wait by blocking it.
```

Followed by:

```kotlin
                            WebAppBuilder.Stage.SIGNING -> stages.nextBlocking(Stage.SIGNING)
                            WebAppBuilder.Stage.VERIFYING -> stages.nextBlocking(Stage.VERIFYING)
                        }
                    },
                    isCancelled = { !active.isActive },
                )
            }

            stages.next(Stage.INSTALLING)
            return when (val result = installer.install(built.apk)) {
```

## 0021bb4042 `shrimpl/src/main/java/com/valghora/shrimpl/data/AppSettings.kt:28` (kdoc)

```
How web apps are installed (R6.2); the system installer unless the user picked Shizuku or root.
```

Followed by:

```kotlin
    val installMethod: StateFlow<InstallMethod> = _installMethod.asStateFlow()

    private val _theme = MutableStateFlow(prefs.getString(KEY_THEME, null)?.let { runCatching { ThemeSetting.valueOf(it) }.getOrNull() } ?: ThemeSetting.SYSTEM)
    val theme: StateFlow<ThemeSetting> = _theme.asStateFlow()

    private val _clickAction = MutableStateFlow(
        prefs.getString(KEY_CLICK_ACTION, null)?.let { runCatching { ClickAction.valueOf(it) }.getOrNull() } ?: ClickAction.EDIT,
    )

    /** What tapping an app in the home list does; Edit unless the user picked Open. */
```

## 0eb6658c87 `shrimpl/src/main/java/com/valghora/shrimpl/data/SigningKeyStore.kt:19` (kdoc)

```
The key every web app is signed with (docs/SHRIMPL_SPEC.md R5). It is a PKCS12 file in no-backup
storage; the file's random password is encrypted with an AES key that never leaves the Android
Keystore. Exported backups are PKCS12 files protected by a password the user chooses.
```

Followed by:

```kotlin
class SigningKeyStore(context: Context) {
    private val dir = File(context.noBackupFilesDir, "signing")
    private val keyFile = AtomicFile(File(dir, "shrimpl.p12"))
    private val passwordFile = AtomicFile(File(dir, "shrimpl.p12.password"))
    private var cached: SigningKey? = null

    fun exists(): Boolean = keyFile.baseFile.exists() && passwordFile.baseFile.exists()

    /** The key, created on first use (R5.1). Slow the first time: call off the main thread. */
    @Synchronized
```

## 03a529f3c7 `shrimpl/src/main/java/com/valghora/shrimpl/discovery/SiteDiscovery.kt:217` (kdoc)

```
Scripts get their own 20 MB budget: large web apps keep their API calls in big bundles.
```

Followed by:

```kotlin
        private const val SCRIPT_BUDGET_BYTES = 20 * 1024 * 1024

        /** "example.com" -> "https://example.com"; http is upgraded; null if it isn't a web address. */
        fun normalizeUrl(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
            val withScheme = when {
                trimmed.startsWith("https://", ignoreCase = true) -> "https://" + trimmed.substring(8)
                trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring(7)
                "://" in trimmed -> return null
```

## 07e4939995 `shrimpl/src/main/java/com/valghora/shrimpl/install/Installer.kt:20` (kdoc)

```
Session installs need "Install unknown apps" for Shrimpl first (R6.3).
```

Followed by:

```kotlin
    data object NeedsUnknownSources : InstallResult

    /** The user turned down the system installer's confirmation, or stopped waiting for it (R6.5). */
    data object Cancelled : InstallResult

    /** [methodUnavailable]: Shizuku stopped or root was denied, so the system installer is worth offering (R6.2). */
    data class Failure(val message: String, val methodUnavailable: Boolean = false) : InstallResult
}

/** One way to install and uninstall web apps (R1.4, R6.1): the system installer, Shizuku or root. */
```

## 00d1cec73c `shrimpl/src/main/java/com/valghora/shrimpl/install/SessionInstaller.kt:83` (kdoc)

```
A system confirmation screen ([screen]) waiting for the user; [sessionId] is the install session, if any.
```

Followed by:

```kotlin
data class Confirmation(val id: Int, val sessionId: Int, val screen: Intent)

internal const val NO_SESSION = -1

/**
 * Receives PackageInstaller results (not exported). A confirmation request starts the system dialog;
 * the final status completes the waiting install or uninstall.
 */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
```

## 0ef25d54d7 `shrimpl/src/main/java/com/valghora/shrimpl/install/SessionInstaller.kt:135` (kdoc)

```
Mutable: the installer adds the status extras to this explicit intent.
```

Followed by:

```kotlin
        fun sender(context: Context, id: Int): IntentSender {
            val intent = Intent(context, InstallStatusReceiver::class.java).setPackage(context.packageName).putExtra(EXTRA_ID, id)
            return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE).intentSender
        }
    }
}

```

## 063a3408f1 `shrimpl/src/main/java/com/valghora/shrimpl/install/ShellService.kt:6` (kdoc)

```
Shizuku's user service (docs/SHRIMPL_SPEC.md R6.1): Shizuku starts this class in its own process
as the shell user, so `pm` runs with the shell's install permissions and needs no prompt.
Shizuku needs a public no-argument constructor, and R8 must keep it (proguard-rules.pro).
```

Followed by:

```kotlin
class ShellService : IShellService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun install(apk: ParcelFileDescriptor, size: Long): String =
        PmShell.run(PmShell.installCommand(size), ParcelFileDescriptor.AutoCloseInputStream(apk))

    override fun uninstall(packageName: String): String = PmShell.run(PmShell.uninstallCommand(packageName))
}
```

## 00b5e9ccfd `shrimpl/src/main/java/com/valghora/shrimpl/install/ShizukuInstaller.kt:25` (kdoc)

```
Installs through Shizuku (docs/SHRIMPL_SPEC.md R6.1): binds [ShellService], which Shizuku runs as the
shell user, and streams the APK into `pm install`. No prompt per install.
```

Followed by:

```kotlin
class ShizukuInstaller(private val context: Context) : Installer {
    override val method = InstallMethod.SHIZUKU

    private val _state = MutableStateFlow(currentAvailability())

    /** Live availability: Shizuku's binder arrives after app start, and permission can change. */
    val state: StateFlow<Availability> = _state.asStateFlow()

    private val _permissionResults = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

```

## 05ea284e51 `shrimpl/src/main/java/com/valghora/shrimpl/ui/common/BuildController.kt:25` (kdoc)

```
What the build sheet shows (docs/SHRIMPL_SPEC.md R3.4).
```

Followed by:

```kotlin
sealed interface BuildState {
    /**
     * Building [name], as a new app, an update or a reinstall ([kind]). [batch]: which app of an
     * Update all or Fix all it is; null for a single build. [uninstalling]: for Fix (R11.7), whether
     * the old copy is still being uninstalled (true) or already is (false); null without that step.
     * [waitingForUser]: installing or uninstalling, and the system is waiting for the user to
     * confirm (R6.5).
     */
    data class Running(
        val stage: WebAppFactory.Stage,
```

## 05cf5f59bc `shrimpl/src/main/java/com/valghora/shrimpl/ui/common/BuildController.kt:124` (kdoc)

```
Fix and Fix all (R11.7): for each app signed with another key, uninstalls the installed copy,
then builds it again from its record with the current key. Cancelling an uninstall stops the
run; one app alone ends like a single build.
```

Followed by:

```kotlin
    fun startFix(requests: List<BuildRequest>) = startBatch(requests.map { BatchItem(it, uninstallFirst = true) })

    private fun startBatch(items: List<BatchItem>) {
        if (job?.isActive == true || items.isEmpty()) return
        lastRequest = items.singleOrNull()?.request
        batchUpdated = emptyList()
        batchFailed = emptyList()
        batchTotal = items.size
        batchFixing = items.any { it.uninstallFirst }
        runBatch(items)
```

## 0f943b61d0 `shrimpl/src/main/java/com/valghora/shrimpl/ui/common/Notice.kt:51` (kdoc)

```
Text and icons on [container].
```

Followed by:

```kotlin
    val content: Color @Composable get() = deep ?: MaterialTheme.colorScheme.onSecondaryContainer

    /** Status text on ordinary surfaces, such as a failure description. */
    val text: Color @Composable get() = (if (isDarkTheme()) pastel else deep) ?: MaterialTheme.colorScheme.secondary
}

/** Whether the app is showing its dark theme (set in Settings or by the system). */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

```

## 072edb2d4b `shrimpl/src/main/java/com/valghora/shrimpl/ui/common/SettingsItems.kt:166` (kdoc)

```
An on/off item; the switch shows a check or a cross, as in the system settings.
```

Followed by:

```kotlin
@Composable
fun SwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    hint: String? = null,
    enabled: Boolean = true,
) {
    SettingsItem(
```

## 0fd46153a9 `shrimpl/src/main/java/com/valghora/shrimpl/ui/common/SettingsItems.kt:287` (kdoc)

```
A dialog with one text field; [onDone] gets the text as typed.
```

Followed by:

```kotlin
@Composable
fun TextDialog(
    title: String,
    initial: String,
    placeholder: String?,
    keyboardType: KeyboardType,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
```

## 07ea69834f `shrimpl/src/main/java/com/valghora/shrimpl/ui/create/CreateViewModel.kt:84` (line)

```
A changed address starts over: an old failure no longer applies to it.
```

Followed by:

```kotlin
        if (_step.value is Step.Failed) _step.value = Step.EnterUrl
    }

    fun search() {
        if (SiteDiscovery.normalizeUrl(_url.value) == null) {
            _urlInvalid.value = true
            return
        }
        discovery?.cancel()
        val pacer = StepPacer()
```

## 025b76bf6c `shrimpl/src/main/java/com/valghora/shrimpl/ui/home/HomeScreen.kt:336` (line)

```
The system installer asks for each app, so Update all says so first and can be cancelled.
```

Followed by:

```kotlin
    if (confirmUpdateAll) {
        AlertDialog(
            onDismissRequest = { confirmUpdateAll = false },
            title = { Text(pluralStringResource(R.plurals.update_all_title, outdated.size, outdated.size)) },
            text = { Text(pluralStringResource(R.plurals.update_all_text, outdated.size, outdated.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUpdateAll = false
                    model.updateAll(outdated)
                }) { Text(stringResource(R.string.action_update_all)) }
```

## 0cd9ac5651 `shrimpl/src/main/java/com/valghora/shrimpl/ui/home/HomeScreen.kt:580` (line)

```
Every tile keeps the status line's height, so the tiles in a line match.
```

Followed by:

```kotlin
        Text(
            status?.let { stringResource(it) }.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (status == null) Modifier.clearAndSetSemantics {} else Modifier,
        )
    }
}
```

## 0a3d0a5475 `shrimpl/src/main/java/com/valghora/shrimpl/ui/home/HomeScreen.kt:648` (kdoc)

```
How far each row's highlight sits in from the sides.
```

Followed by:

```kotlin
private val ROW_INSET = 4.dp

/** The narrowest a web app item gets before the grid drops a column. */
private val MIN_CELL_WIDTH = 360.dp

/** The space Scaffold leaves between the floating action button and the bottom of the content. */
private val FAB_MARGIN = 16.dp

private const val OTHER_KEY_NOTICE = "other-key-notice"
private const val NOT_INSTALLED_LABEL = "not-installed-label"
```

## 0b074e1250 `shrimpl/src/main/java/com/valghora/shrimpl/ui/home/HomeScreen.kt:716` (kdoc)

```
R2.2: a small icon-only pill (48 x 32 dp, a full 48 dp touch target) with a tooltip naming the
action. Update is in the theme's tonal color; Fix in the Caution yellow and Rebuild in the Success
green, as in the notices.
```

Followed by:

```kotlin
@Composable
private fun RowActionButton(action: RowAction, appName: String) {
    val label = stringResource(action.label, appName)
    val colors = action.tone?.let { IconButtonDefaults.filledTonalIconButtonColors(containerColor = it.container, contentColor = it.content) }
        ?: IconButtonDefaults.filledTonalIconButtonColors()
    WithTooltip(label) {
        FilledTonalIconButton(
            onClick = action.onClick,
            shape = CircleShape,
            colors = colors,
```

## 02cbabc121 `shrimpl/src/main/java/com/valghora/shrimpl/ui/settings/SettingsScreen.kt:203` (line)

```
R5.4: the certificate's SHA-256 fingerprint, with a copy button.
```

Followed by:

```kotlin
                    SettingsItem(
                        stringResource(R.string.signing_certificate),
                        summary = if (fingerprint == null) stringResource(R.string.signing_none) else null,
                        trailing = fingerprint?.let { value ->
                            {
                                TooltipIconButton(stringResource(R.string.action_copy_certificate), { copyFingerprint(context, value) }) {
                                    Icon(CopyIcon, contentDescription = stringResource(R.string.action_copy_certificate))
                                }
                            }
                        },
```

## 014c734bfc `shrimpl/src/main/java/com/valghora/shrimpl/ui/settings/SettingsViewModel.kt:33` (kdoc)

```
Where a root or Shizuku access request stands (the Request Access button's states).
```

Followed by:

```kotlin
    enum class Access { NOT_REQUESTED, REQUESTING, GRANTED, FAILED }

    private val _rootAccess = MutableStateFlow(Access.NOT_REQUESTED)
    val rootAccess: StateFlow<Access> = _rootAccess.asStateFlow()

    // Shizuku can be checked without a prompt, so a permission granted earlier shows as Success.
    private val _shizukuAccess = MutableStateFlow(
        if (container.installers.shizuku.state.value == Availability.AVAILABLE) Access.GRANTED else Access.NOT_REQUESTED,
    )
    val shizukuAccess: StateFlow<Access> = _shizukuAccess.asStateFlow()
```

## 09513b7465 `shrimpl/src/main/java/com/valghora/shrimpl/ui/settings/SettingsViewModel.kt:47` (kdoc)

```
The signing certificate's SHA-256, or null before the first web app.
```

Followed by:

```kotlin
    val fingerprint: StateFlow<String?> = _fingerprint.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** A backup that holds a different key while web apps exist: the user must confirm (R5.2). */
    private val _pendingRestore = MutableStateFlow<SigningKey?>(null)
    val pendingRestore: StateFlow<SigningKey?> = _pendingRestore.asStateFlow()

    val templateVersion: Int get() = container.template.version
```

## 06c13c52c7 `template/src/main/java/com/valghora/shrimpl/webapp/MainActivity.kt:166` (kdoc)

```
A page's location request waiting for Android's location permission prompt.
```

Followed by:

```kotlin
    private var pendingGeolocation: Pair<String, GeolocationPermissions.Callback>? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { finishGeolocation() }

    /** The dialog explaining a permission before Android asks again (T6.5), while it shows. */
    private var permissionDialog: AlertDialog? = null

    /** Permissions whose "off" message this window showed already: once each per window (T6.5). */
    private val offShown = mutableSetOf<String>()
```

## 016dd09613 `template/src/main/java/com/valghora/shrimpl/webapp/MainActivity.kt:253` (line)

```
T8.4: only when the page should not keep running while hidden.
Invariant: lifecycle methods never call pauseTimers() or resumeTimers(); they are process-wide (T8.4).
```

Followed by:

```kotlin
        if (!WebApp.profile.keepAliveWhenHidden && webViewAlive) webView.onResume()
    }

    override fun onStop() {
        super.onStop()
        flushCookies()
        if (!WebApp.profile.keepAliveWhenHidden && webViewAlive) webView.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
```

## 0fca125ef0 `template/src/main/java/com/valghora/shrimpl/webapp/MainActivity.kt:661` (kdoc)

```
Called from the popup's own WebView callbacks, so the teardown is posted (T6.6).
```

Followed by:

```kotlin
        fun dismiss() {
            root.post {
                val shown = dialog
                if (shown != null && shown.isShowing) shown.dismiss() else destroy()
            }
        }

        private fun destroyLater() {
            view.stopLoading()
            root.post { destroy() }
```

## 01687ed848 `template/src/main/java/com/valghora/shrimpl/webapp/MainActivity.kt:851` (line)

```
----- Android permissions for a page (T6.5, android-permissions-security skill) -----
```

Followed by:

```kotlin

    /**
     * Gets Android's [missing] permissions for a page on [host], then [finish]es its request. The first
     * time Android asks directly; after one refusal a dialog explains first ("Not now" turns the page
     * down); once Android no longer asks, a snackbar says the permission is off, with Settings.
     * [finish] answers this request only, so a dialog replaced by another request's turns its own down.
     */
    private fun requestForPage(host: String, missing: List<String>, launch: (Array<String>) -> Unit, finish: () -> Unit) {
        val plan = PermissionPlan.of(missing, prefs::wasAsked, ::shouldShowRequestPermissionRationale)
        if (plan.blocked.isNotEmpty()) showPermissionOff(plan.blocked)
```

## 0eb9c81b81 `template/src/main/java/com/valghora/shrimpl/webapp/MainActivity.kt:1112` (kdoc)

```
The Keyboard Shortcuts Helper's two groups: the site's shortcuts and Window (T5.4).
```

Followed by:

```kotlin
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
```

## 078b6e0fa2 `template/src/main/java/com/valghora/shrimpl/webapp/PageColors.kt:17` (kdoc)

```
The page's colors along its top and bottom edges, for the Dynamic header (docs/TEMPLATE_SPEC.md T3.3):
a document-start script samples them after loading, scrolling, resizing and theme changes, and
posts them here; [onColors] gets them on the main thread whenever they change.
```

Followed by:

```kotlin
class PageColors(private val controller: DesktopSessionController, private val onColors: (top: Int, bottom: Int) -> Unit) {
    private val listener = WebViewCompat.WebMessageListener { _, message, sourceOrigin, isMainFrame, _ ->
        if (!isMainFrame || controller.classify(sourceOrigin.toString()) != UrlClass.INTERNAL) return@WebMessageListener
        val match = MESSAGE.matchEntire(message.data.orEmpty()) ?: return@WebMessageListener
        onColors(match.groupValues[1].toColorInt(), match.groupValues[2].toColorInt())
    }

    /** False when the WebView can't run the script; the header then keeps the system color. */
    fun install(webView: WebView): Boolean {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
```

## 011be8c6ad `template/src/main/java/com/valghora/shrimpl/webapp/SessionPrefs.kt:29` (kdoc)

```
A random token the desktop/mobile shortcut carries, so only that shortcut toggles (T6.11).
```

Followed by:

```kotlin
    val toggleToken: String
        get() = prefs.getString(KEY_TOGGLE_TOKEN, null) ?: UUID.randomUUID().toString().also { token ->
            prefs.edit { putString(KEY_TOGGLE_TOKEN, token) }
        }

    /**
     * Whether the app asked Android for [permission] before (T6.5). Android can't tell "never asked"
     * from "Don't allow" chosen twice; with this it can.
     */
    fun wasAsked(permission: String): Boolean = prefs.getBoolean(KEY_ASKED + permission, false)
```

## 0e991f0d7c `template/src/main/java/com/valghora/shrimpl/webapp/SessionPrefs.kt:35` (kdoc)

```
Whether the app asked Android for [permission] before (T6.5). Android can't tell "never asked"
from "Don't allow" chosen twice; with this it can.
```

Followed by:

```kotlin
    fun wasAsked(permission: String): Boolean = prefs.getBoolean(KEY_ASKED + permission, false)

    fun markAsked(permissions: List<String>) = prefs.edit { permissions.forEach { putBoolean(KEY_ASKED + it, true) } }

    /** A granted permission starts over: if it is revoked in Settings, Android asks again. */
    fun clearAsked(permissions: List<String>) = prefs.edit { permissions.forEach { remove(KEY_ASKED + it) } }

    private companion object {
        const val KEY_ASKED = "asked_"
        const val KEY_TOGGLE_TOKEN = "toggle_token"
```
