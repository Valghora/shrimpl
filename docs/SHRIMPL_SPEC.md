# Shrimpl: specification

Shrimpl is an Android app in which the user enters a website address and, entirely on the device,
gets an installable app for that site. Each generated app is a separate Android package (its own
process, storage, launcher entry and windows) and behaves like an installed PWA on Android desktops
(desktop windowing, Googlebook OS), tablets, foldables and phones.

This file says what the project must be now. Requirement IDs (R0.1, R2.6) are stable: code cites them
(`scripts/spec_map.sh <ID>` lists where), so an ID is never renumbered or reused. Companion documents:

- [docs/TEMPLATE_SPEC.md](TEMPLATE_SPEC.md): what every web app does (T1 to T8).
- [docs/GOOGLEBOOKS.md](GOOGLEBOOKS.md): the primary devices' screens in dp.
- [docs/DEVIATIONS.md](DEVIATIONS.md): every deliberate deviation from this spec, TEMPLATE_SPEC,
  Google's guidance and the vendored skills.
- [docs/BUILD_NOTES.md](BUILD_NOTES.md): toolchain versions and platform findings.
- [docs/TESTING.md](TESTING.md): what is tested where.

## Terms

- **Shrimpl** (the manager): the app the user installs. Package `com.valghora.shrimpl`.
- **Template**: a prebuilt web app APK bundled inside Shrimpl, with all web app code and every
  capability any site may need.
- **Profile**: one site's settings (name, address, domains, colors, permissions, ...) as JSON.
- **Web app**: an APK Shrimpl produced by patching the template with a profile and icon, then
  signing it. User-facing text says "app", never "web app".
- **Record**: what Shrimpl stores about a web app it made (R11.1).
- **Build number**: a web app's version, starting at 1 and incremented on every rebuild.
- **Pending action**: the one thing an app needs, if any: Fix, Update or Reinstall (R2.2).

## Reading this spec

- MUST is mandatory. SHOULD means do it unless there is a technical conflict, and record why in
  docs/DEVIATIONS.md if you don't.
- Precedence when instructions conflict: R0; then platform correctness (builds, installs and runs on
  API 37); then this spec and TEMPLATE_SPEC; then Google's guidance (R1.1). Every
  deviation from the last two is written down.
- Quoted text ("No apps yet") is the exact user-facing string; `<name>` and the like stand for values.
- A requirement met outside the Kotlin code ends with a line *Outside code:* saying where.
- Non-goals: Play Store distribution, analytics, crash reporting, cloud sync or accounts, push
  delivery while a web app isn't running, foreground services, building web apps on a computer, and
  evading sites' WebView sign-in blocks.

## R0 Architecture neutrality (first and foremost)

Every APK this project produces MUST run natively on every Android CPU architecture, ARM and x86,
32 and 64 bit, with no translation layer (NativeBridge, Houdini, libndk_translation or any other
binary translation) on any device.

- R0.1 The template, and so every web app, contains **no native code**: zero `.so` files, no NDK,
  no JNI, no `abiFilters`, no ABI splits. Only dependencies without native code may be added to it.
  *Outside code:* `template/build.gradle.kts` and its dependencies; checked by `scripts/check_native_code.sh` in CI.
- R0.2 Shrimpl adds no native code of its own. A third-party library that ships native code is
  allowed only if it ships it for all four ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64` and `x86`.
  Today that is one library, `libandroidx.graphics.path.so` from Compose's
  androidx.graphics:graphics-path, which is loaded unconditionally on Android and can't be excluded.
  *Outside code:* Shrimpl's dependencies; checked by `scripts/check_native_code.sh` in CI.
- R0.3 Building a web app on the device uses only Kotlin and Java code (apksig, the manifest
  patcher); no native tools such as aapt2 run on the device. Shrimpl MUST NOT bundle native
  executables.
  *Outside code:* the build pipeline (R8) uses only apksig and the manifest patcher.
- R0.4 Enforcement: `scripts/check_native_code.sh --none` on the template and `--all-abis` on both
  Shrimpl builds, in CI (R12.2), and the core test `webAppsHaveNoNativeCode` on every web app it
  builds.
- R0.5 The build host is not constrained by this rule (Android's aapt2 from Maven is x86_64 on
  Linux); only the APKs are.
  *Outside code:* a rule about the build host.

## R1 Shrimpl

- R1.1 Form factors and panes.
  - MUST work on phones, foldables, tablets, in desktop windows and on Googlebook OS (its primary
    devices, docs/GOOGLEBOOKS.md): freely resizable, no aspect ratio limits, no `configChanges`, edge
    to edge, with keyboard and pointer support, in every orientation (WCAG 1.3.4: no orientation
    lock, phones included).
  - It meets the adaptive skill, Google's adaptive app quality guidelines (Tiers 2 and 3) and the
    desktop app quality checklist, except where docs/DEVIATIONS.md says otherwise:
    - layout from window size classes and the pane rules below, never device type;
    - state survives configuration changes and process death: user input, open dialogs and menus,
      and the back stack are kept in saved state or a ViewModel;
    - 48 dp touch targets; every icon-only button has a tooltip, on hover, long press and focus,
      that names its keyboard shortcut where it has one (Settings (Ctrl+,));
    - a right-click menu on apps, the same as their long-press menu;
    - app-wide keyboard shortcuts, Ctrl+N new app and Ctrl+, settings (Ctrl alone, key repeat
      ignored), listed in the Keyboard Shortcuts Helper; Tab moves between controls, and Esc
      closes dialogs and menus without acting as Back;
    - a single instance: Shrimpl is one manager, so it never opens a second window of itself.
  - Two panes show when the window has room:

    | Window | Two panes from |
    |---|---|
    | Filling the screen (a tablet, an unfolded foldable) | 840 dp, Material's Expanded width |
    | Sharing the screen (snapped, split screen, a desktop window) | 1000 dp, so every Googlebook window snapped to half the screen shows one pane (docs/DEVIATIONS.md) |
    | With a separating hinge (a dual-screen phone, a foldable half-open like a book) | always: one pane on each side of the hinge |

  - With two panes, the list and the detail are rounded cards on the window background, 24 dp apart.

    | Window width | List pane | Tiles across (R2.6) |
    |---|---|---|
    | Below 1600 dp | 412 dp, Material's list pane | three, each about 119 dp (124 dp on a phone) |
    | From 1600 dp, Material's Extra-large (every Googlebook's full screen) | 540 dp | four of the same size, since the detail has room to spare beside its 720 dp column |

    The detail takes the rest, so an app's settings always get at least a phone's width. The panes
    can't be resized; resizing the window keeps what each pane shows and any unsaved changes.
  - The detail pane shows an app's edit page (R2.3), the configuration page (R3) or Shrimpl's
    settings (R2.5), each replacing the previous one. The list is always one Back away: Back closes
    what the detail pane shows and leaves it empty; it doesn't leave Shrimpl. Every page in the
    detail pane has a back arrow, which closes it (R3.5).
  - Empty states. With nothing open, the detail pane says to pick an app, or with none yet "Provide
    a website, get a custom app." with *Shrimpl as that!* in italics on its own line, and offers New
    app (R2.4). Empty-state text is exactly centered in each pane, so the two panes line up. With one
    pane, "No apps yet" and that line are centered together as one block between the top bar and
    the top of the New app button.
- R1.2 Kotlin only. The single exception: discovery injects JavaScript into a hidden WebView to read
  what only exists in the rendered page (R9.3).
  *Outside code:* a rule for the whole repository.
- R1.3 Material 3 in Jetpack Compose (latest stable, Expressive components where stable), dynamic
  color, light and dark themes.
  - The status and navigation bars (the 3-button bar included) are transparent with no contrast
    scrim, so the screen's body color shows through; their icons follow Shrimpl's Theme setting.
  - Navigation is Jetpack Navigation 3 with Material's list-detail scene strategy
    (`adaptive-navigation3`), not `ListDetailPaneScaffold`.
  - Content descriptions on every icon; font scaling supported.
  - The template stays on Views (T1.1).
  - Shrimpl's launcher icon is a white shrimp on #CC5500, with a monochrome layer for themed icons.
- R1.4 Three install methods behind one interface (R6): System (the platform installer), Shevery and
  Root.
- R1.5 Shapes: rounded corners from Material's shape scale, one size per role, all defined in
  `ui/theme/Shapes.kt`, which also sets Material's theme shapes so dialogs, sheets, chips, tooltips
  and snackbars follow.

  | Role | Corners | Used for |
  |---|---|---|
  | Input | 12 dp | text fields |
  | Search | fully round | the website field on Create (R3.1) |
  | Control | fully round | buttons with a container: New app, Create, Save, Update all, Fix all, row buttons, the search button, buttons in sheets and settings |
  | (chips) | Material's 8 dp | chips |
  | Item | 20 dp outside, 4 dp where grouped items meet | home tiles, settings groups |
  | Row | fully round | a home row's press, hover and selection highlight (R2.1) |
  | Notice | a pill when one line, at any text size; 28 dp when taller | notices (R3.3) |
  | Menu | 16 dp, and 12 dp for an item's highlight inside it | menus; also the press highlight of icon option tiles and custom icon sources |
  | Pane | 16 dp, tighter than sheets so the desktop layout stays crisp | the two-pane cards |
  | Surface | 28 dp | bottom sheets and dialogs |
  | Round | circle | app icons, badges, color swatches, progress marks, icon-only buttons without a container |
- R1.6 Settings wording, on the web app form (R3.3) and in Settings (R2.5):
  - A switch has a fixed label naming what it controls and a fixed description of what it does when
    on; the switch shows the state, and accessibility services announce it. The description says what
    off does only when that isn't simply "it doesn't happen".
  - A choice (dropdown or list) shows a description of the selected value.
  - Descriptions start with a present-tense verb and no subject (Keeps, Opens), say what changes
    for the user, and use "app" and plain words, not internals such as the user agent or ADB.
  - "Select" is the pointing verb, since Googlebooks have touch and a trackpad; labels are sentence
    case.
  *Outside code:* the wording of `shrimpl/src/main/res/values/strings.xml`, kept by review.

## R2 Home

- R2.1 Layout and gestures.
  - A top app bar in the settings pages' large-title style (R3.5): the title "My Apps" in large
    type, then a layout switch ("Show as tiles" / "Show as list", R2.6) and a settings button at its
    end. With one pane the title shrinks to the usual bar size as the list scrolls; beside the detail
    pane the bar stays put.
  - Under it, an adaptive grid of the apps Shrimpl created: one column on phones, more on wide
    windows.
  - A row is borderless on the page background. Its pill (Row corners, R1.5) shows only as its press
    and hover highlight, and in two panes as the selection (the hover tint, so a tonal button on it
    stays visible).
  - A row holds: the installed icon (48 dp); the name, with no status text (accessibility services
    hear the status as the row's state, R2.2); the row button of its state (R2.2); and a
    more-options (three vertical dots) button (`Options for <name>`), lined up under the settings
    button, which opens the app's menu.
  - Build numbers are never shown.
  - A tap does what the app's state says (R2.2).
  - Right-click or long press opens the same menu as the ⋮ button. A long press vibrates as it opens,
    on rows and tiles alike, unless Haptic feedback is off (R2.5).
- R2.2 The list and app states.
  - The list comes from Shrimpl's records (R11) joined with the installed state, re-read on resume.
    An empty list says "No apps yet".
  - Installed apps come first; apps that aren't installed follow under a "Not installed" label (as
    rows; tiles have no label, R2.6). Each group is in name order.
  - Each app is in one state, which sets everything below:

    | State | When | Row button | Tile badge (R2.6) | Accessibility state | Tap | Menu, in order |
    |---|---|---|---|---|---|---|
    | Fix required | installed, signed with another key (R11.7) | Fix: a wrench, red | red wrench | "Fix required" | nothing (with Selecting fixes first: Fix) | Fix, Edit, Remove |
    | Update ready | installed, built from an older template (R11.4) | Update: an up arrow, green, right of the name | green up arrow | "Update ready" | the Default select action (with Selecting fixes first: Update) | Update, Open, Edit, Uninstall |
    | Not installed | a record whose app isn't installed | Reinstall: Material's "replay", yellow | yellow "replay" | "Not installed" | nothing, and no tap is offered to accessibility services; Selecting fixes first doesn't apply | Reinstall, Edit, Remove |
    | Installed | none of the above | none | none | none | the Default select action (R2.5) | Open, Edit, Rebuild, Uninstall |

  - The ⋮ menu, the right-click menu and the long-press menu ("Show options") offer the same actions.
    The pending action is always first; Rebuild appears only when there is none.
  - Uninstall, here and on the edit page (R2.3), asks once: "Uninstall <name>?", "The app and its
    data will be completely removed from your device. You can select the box below to keep <name>
    in your app list if you wish to reinstall later.", with a checkbox "Keep in app list",
    unchecked, then Cancel or Uninstall. Once the app is uninstalled, it leaves the list
    unless the box was checked, which keeps it as Not installed. A turned-down or failed uninstall
    keeps it either way.
  - Update rebuilds with the bundled template. Reinstall builds the app signed with the current key,
    whatever key it had (R11.7).
  - A not-installed app's icon and name are greyed out (65 % opacity, so the name keeps 4.5:1
    contrast in both themes); its row button keeps its colors.
  - Row buttons are small icon-only buttons (48 x 32 dp, Control corners, a 48 dp touch target) whose
    tooltip names the action and the app. Each is in its status's pastel with the deep shade for the
    icon (Notice.kt's Tone, fixed on every theme, 7:1 contrast; or as the Badge theme sets, R2.5),
    matching the tile badges. The two line icons (replay, up arrow) are weighted to match the solid
    wrench.
- R2.3 The edit page.
  - It offers every profile setting in the form of R3.3 (the app ID is not shown) and the icon
    options (R10).
  - Its ⋮ menu offers, pending action first as on home (R2.2): Reinstall (when not installed), Open
    and Rebuild (when installed), and Uninstall (when installed; asked as in R2.2, and the page closes
    when the app leaves the list) or Remove.
  - For an installed app signed with another key (R11.7) it offers Fix, then Remove; a Caution
    notice says that saving or fixing uninstalls the app (its settings are kept, not its site data);
    and Save fixes it with the changes.
  - Once something is changed, a Save button appears in the top bar, before the ⋮ button (the page
    has no floating button, so its column is centred like Settings'). Save rebuilds with the next
    build number and installs it as an update (same package and key, so the app keeps its data).
  - Changes not saved are discarded on leaving the page; recreating the window (rotation, resize)
    keeps them.
  - Its back arrow leaves the page, in two panes too.
- R2.4 New app, Update all.
  - "New app" opens the configuration page (R3), titled "Create app". Its tooltip names Ctrl+N.
  - New app, Create and Update all share one design: a filled button with Control corners (R1.5)
    and an icon, in the theme's primary color (Update all in its secondary container color).
  - Where New app sits: with one pane, at the bottom right of the list. With two panes, in exactly
    one place: in the empty detail pane, or at the bottom right of the list while the detail pane
    shows an app's settings or Shrimpl's settings (not while it shows the configuration page).
  - When two or more installed apps have a template update, "Update all" sits above New app in the
    list, the two the same width. It rebuilds those apps one after another; a failure doesn't stop
    the rest; the build sheet shows "Updating <name> (<i> of <n>)…" and ends with what was updated and
    what failed.
  - With the System install method, Update all first asks: "Android will ask you for permission to
    perform each update. To update your apps without prompts, switch your installation method to
    Shevery or Root via Settings.", with Cancel and Update all.
  - Fix all (R11.7) sits in the same place and works the same way.
- R2.5 Settings, from the top bar button or Ctrl+,. Descriptions are quoted exactly and follow R1.6.

  | Section | Setting | Values | Behavior |
  |---|---|---|---|
  | Appearance | Theme | System (default): "Follows the system's light or dark theme"; Light: "Always uses the light theme"; Dark: "Always uses the dark theme" | Light or dark only: every choice uses the wallpaper's dynamic colors (R1.3). |
  | Appearance | Badge theme | Shrimpl (default): "Each status keeps its own pastel color"; Harmony: "Status colors are tinted toward your wallpaper's colors"; Mono: "Every status uses your wallpaper's main color" | Home badges and row buttons (R2.2, R2.6) only, not notices. Shrimpl: each status's fixed pastel and deep shade. Harmony: both run through Material's harmonize with the theme's primary color, the hue turned up to 15 degrees and the tone kept, so 7:1 contrast holds. Mono: the theme's primary container and on-primary-container colors for every status, which the symbols then tell apart. |
  | Installation | Method | System: "Android asks you to confirm each install"; Shevery: "Installs silently through Shevery"; Root: "Installs silently with root access" (R6.2) | Shevery and Root add the Request Shevery / Request Root item. |
  | Behavior | Default select action | Edit (default): "Opens the app's settings"; Open: "Opens the app" | What a tap does on an installed app with no pending action, or with Selecting fixes first off. |
  | Behavior | Selecting fixes first | a switch, off by default: "When an app needs an update, fix or reinstall, selecting it does that instead." | On: tapping an installed app with a pending action (Fix or Update) does that action instead, in rows and tiles, and accessibility services hear the tap named after it ("Fix Example"). Off: the Default select action applies, and an app signed with another key still does nothing on a tap. An app that isn't installed ignores the switch (R2.2). |
  | Behavior | Haptic feedback | a switch at the end of the section, on by default: "Vibrates when a long press opens a menu" | On: a long press that opens an app's menu vibrates (R2.1); the system's touch feedback setting still applies. |
  | Signing | | the certificate's SHA-256 fingerprint with a copy button, "Export…" and "Import…" | Export and import the certificate (R5). |
  | About | "Shrimpl <version>" with the update state, and Update check frequency | R14.3 | Shrimpl's own updates from GitHub (R14). |

- R2.6 Tiles: the layout switch shows the apps as tiles instead of rows, and the choice is kept.
  - Tiles flow three across on phones (at least 100 dp wide, 8 dp apart, 12 dp in from the sides),
    more on wide windows. From the Medium width class (600 dp) a tile is at least 118 dp wide, near
    its phone width, so names aren't cut short: three across beside the detail pane, four from
    1600 dp (R1.1), six across a window snapped to half a laptop screen (about 850 dp).
  - A tile is a card with Item corners (R1.5) in the theme's container color, with no tint of its
    own: the icon (56 dp) and the name, 16 dp of space above and below. The name's line spacing above
    its first line and below its last is trimmed, so the space under it looks the same as the space
    above the icon.
  - With a font size above the default, a name may take a second line instead of being cut short.
    Every tile keeps two lines' height, so the tiles in a line match, with a one-line name centred;
    the number across doesn't change.
  - No status text: accessibility services hear the status (R2.2) as the tile's state.
  - A round badge on the icon's corner (28 dp with a 16 dp symbol), ringed in the tile's color,
    marks an app with a pending action, in the row buttons' colors (R2.2). The Not installed badge
    sits outside the dimmed icon, so it keeps its colors. Accessibility services skip the badge.
  - Uninstalled apps follow the installed ones in the same grid, greyed out as in rows.
  - Tiles have no ⋮ button or row buttons: a tap does what it does on a row, and right-click or long
    press opens the row's menu (R2.2).
  - In two panes the selected tile takes the highest container color.

## R3 Configuration page

- R3.1 Address.
  - A Website field (URI keyboard, `https://` assumed without a scheme) with a search button beside
    it; the IME action searches too.
  - Text fields everywhere are filled, with Input corners (the website field a Search pill, R1.5) and
    no outline; typing draws a primary edge, an error a red one with the message below.
  - The search button is a circle of the field's height, in the tonal container color.
  - Input that isn't a web address shows an error under the field, before any search.
- R3.2 Discovery (R9) progress and errors.
  - A checklist under "Searching…": Loading the site, Consulting the manifest, Researching
    permissions, Searching for icon. Finished steps get a check, the current one a spinner (as in the
    build sheet), the others an empty circle; each step shows for at least 0.5 s. Cancel sits at the
    end.
  - Only loading the site can fail, with a plain message under the step:

    | Cause | Message |
    |---|---|
    | 404, or an unknown host | "The site could not be found" |
    | another 4xx | "The site responded with an error" |
    | 5xx | "The site is unavailable" |
    | no connection | "No internet connection" |
    | no answer in 15 s | "The site took too long to respond" |
    | a certificate problem | "The site's certificate is invalid" |
    | anything else | "The site couldn't be loaded" |

  - A 403 continues to the rendered pass, since bot protection often refuses plain requests but lets
    a browser in. Only if the rendered page is refused too does Researching permissions fail, with
    "The site denied the request".
  - On failure the heading reads "Search failed"; the search button becomes Retry (a refresh icon)
    until the address is edited; and a Continue button (defaults for the address) floats at the
    bottom right.
- R3.3 Review: a form prefilled from discovery, laid out like the system settings.
  - Titled sections of tonal items 3 dp apart, each group with Item corners (R1.5): the first item
    rounded at the top, the last at the bottom, a lone item all round. Settings (R2.5) uses the same
    sections.
  - Each item shows its value (summary, switch, menu value or color swatch); text, color, domain and
    window size values open dialogs. Nothing is collapsed.
  - Each section has one wrap line for its items' text (description, error, discovery hint): 24 dp
    before the widest value, switch or chevron of any of its items, so no description runs into one
    and the section's text has one right edge. The line is measured, not fixed, so a longer value or
    a larger font moves it. The icon options are not items and are exempt.
  - The sections, in order:
    - **Identity:** the name, Address (the start address) and Domains (the internal domains).
    - **Icon:** the icon options as preview tiles, then the selected option's settings (R10.1):
      Glyph color (`#FFFFFF` by default) and Background color (the site's brand color by default)
      for Brand, Fill icon canvas for a Website image, Background color for Custom.
    - **Appearance:** Header color (System, "Matches the system's light or dark theme", the default;
      Dynamic, "Uses the site's own colors", T3.3; Custom, "Uses the color you choose below") and
      Custom header color (disabled unless Custom).
    - **Behavior:** Site layout, the user agent (Auto, "Picks the desktop or mobile site
      automatically"; Desktop, "Asks for the site's desktop layout"; Mobile, "Asks for the site's
      mobile layout"); Link and image menu (Site, the default, "A long press or right-click shows the
      site's own menu."; Shrimpl, "A long press or right-click shows Shrimpl's menu to copy, open or
      save links and images.", T5.5); Run in background; Pause in battery saver.
    - **Window:** Allow multiple windows ("Lets the app open in more than one window", off by
      default), default and minimum window size.
    - **Permissions:** Microphone, Camera, Notifications and Location, off by default, with no
      description; one that discovery turned on shows the line naming where that came from, and
      one it left off shows nothing.
  - Site keyboard shortcuts (`appShortcuts`) are not editable in the form; a profile keeps any it has.
  - Values discovered with low or medium confidence say where they came from (a permission only when
    discovery turned it on); discovery's notes
    appear as Info, Caution or Error notices.
  - Every field is validated as the user types, by the core validator (R4.2).
  - Notices (here and on home, R11.7) have Notice corners (R1.5):
    - Every dismissable notice has a Dismiss button (never an X) and is also dismissed by swiping it
      left or right.
    - A one-paragraph notice with no other button, whose Dismiss fits on its line, keeps it there, so
      short notices stay one line tall. Otherwise the buttons sit in a row under the text, their
      labels 12 dp below it and 12 dp above the card's bottom.
    - Dismiss always ends the row, its button as far from the card's end as the text is from the
      card's start (the icon column mirrored), whatever the card's width.
    - Another button (such as Open settings) depends on the card's width, not the window's:
      - narrower than 480 dp (a phone, or a narrow pane), the two split to the row's ends: the other
        button starts where the text starts, and Dismiss ends at its mirrored inset;
      - from 480 dp, where splitting would leave a wide gap between them, both sit together at the
        end, the other button 16 dp before Dismiss.
    - A site that needs a Google sign-in gets a Caution notice: "Google doesn't allow signing in from
      apps like this one. To use your Google account, open the site in Chrome and install it from
      Chrome's menu." Its button, Open in Chrome, opens the start address in Chrome, where the user can
      sign in and install the site as an app (Android lets no other app start that install). Without
      Chrome it reads Open in browser, the text says "your browser" and "the browser's menu", and the
      default browser opens; with no browser, a toast says so. Create stays available.
- R3.4 Create builds, signs and installs (R8, R6), in a bottom sheet.
  - Its title: "Creating an app for <name>…", "Updating <name>…" for an update, or "Reinstalling
    <name>…" for a known app that isn't installed, with "(n of m)" during Update all and Reinstall
    all.
  - It shows each stage (drawing, building, signing, checking, installing; each on screen for at least
    0.5 s), or the failure.
  - On success: Open and Done, with no build number (build numbers are internal, R11.3). A new app
    from the Create app page instead drops the user back on the app list, after the one-time backup
    prompt (R5.3).
- R3.5 The page layout, shared by the configuration page, the edit page and Settings (R2.5): one
  column and one top bar from the same code, so the pages always match.
  - Beside the list (two panes) the column is at most 720 dp wide, so a description beside a value
    runs about 70 characters a line, and centred. On a page with a floating button (Create), if
    centring would put the column under the button at the bottom right, it moves left as far as
    needed (at least 24 dp from the left edge), so the button never covers an option.
  - With one pane (a phone, or any window too narrow for two panes), or in a pane too narrow for a
    360 dp column, the form takes the full width (16 dp margins) and the button floats over it, with
    room below the last section. With one pane it is still at most 720 dp wide, centred, so every
    Googlebook window snapped to half the screen shows the settings at the same width, with the top
    bar's arrow and buttons over it.
  - The top bar holds a back arrow and the page's title (the app's name, Settings, or Create app) in
    large type on one line, with the edit page's ⋮ at the end.
  - With one pane the arrow starts the bar; as the page scrolls, the title shrinks to the usual size
    and the bar takes the container color.
  - Beside the list the bar stays put while the page scrolls under it, in the pane's color, with no
    divider, also in a pane too narrow for the column, where the page fills the pane. The arrow's
    glyph starts where the section labels do, the title follows it, and ⋮ sits over the items'
    trailing chevrons. There the arrow closes the detail pane.

## R4 Modules

- R4.1 Three Gradle modules: `:core` (Kotlin JVM), `:template` (Android app, the web app) and
  `:shrimpl` (Android app, the manager). Both apps depend on `:core`; `:shrimpl` bundles the
  template's release APK (R8.1).
  *Outside code:* `settings.gradle.kts` and each module's build file.
- R4.2 `:core` MUST NOT import `android.*`, `androidx.*` or `com.google.android.*`. It holds:
  - the profile model, JSON codec (kotlinx.serialization) and validator with field-level messages
    (domains, an https start address covered by the internal domains, colors, window sizes,
    shortcuts), and derived values (header darkness, automatic glyph color, allowed origin rules,
    notification channel ID, the session config);
  - `DesktopSessionController` (T2);
  - the discovery rules (R9.2), icon geometry and SVG glyph handling (R10);
  - the web app records (R11) and the editing model (`WebAppDraft`);
  - `ManifestPatcher`, `WebAppBuilder` and `SigningKeys` (R5, R8).
- R4.3 `:core` tests reach at least 95 % line coverage (JaCoCo, verified by `:core:check`); they
  include the patcher and builder run against the real template release APK, and discovery on stored
  fixtures of three real sites and a made-up one.
  *Outside code:* `core/build.gradle.kts` (JaCoCo verification in `:core:check`).
- R4.4 Dependencies are those in `gradle/libs.versions.toml`: for `:core` only kotlinx-serialization-json
  and apksig, for `:template` exactly T1.1's. A new runtime dependency MUST satisfy R0 and is recorded
  in docs/BUILD_NOTES.md.
  *Outside code:* `gradle/libs.versions.toml` and the modules' build files.

## R5 Signing key

- R5.1 On first use Shrimpl generates one RSA 3072 key with a self-signed certificate (CN=Shrimpl
  apps; an older key keeps its own, CN=Shrimpl web apps), stored as PKCS12 in app-private
  storage; the file's random password is encrypted with an AES key in the Android Keystore.
- R5.2 Export certificate writes the PKCS12 through the Storage Access Framework, protected by a
  password the user types twice (at least 8 characters). Import certificate replaces the key after
  its password, and asks for confirmation when apps exist and the imported certificate differs (they
  could no longer be updated).
- R5.3 After the first app is created without a backup, a one-time prompt explains why to back up,
  with "Back up now" and "Later".
- R5.4 Settings shows the certificate's SHA-256 fingerprint, with a copy button.

## R6 Install methods

- R6.1 One `Installer` interface (install, uninstall, availability) with three implementations:
  - **Session:** `PackageInstaller` sessions; REQUEST_INSTALL_PACKAGES. Sessions ask for
    `USER_ACTION_NOT_REQUIRED`, but Android still asks the user to confirm every install and every
    update (the emulator's `BuildAndInstallTest` taps Update), hence R2.4's warning.
  - **Shevery:** a Shevery user service running `pm install -S` with the APK piped in; no prompts.
    The service refuses every install and uninstall that doesn't come from Shrimpl's own app ID, so no
    other app can use the shell's permissions through it.
  - **Root:** `su -c pm install -S` with the APK piped in; `pm uninstall` for uninstalls.
- R6.2 Settings has one Method choice: System (default; every install asks for confirmation),
  Shevery (silent, through ADB) or Root (silent, through superuser), described in R2.5.
  - Choosing Shevery or Root shows a Request item (Request Shevery with "Asks Shevery for access",
    or Request Root with no description; button Request). Success turns the button into Success (green); failure shows "Failed to
    acquire root" / "Failed to acquire Shevery" in red and the button becomes Retry (red). The button keeps
    one size in every state, a spinner in its centre while the request runs, so it doesn't move.
  - If the chosen method fails at install time, Shrimpl says so and offers the system installer for
    this install.
- R6.3 The Session method needs "Install unknown apps"; if it's missing, the build sheet explains it,
  opens the setting and continues when the user returns.
- R6.4 Uninstall uses the selected method (Session: the system's uninstall dialog).
- R6.5 With the Session method, the install waits for the system's confirmation.
  - Turning it down ends the build quietly: the sheet closes and nothing is saved (Update all stops,
    listing what was updated).
  - If the user leaves the confirmation unfinished and comes back to Shrimpl, the sheet says Android
    is waiting and offers Show installer (opens the confirmation again) and Cancel (abandons the
    install session, so it can't finish later without a record).

## R7 The template in Shrimpl

The template's behavior is docs/TEMPLATE_SPEC.md. What Shrimpl relies on:

- R7.1 The template reads its profile from the asset `assets/shrimpl/profile.json` once at
  application start and fails loudly (a crash with a clear message) if it is missing or invalid.
- R7.2 Labels come from the patched manifest (application label) and the profile (task description,
  notification channel, the site's shortcut group).
- R7.3 The manifest declares the superset of permissions and features (T4.2); code for a disabled
  capability never requests its permission.
- R7.4 The launcher icon is an adaptive icon whose background, foreground and monochrome layers, and
  the notification icon, are PNGs at fixed paths (`res/drawable-nodpi-v4/...`), placeholders in the
  template. Resource path shortening is off (`android.enableResourceOptimizations=false`), checked by
  a core test.
- R7.5 `<layout>` sizes, the launch mode and the multi-instance property are manifest values the
  patcher rewrites.
- R7.6 The template's package is the placeholder `com.valghora.shrimpl.template`; its namespace is
  fixed at `com.valghora.shrimpl.webapp`, so classes keep their names in every web app.
  *Outside code:* `template/build.gradle.kts` (applicationId and namespace).

## R8 Building a web app (on the device)

- R8.1 `:shrimpl` bundles the template's release APK, built in the same Gradle invocation, as
  `assets/template/template.apk`, with its version (R11.4). Shrimpl copies it out once per install
  of itself (an update included), never keyed on the version alone, so a new build of Shrimpl always
  builds web apps from its own template.
- R8.2 Manifest patching (binary XML, in `:core`): package `com.valghora.shrimpl.app.<appId>` (and
  every string derived from the template package), the literal label, versionCode = build number,
  versionName = `<template version>.<build>` (for example `1.0.0.12`, R11.8), removal of disabled permissions
  and features, launch mode, `<layout>` sizes and the multi-instance flag.
- R8.3 Assembly replaces `assets/shrimpl/profile.json` and the icon PNGs, keeps every other entry and
  its compression, drops old signatures, and signs with the Shrimpl key using apksig (v2 and v3 as
  apksig chooses for minSdk 37).
- R8.4 Before installing, verify the signature (`ApkVerifier`) and that the package and label read
  back from the patched manifest are the intended ones; fail with a clear message otherwise.
- R8.5 Host tests in `:core`, against the real template release APK: package rename, label,
  versions, permission stripping, window values, profile and icon replacement, signature, stable icon
  paths, no native code (R0), and `aapt2 dump badging` when the SDK is present.
- R8.6 Building runs off the main thread, can be cancelled before install, and reports its stages.

## R9 Discovery (on the device)

- R9.1 Input: a URL. Output: a draft profile in which every field carries a source and a confidence
  (high, medium, low), plus notes with a level (info, caution, error).
- R9.2 Static pass (rules in `:core`, network in `:shrimpl`): fetch the page following redirects and
  read the HTML head (title, `application-name`, `apple-mobile-web-app-title`, `og:site_name`,
  `theme-color` with media, manifest link, `mask-icon`, icon links, `apple-touch-icon`) and the web
  manifest (name, short_name, start_url, theme_color, background_color, icons). Rules:
  - name: manifest short_name and name, then the head's names, then a cleaned title;
  - start address: the manifest's start_url on the same site, else the requested address; an auth
    redirect keeps the requested path;
  - internal domains: the registrable domain from the bundled Public Suffix List;
  - simple-icons, by name and not bundled: the slugs of the site's own names, then the domain word,
    looked up at `https://cdn.simpleicons.org/<slug>` (at most 4 requests);
  - icon: the simple-icons glyph, then mask-icon, a maskable manifest icon, the largest icon, a letter;
  - icon background, the brand color: the simple-icons color, else the first colorful value among
    the mask-icon color, manifest theme_color, theme-color meta, CSS brand variables (R9.3), button
    colors and manifest background_color, else any non-white theme color, else blue gray;
  - no header color is suggested (apps default to System);
  - notes for pages that refuse WebViews and for Play Store redirects;
  - instead of the WebView note, a Google sign-in note carrying the start address (R3.3) when the
    site is google.com or one of its subdomains, the WebView pass went to accounts.google.com, or the
    rendered page shows Google's refusal (`disallowed_useragent`, `Couldn't sign you in`, `This
    browser or app may not be secure`).
- R9.3 Rendered pass (SHOULD), in a hidden WebView with desktop (light and dark) and phone viewports:
  CSS custom properties named brand, primary or accent (ranked in `:core`), visible button colors,
  and use of getUserMedia, Notification, Push, WebRTC and geolocation (suggesting microphone,
  camera, notifications and location). At most 20 s in total; on timeout the static result stands.
  WebView exposes no per-request resource type, so request types come from resource timing (`xhr`,
  `fetch`, `script`, ...) and WebSocket hosts aren't seen; the visit's HTTP status is known only when
  it is an error (400 or above).
- R9.4 Network: HTTPS only (http is upgraded), 15 s per request, 5 MB per response (scripts have a
  20 MB budget), no cookies kept.

## R10 Icons

- R10.1 Options, shown as a row of preview tiles labeled Brand, Website and Custom; tapping one
  selects it and rings it (no other mark).

  | Option | What it is | Its settings |
  |---|---|---|
  | **Brand** | the simple-icons glyph (or the site's mask-icon glyph) in the Glyph color on the Background color. The default when found; not shown when the site has no brand glyph. | Glyph color, Background color |
  | **Website** | the site's own icon: the best square web manifest icon, apple-touch-icon or icon link of at least 192 px (R9.2); a letter on the background color when the site has none. Selected when there is no brand glyph. Changing the name updates a letter icon. | for an image, Fill icon canvas: a switch, on for maskable manifest icons, that enlarges the image to fill the whole icon shape instead of keeping it inside the safe zone |
  | **Custom** | a dashed tile with a plus until an icon is picked. Tapping it (empty, or again once selected) opens a sheet of sources: Photos (the Photo Picker) and Files (any image), cropped to a centered square and filling the icon shape, and every installed icon pack (R10.2). | Background color, behind and around the picked image |

  The selected option's settings show under the tiles.
- R10.2 Icon packs: installed apps answering the launchers' `org.adw.launcher.icons.ACTION_PICK_ICON`
  request, shown with their own app icon and name. Choosing one opens the pack's own icon picker,
  which hands back a bitmap (or an image URI); the icon keeps its own shape. Packs that don't answer
  that request aren't listed.
- R10.3 Rendering: adaptive background and foreground layers at 432x432 px (foreground inside the
  66 % safe zone, or 80 % for maskable images), a monochrome layer (the glyph or letter; a letter for
  images), and a 96x96 px white notification icon. Letters use the system bold font; none is bundled.

## R11 Data and versions

- R11.1 A record per web app, as JSON in app-private storage, written atomically:
  - the profile;
  - the icon options (brand glyph, website icon, custom icon, which is selected), images as centered
    square PNGs of at most 512 px next to the record (unused ones deleted on save), and the icon the
    last build used;
  - the package name, the build number, the template version it was built with, and created and
    updated times.

  Older profiles and records are upgraded when read.
- R11.2 The app ID comes from the name (lowercase letters and digits, starting with a letter),
  deduplicated with a numeric suffix, and is fixed at creation.
- R11.3 Rebuilds increment the build number; a failed install doesn't use one up.
- R11.4 The template version is `shrimpl.templateVersion` in `gradle.properties` (R11.8), and its code
  is the template's versionCode.
  - It MUST be raised whenever template code or resources change (R11.8).
  - A version is never reused, even for a build that was thrown away (apps built with it would show
    no update). A raise with no template change is allowed, to offer every installed app an Update.
  - Records built with an older version are in the Update ready state (R2.2); Update rebuilds with the
    bundled template.
- R11.5 Shrimpl never reads or modifies web apps' private data.
  *Outside code:* a rule that no code reads web apps' data.
- R11.6 Recovery after Shrimpl is reinstalled.
  - Each built web app carries what is needed to list it again: its profile, and its icon options as
    `assets/shrimpl/icons.json` with their images beside it (from template version 7; the builder
    adds them, `WebAppSpec.extras`, not the template).
  - On its first start with no records, Shrimpl reads every installed `com.valghora.shrimpl.app.*`
    APK and rebuilds its record: profile, icon options, build number (versionCode), template version
    (versionName) and signer. Apps built before icons.json come back with their drawn launcher icon
    as a Custom icon.
  - This search runs once, so apps removed from the list stay removed.
  - Apps signed with another key are handled by R11.7.
- R11.7 An app last signed with a key other than Shrimpl's current one keeps its record either way.
  - Installed, it is in the Fix required state (R2.2). Save on the edit page fixes it (R2.3). Update
    all skips it.
  - Fix uninstalls the app (the system asks), then rebuilds it from the saved settings signed with
    the current key (the system asks again). The build sheet adds an Uninstalling step before the
    build's stages; turning the uninstall down stops with nothing changed.
  - With two or more such apps, Fix all (a button above New app, like Update all, R2.4) fixes them one
    after another and ends with what was fixed and what failed.
  - Not installed (say, its Fix stopped after the uninstall), the key no longer matters: it is shown
    and reinstalled like any app that isn't installed (R2.2).
  - Home shows a Caution notice explaining both ways out (import the certificate backup, R5.2, or
    Fix, keeping the settings but not the site data), with Open settings at the start and Dismiss at
    the end (or a swipe, R3.3). Dismiss hides it for the apps it covered; another such app shows it
    again.
  - Only installed apps count for the notice and Fix all.
- R11.8 Versions. Shrimpl (`shrimpl.appVersion`) and the template (`shrimpl.templateVersion`) each
  have a version `major.minor.patch`, in `gradle.properties`, minor and patch each 0 to 9.
  - Each change merged to `develop` (R12.5) raises the version of the APK it changes by one in its last part,
    carrying at 9: 1.0.0, 1.0.1 … 1.0.9, 1.1.0 … 1.1.9, 1.2.0 … 1.9.9. Shrimpl's version rises with any
    change to Shrimpl's APK, including a new template (which Shrimpl bundles); the template's rises with
    any change to template code or resources (R11.4). A merge that changes neither APK, such as docs,
    tests or CI only, leaves both.
  - A new major version (2.0.0, 3.0.0 …) only on the owner's explicit instruction. A change that would
    go past x.9.9 waits for the owner's decision.
  - Each version is stored as a code, major × 100 + minor × 10 + patch (1.0.0 is 100, 1.2.3 is 123):
    the APK's versionCode, and what Shrimpl compares to offer an Update. Codes only rise, so Shrimpl
    always installs over itself. The codes are those of the earlier `major.minor` versions (Shrimpl
    1.1 is 1.0.1, the template's 1.0 is 1.0.0), and the whole-number template versions from
    before (1 to 19) are older than every version since, so their apps are offered an Update once.
  - Recovery (R11.6) reads a web app's template version from its versionName in any of its forms:
    `1.0.0.12` (template 1.0.0, build 12), `1.0.12` (a `major.minor` template) or `19.12`.
- R11.9 App options changes (docs/TEMPLATE_SPEC.md T5.8) reach the app's record, so its edit page
  shows them: Desktop site on sets the user agent to Desktop and off to Mobile, and Link and image
  menu sets that setting. A web app sends them to Shrimpl's exported App options provider, which
  saves a change only for the caller itself: a package of one of its records, signed with the key
  that record names. An edit page that is open takes the new settings at once; with edits of its own,
  it takes only those two. The change applies in the app already, so saving it needs no rebuild.

## R12 Repository, CI and releases

- R12.1 The repository builds exactly two APKs, the template and Shrimpl, from `:core`, `:template`
  and `:shrimpl`. Developer scripts: `scripts/setup_android_sdk.sh` (SDK and tool check),
  `scripts/check_native_code.sh` (R0), and `scripts/dev/` (placeholder icons, Public Suffix List
  refresh).
  *Outside code:* the repository layout and `scripts/`.
- R12.2 CI (`.github/workflows/android.yml`) runs by hand, never on a push: on a branch when it is
  about to merge into develop, and on develop before a release (R12.5); and on pull requests from
  forks. It runs the
  comment and document checks (new lines measured against the branch it comes from, R12.5), then a
  Build job (skipped for a commit whose files already passed Build, as develop's after a merge of a
  branch that passed, and for a change to documentation only, docs/ and Markdown) that tests
  `:core` with coverage, the template and Shrimpl, lints both apps' release variants to 0 errors, builds the template's release APK and Shrimpl's
  debug APK, and checks both for R0 and 16 KB page alignment (`scripts/check_page_alignment.sh`).
  Shrimpl's release APK is built by the Release workflow (R12.6), not here. Test and
  lint reports are uploaded only when a step fails; APKs are never uploaded.
  *Outside code:* `.github/workflows/android.yml`.
- R12.3 Shrimpl release builds use R8. The Release workflow (R12.6) signs them with the release key,
  passed in `SHRIMPL_RELEASE_KEYSTORE`, `SHRIMPL_RELEASE_STORE_PASSWORD`, `SHRIMPL_RELEASE_KEY_ALIAS`
  and `SHRIMPL_RELEASE_KEY_PASSWORD`; without them, as in local builds, the debug key signs them.
  Debug builds are "Shrimpl Debug", `com.valghora.shrimpl.debug`: the keys differ, so a debug build
  can never update a release or take one (R14), and it installs beside a release instead. Each keeps
  its own web app signing key (R5), so web apps one built show as signed with another key in the
  other.
  *Outside code:* `shrimpl/build.gradle.kts` (the signing configs and build types),
  `shrimpl/src/debug/res/values/strings.xml`.
- R12.4 The Emulator workflow runs the instrumented tests of `:template` and `:shrimpl` on an x86_64
  Android 37 emulator, by hand. It is required on `develop` before a merge into `main` (R12.5): main
  only receives a commit it passed on, or one that changes only documentation after such a commit. How
  it runs and what it covers: docs/TESTING.md.
  *Outside code:* `.github/workflows/emulator.yml`.
- R12.5 Branches. `develop` is where work lands; `main` holds releases and changes only when the owner
  says so.
  - New work branches from `develop` and merges back into it once the owner says so and the Android
    workflow (R12.2), run then, is green on the branch's last commit.
  - A merge of `develop` into `main` needs the Android workflow, run on `develop` by hand, and the
    Emulator workflow (R12.4) green on `develop`. Every merge into main publishes a release of a
    version not released before (R12.6).
  - A change to documentation only (docs and Markdown) needs neither workflow before either merge.
  *Outside code:* the workflow in AGENTS.md.
- R12.6 The Release workflow publishes a release on each push to `main` (a merge from develop), or when
  run by hand on main: it names the release after `shrimpl.appVersion`, publishes nothing when that
  version is already released (a merge of documentation only) and fails when its tag exists without a
  release. It builds Shrimpl's release APK with the release key from the repository secrets
  `RELEASE_KEYSTORE` (the keystore in base64), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
  `RELEASE_KEY_PASSWORD`, fails if the debug key signed it, checks it for R0 and 16 KB page
  alignment, and creates the GitHub release `v<version>`, and with it the tag, on the merged commit,
  with `shrimpl-<version>.apk` and `shrimpl-<version>.apk.sha256` attached (R14.1). Its notes, from
  `scripts/release_notes.sh`, list each change merged into develop since the previous release, from
  the merges' summaries without requirement IDs; they name and mention nobody and list no
  contributors. When no release is published yet, the notes read `Initial Release`. The key exists only in the job's temporary directory.
  *Outside code:* `.github/workflows/release.yml`, `scripts/release_notes.sh`.

## R13 Test tags (debug builds only)

Debug builds can put the app list and the search into states that are hard to set up for real.
Release builds never read tags or test addresses.

- R13.1 An app whose name starts with a comma-separated list of tags, such as "update,cert Example",
  "update, cert Example", "[update,cert] Example" or just "update,cert", shows the states they name.
  - Every part must be a known tag, so an ordinary first word is never read as one.
  - The tags override what Home works out for each app (installed, update available, signed with
    another key) in one place, before the usual rules, so the list shows only states real users can
    reach: update,cert shows Fix required, as a real mismatch would. Nothing is stored.

  | Tag | Effect |
  |---|---|
  | `update` | Update ready (R2.2); the app counts for Update all |
  | `cert` | signed with another key (R11.7): Fix required, the notice and Fix all, on an installed app; with `missing` it changes nothing |
  | `missing` | Not installed |

- R13.2 A build of an existing app whose name has tags, and keeps them, plays back: the sheet goes
  through every stage (and Uninstalling for Fix) at 0.5 s each without uninstalling, building,
  installing or saving, then ends as installed, or as its tags say. Creating an app, or saving one
  with its tags removed, builds for real.

  | Tag | Effect |
  |---|---|
  | `fail` | fails at installing |
  | `cancel` | the install is turned down (R6.5) |
  | `wait` | waits for confirmation until Cancel (R6.5) |
  | `unknown-sources` | asks for "Install unknown apps" (R6.3) |

- R13.3 Searching for a test address goes through the steps up to its failing one without going
  online, then fails as a real search would (R3.2): 404.test, 400.test, 500.test, offline.test,
  nohost.test, timeout.test, certificate.test and unloaded.test fail at Loading the site; 403.test
  fails at Researching permissions. The .test domain is reserved and never resolves.

## R14 Shrimpl updates

Shrimpl finds and installs its own new releases from GitHub, the repository's releases (R12).

- R14.1 A check asks GitHub's API for the latest release (`releases/latest`), as an anonymous client
  named "Shrimpl" that sends nothing about the user. A release is one tagged `v<version>` (R11.8) with
  `shrimpl-<version>.apk` and `shrimpl-<version>.apk.sha256` attached. Its version code is compared with
  the installed one: higher is available; otherwise Shrimpl is up to date. GitHub's "not found" (no
  release yet), another HTTP status, no connection, or a release without those files is a failed check,
  each with its reason. What a check found survives restarts.
- R14.2 Background checks run with WorkManager as often as Update check frequency says (every hour, 6
  hours, 12 hours, day or week; once a day by default), only on a network connection. Disabled stops
  them, and checks then run only by hand. Android may run one later than due to save battery.
  Changing the setting reschedules at once.
- R14.3 Settings' About section holds Shrimpl's updates:
  - The version row, titled "Shrimpl <version>" (the installed version, R11.8), shows the update state
    on one line in its tone, and one button:

    | State | Line | Tone | Button |
    |---|---|---|---|
    | before the first check | "Not checked yet" | neutral | Check |
    | checking | "Checking…" | neutral | spinner |
    | no newer release | "Up to date" | green | Check |
    | a newer release | "Update available" | blue | Update |
    | downloading, installing | "Downloading…", "Installing…" | blue | spinner |
    | the check failed | "Update check failed" | red | Retry (checks again) |
    | the update failed | "Update failed" | red | Retry (updates again) |

    The line, in the tone's text color, gives no reasons. Check works whatever the frequency says. The
    button keeps one size in every state, its spinner centred, so it doesn't move. Blue is the Update tone (`ui/common/Notice.kt`).
  - "Update check frequency", a choice: Disabled ("Never checks for updates automatically"), 1 hour
    ("Checks every hour"), 6 hours ("Checks every 6 hours"), 12 hours ("Checks every 12 hours"), 1 day
    ("Checks once a day", the default) or 1 week ("Checks once a week").
  - Choosing an interval, or Check, asks for the notification permission when Shrimpl lacks it.
- R14.4 Home shows an Info notice for a release found and not dismissed: "Shrimpl <version> is
  available. Update to get the latest fixes and features.", with Update and Dismiss (R3.3), above the
  apps or above the empty state. Dismiss hides it until a newer release.
- R14.5 The background check posts a notification once per release, on an "Updates" channel: "Shrimpl
  <version> is available", "Select to see what's new", with an Update action. Selecting it opens
  Settings; Update opens Settings and starts the update. Without the notification permission, Home's
  notice and the Settings row still show the release.
- R14.6 Update downloads the APK and its checksum file, installs the APK with the chosen install method
  (R6) only when its SHA-256 matches, and Android refuses an APK not signed with Shrimpl's key. Debug
  builds, signed with another key, refuse with "Debug builds can't install a release. Install the
  release APK from GitHub instead."

## Known limitations

- Shevery and Root installs, hinges, window snapping, TalkBack, and how Shrimpl looks and feels are
  verified only on the owner's devices (docs/TESTING.md).
- Google's developer verification for sideloaded apps may block or warn about installs on certified
  devices.
- The WebView limitations in docs/TEMPLATE_SPEC.md (sign-in blocks, no background push).
- Losing the signing key without a backup makes web apps impossible to update.
