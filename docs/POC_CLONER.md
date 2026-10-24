# Proof of concept: cloning an APK on the device

Question: can a phone app take a prebuilt wrapper APK and turn it into a separate, installable app
(its own package, process, storage and launcher entry) without a computer? If so, "give a URL, get an
app" can run entirely on the device.

This branch answers the first half: making and installing the copy. Site discovery and per-site
configuration on the device come later.

## Result (2026-10-24): positive

Tested on a device: the cloner installed, and the "Discord 2" copy it built and signed installed
and ran as expected. CI passes on the branch (host clone test, lint, build).

Android shows strong warnings at several points: installing the sideloaded cloner, allowing it to
install apps, and installing each copy. That is expected for an app that installs other apps. The
plan is to use [Shizuku](https://shizuku.rikka.app/) later: it lets the cloner use ADB-level (shell)
privileges, so installs can go through the shell user like `adb install` does, which avoids the
per-app "install unknown apps" permission and most of the prompts.

## What it does

`cloner/` is a small app that bundles one template APK (by default the committed
`sites/discord/apks/1.1.0/discord-release.apk`). Tapping **Create and install copy**:

1. **Rewrites the compiled manifest** (`ManifestPatcher.kt`). Every string that is the template's
   package name, or starts with it plus ".", gets the new package name. That covers the package
   itself, the `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` permission and the `…androidx-startup`
   provider authority. The last two would otherwise block installing the copy next to the original
   (duplicate permission, conflicting provider). The application label becomes a literal string such
   as "Discord 2". Only the manifest's string pool is rebuilt; the element tree is untouched.
2. **Rewrites the APK** (`ApkCloner.kt`): copies every entry, keeping each one's compression
   (`resources.arsc` must stay uncompressed), and drops the old signature.
3. **Signs it on the device** with Google's `apksig` library, using an RSA 3072 key created in the
   Android Keystore on first use (`SigningKey.kt`). apksig also re-aligns the entries.
4. **Installs it** through `PackageInstaller`. Android asks the user to confirm.

The template's app code (`com.valghora.wrapper.*`) is unchanged, so the copy runs the same code
under a different package.

## Verified on the host

`./gradlew :cloner:testDebugUnitTest` clones the real Discord release APK and checks:

- an unchanged manifest re-encodes byte for byte (the rewriter is lossless);
- the package, permission and authority are renamed, and other strings are untouched;
- the copy verifies with `ApkVerifier` (signature scheme v3; v2 is unnecessary at minSdk 37);
- `resources.arsc` is still stored uncompressed;
- `aapt2 dump badging` reports the new package and label (when the Android SDK is present).

`zipalign -c -P 16 4` and `apksigner verify` also pass on the output.

## How to test on a device

1. Install `cloner-debug.apk`: from the CI artifact `cloner-poc`, or build with
   `./gradlew :cloner:assembleDebug`.
2. Open **APK Cloner PoC**. The first tap opens the "Install unknown apps" setting for the app;
   allow it, go back and tap again.
3. Confirm the install prompt. "Discord 2" appears in the launcher.
4. Make a third copy with another package name (for example `….clone3`), and install the normal
   Discord build too.
5. Check: all copies run at once, each in its own window and recents entry; signing in to one
   doesn't sign in the others (separate storage); notifications from each are separate.

Things to note while testing:

- On a Google-certified device, whether the installer shows any developer-verification warning
  or refusal. This is the biggest open question for the approach.
- How long building and signing takes. The status line reports it.

## Known gaps (fine for a proof of concept)

- **Same icon.** The copy uses the template's icon; replacing it means swapping files inside the
  APK, the next step.
- **The label in recents.** The template sets its task description from its `app_name` string
  resource, which still says "Discord". Fix: read the label from the manifest at runtime.
- **The same site.** Pointing a copy at another website needs the template to read its site
  settings from a file inside the APK at launch, instead of from generated code. That's the main
  refactor if the approach works.
- **Signing key.** It lives only in this app's Keystore. Uninstalling the cloner means copies can't
  be updated again (only uninstalled and recreated).
