# Rubric sample: final

Seed `comment-eval-v1`, 40 of 1023 comment blocks in main sources.

## 051e17f836 `core/src/main/kotlin/com/valghora/shrimpl/core/DesktopSessionController.kt:176` (kdoc)

```
Whether [url] sends the user to the Play Store: `market:`, an `intent:` naming a
package or falling back to a Play Store page, or a Play Store apps page (T6.10).
```

Followed by:

```kotlin
    fun isStoreRedirect(url: String): Boolean {
        val trimmed = url.trim()
        return when (schemeOf(trimmed)) {
            "market" -> true
            "intent" -> trimmed.contains("package=") || intentFallbackUrl(trimmed)?.let(::isPlayStoreAppsUrl) == true
            "http", "https" -> isPlayStoreAppsUrl(trimmed)
            else -> false
        }
    }

```

## 0735104fe2 `core/src/main/kotlin/com/valghora/shrimpl/core/build/ManifestPatcher.kt:44` (kdoc)

```
The bytes aren't a compiled manifest this patcher can read, or lack an element it needs.
```

Followed by:

```kotlin
class ManifestFormatException(message: String) : RuntimeException(message)

/**
 * Reads and rewrites a compiled (binary XML) AndroidManifest.xml, the form it takes inside an APK
 * (docs/SHRIMPL_SPEC.md R8.2): the package, label, versions, permissions and features, and the window
 * values the profile sets (R7.5).
 *
 * Renaming the package also renames every string that starts with the old package name followed by
 * ".": permissions and provider authorities derived from it would otherwise make a copy fail to
 * install next to another one (duplicate permission, conflicting provider). Edits rebuild the string
```

## 022c644978 `core/src/main/kotlin/com/valghora/shrimpl/core/build/SigningKeys.kt:74` (kdoc)

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

## 00c5049dc7 `core/src/main/kotlin/com/valghora/shrimpl/core/build/WebAppBuilder.kt:51` (kdoc)

```
The build was cancelled before it finished; the partial output is deleted.
```

Followed by:

```kotlin
class BuildCancelledException : Exception("Build cancelled")

/**
 * Builds a web app on the device from the template APK (docs/SHRIMPL_SPEC.md R8): patches the
 * manifest, replaces the profile and the icons at their fixed paths (R7.4) and keeps every other entry
 * (R8.3), signs with Shrimpl's key, and verifies the signature, package and label (R8.4).
 */
object WebAppBuilder {
    const val PACKAGE_PREFIX = "com.valghora.shrimpl.app"
    const val TEMPLATE_PACKAGE = "com.valghora.shrimpl.template"
```

## 04e390db6e `core/src/main/kotlin/com/valghora/shrimpl/core/discovery/Discovery.kt:63` (kdoc)

```
[value] as `#RRGGBB`, or null when it is missing, unparsable or
mostly transparent; short hex is expanded and alpha dropped.
```

Followed by:

```kotlin
    fun parse(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        val v = value.trim().lowercase()
        NAMED[v]?.let { return it }
        HEX.matchEntire(v)?.let { m ->
            var h = m.groupValues[1]
            if (h.length == 3 || h.length == 4) {
                h = h.take(3).map { "$it$it" }.joinToString("")
            } else if (h.length == 8 && h.substring(6).toInt(16) < 128) {
                return null
```

## 06fc21b98b `core/src/main/kotlin/com/valghora/shrimpl/core/discovery/Discovery.kt:216` (line)

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

## 0021515b3f `core/src/main/kotlin/com/valghora/shrimpl/core/discovery/HtmlHead.kt:18` (kdoc)

```
The head of static [html] fetched from [pageUrl] (R9.2): title, meta tags, theme
colors and links, made absolute against `<base href>` or the page URL. Scripts,
styles and comments are skipped; only the first million characters are read.
```

Followed by:

```kotlin
    fun parse(html: String, pageUrl: String): PageInfo {
        val text = RAW_TEXT.replace(COMMENT.replace(html.take(MAX_CHARS), ""), "")
        val head = text.substringBefore("</head", text)
        var base = pageUrl
        val metas = mutableListOf<Meta>()
        val links = mutableListOf<Link>()
        val themeColors = mutableListOf<ThemeColor>()
        for (match in TAG.findAll(head)) {
            val attrs = attributes(match.groupValues[2])
            when (match.groupValues[1].lowercase()) {
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

    /**
     * [url] split into scheme (lowercase), netloc, path and query, as
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

## 069e9381c5 `core/src/main/kotlin/com/valghora/shrimpl/core/profile/Profile.kt:89` (kdoc)

```
A window size in dp.
```

Followed by:

```kotlin
@Serializable
data class WindowSize(val width: Int, val height: Int)

/** A key and its modifiers; [key] is an Android `KEYCODE_` name. */
@Serializable
data class KeyCombo(val key: String, val modifiers: Set<Modifier> = emptySet())

```

## 00d79e5084 `core/src/main/kotlin/com/valghora/shrimpl/core/profile/ProfileCodec.kt:9` (kdoc)

```
A profile can't be read: not JSON, an unknown field, or values that fail validation ([errors]).
```

Followed by:

```kotlin
class ProfileException(message: String, val errors: List<FieldError> = emptyList()) : Exception(message)

/** Reads and writes profiles as JSON (docs/SHRIMPL_SPEC.md R7.1, R11.1). */
object ProfileCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
        // Unknown keys are errors: a typo must not silently fall back to a default.
        ignoreUnknownKeys = false
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

## 0b494f2902 `shrimpl/src/main/java/com/valghora/shrimpl/build/WebAppFactory.kt:51` (kdoc)

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

    /**
     * Builds [request] and installs it with [installer], reporting each stage. The record is
```

## 0262be7c8e `shrimpl/src/main/java/com/valghora/shrimpl/build/WebAppFactory.kt:96` (line)

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

## 0021bb4042 `shrimpl/src/main/java/com/valghora/shrimpl/data/AppSettings.kt:35` (kdoc)

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

## 040eec425a `shrimpl/src/main/java/com/valghora/shrimpl/data/WebAppStore.kt:27` (kdoc)

```
The saved record of [appId], or null.
```

Followed by:

```kotlin
    fun get(appId: String): WebAppRecord? = _records.value.firstOrNull { it.appId == appId }

    /** The IDs of every saved record, so a new app's ID can be made unique. */
    fun takenIds(): Set<String> = _records.value.mapTo(mutableSetOf()) { it.appId }

    /** An image icon's stored image, by its file name (`IconRecord.image`). */
    fun image(appId: String, file: String): ByteArray {
        require(IMAGE_NAME.matches(file)) { "invalid image name $file" }
        return File(directory(appId), file).readBytes()
    }
```

## 03a529f3c7 `shrimpl/src/main/java/com/valghora/shrimpl/discovery/SiteDiscovery.kt:226` (kdoc)

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

## 07e4939995 `shrimpl/src/main/java/com/valghora/shrimpl/install/Installer.kt:21` (kdoc)

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

## 00d1cec73c `shrimpl/src/main/java/com/valghora/shrimpl/install/SessionInstaller.kt:84` (kdoc)

```
A system confirmation screen ([screen]) waiting for the user; [sessionId] is the install session, if any.
```

Followed by:

```kotlin
data class Confirmation(val id: Int, val sessionId: Int, val screen: Intent)

/** [Confirmation.sessionId] for a confirmation that belongs to no install session. */
internal const val NO_SESSION = -1

/**
 * Receives PackageInstaller results (not exported). A confirmation request starts the system dialog;
 * the final status completes the waiting install or uninstall.
 */
class InstallStatusReceiver : BroadcastReceiver() {
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

## 05cf5f59bc `shrimpl/src/main/java/com/valghora/shrimpl/ui/common/BuildController.kt:128` (kdoc)

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

## 0ab9f81a48 `shrimpl/src/main/java/com/valghora/shrimpl/ui/common/Notice.kt:146` (line)

```
One line: icon, text and Dismiss centered on one row, Dismiss's own padding at the end.
```

Followed by:

```kotlin
            val placedText = text.measure(Constraints(maxWidth = inlineRoom))
            val inner = maxOf(icon.height, placedText.height, dismiss!!.height)
            val height = inner + INLINE_VERTICAL.roundToPx() * 2
            layout(width, height) {
                pillBackground.measure(Constraints.fixed(width, height)).place(0, 0)
                val mid = height / 2
                icon.place(SIDE.roundToPx(), mid - icon.height / 2)
                placedText.place(textStart, mid - placedText.height / 2)
                dismiss.place(width - INLINE_END.roundToPx() - dismiss.width, mid - dismiss.height / 2)
            }
```

## 072edb2d4b `shrimpl/src/main/java/com/valghora/shrimpl/ui/common/SettingsItems.kt:221` (kdoc)

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

## 07ea69834f `shrimpl/src/main/java/com/valghora/shrimpl/ui/create/CreateViewModel.kt:85` (line)

```
A changed address starts over: an old failure no longer applies to it.
```

Followed by:

```kotlin
        if (_step.value is Step.Failed) _step.value = Step.EnterUrl
    }

    /**
     * Searches the address (R3.2), showing each step for at least half
     * a second; flags an address that isn't one instead of searching.
     */
    fun search() {
        if (SiteDiscovery.normalizeUrl(_url.value) == null) {
            _urlInvalid.value = true
```

## 067eabaaea `shrimpl/src/main/java/com/valghora/shrimpl/ui/edit/EditViewModel.kt:113` (kdoc)

```
Uninstalls the app with the chosen method; after it, offers to remove the record too (R2.3).
```

Followed by:

```kotlin
    fun uninstall() {
        val record = _record.value ?: return
        viewModelScope.launch {
            when (val result = container.installers.preferred().uninstall(record.packageName)) {
                InstallResult.Success -> {
                    _installed.value = false
                    _offerRemove.value = true
                }
                InstallResult.NeedsUnknownSources, InstallResult.Cancelled -> Unit
                is InstallResult.Failure -> _message.value = result.message
```

## 025b76bf6c `shrimpl/src/main/java/com/valghora/shrimpl/ui/home/HomeScreen.kt:352` (line)

```
The system installer asks for each app, so Update all says so first and can be cancelled.
```

Followed by:

```kotlin
    if (confirmUpdateAll) {
        AlertDialog(
            onDismissRequest = { confirmUpdateAll = false },
            title = { Text(pluralStringResource(R.plurals.update_all_title, outdated.size, outdated.size)) },
            text = { Text(stringResource(R.string.update_all_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUpdateAll = false
                    model.updateAll(outdated)
                }) { Text(stringResource(R.string.action_update_all)) }
```

## 0a3d0a5475 `shrimpl/src/main/java/com/valghora/shrimpl/ui/home/HomeScreen.kt:714` (kdoc)

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

## 01c5e38293 `shrimpl/src/main/java/com/valghora/shrimpl/ui/settings/SettingsScreen.kt:119` (line)

```
Beside the list the arrow closes the detail pane (docs/DEVIATIONS.md).
```

Followed by:

```kotlin
                    navigation = {
                        TooltipIconButton(stringResource(R.string.action_back), onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            FormColumnLayout(
```

## 02cbabc121 `shrimpl/src/main/java/com/valghora/shrimpl/ui/settings/SettingsScreen.kt:227` (line)

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

## 014c734bfc `shrimpl/src/main/java/com/valghora/shrimpl/ui/settings/SettingsViewModel.kt:36` (kdoc)

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

## 09513b7465 `shrimpl/src/main/java/com/valghora/shrimpl/ui/settings/SettingsViewModel.kt:50` (kdoc)

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

## 04d9a9e11d `shrimpl/src/main/java/com/valghora/shrimpl/ui/theme/Shapes.kt:31` (kdoc)

```
Where items in a settings group meet.
```

Followed by:

```kotlin
    val ItemInnerRadius = 4.dp

    /** A home tile, or a settings item on its own. */
    val Item: Shape = RoundedCornerShape(ItemRadius)

    /** A home row's press, hover and selection highlight: a pill, like Material's navigation items. */
    val Row: Shape = CircleShape

    /** Notices of more than one line (R3.3): half a one-line notice's usual 56 dp height. */
    val Notice: Shape = RoundedCornerShape(28.dp)
```

## 01eac34939 `template/src/main/java/com/valghora/shrimpl/webapp/FileAccessBridge.kt:388` (kdoc)

```
Replaces the picked file with the write's content, then answers the page.
```

Followed by:

```kotlin
    private fun commit(id: Int, uri: Uri, reply: JavaScriptReplyProxy) {
        val staged = File(writesDir, id.toString())
        try {
            if (failedWrites.remove(id)) throw IOException("Part of the file didn't arrive")
            openForReplace(uri).use { output -> if (staged.exists()) staged.inputStream().use { it.copyTo(output) } }
            post(reply, JSONObject().put("id", id).put("ok", true))
        } catch (e: IOException) {
            main.post { fail(reply, id, "NotReadableError", e.message ?: "The file can't be saved") }
        } catch (_: SecurityException) {
            main.post { fail(reply, id, "NotAllowedError", "Access to the file was lost; pick it again") }
```

## 06c13c52c7 `template/src/main/java/com/valghora/shrimpl/webapp/MainActivity.kt:193` (kdoc)

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

## 016dd09613 `template/src/main/java/com/valghora/shrimpl/webapp/MainActivity.kt:283` (line)

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

## 01687ed848 `template/src/main/java/com/valghora/shrimpl/webapp/MainActivity.kt:893` (line)

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
