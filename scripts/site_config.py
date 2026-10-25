#!/usr/bin/env python3
"""Site configuration for the Universal Android Wrapper template.

A site is sites/<id>/site.toml layered over sites/defaults.toml (and the gitignored
sites/defaults.local.toml). This script validates the merged config, computes the derived
values, and generates everything site-specific the Android build needs:

    site_config.py list [--json]                 site ids
    site_config.py validate (--site ID | --all)  check config files
    site_config.py show --site ID                resolved config as JSON
    site_config.py properties --site ID          key=value lines read by template/build.gradle.kts
    site_config.py generate --site ID --assets-out DIR --res-out DIR --manifest-out FILE
    site_config.py prune-apks --site ID          keep the APKs of the last 3 versions built

Standard library only (Python 3.11+ for tomllib).
"""

import argparse
import datetime
import json
import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SITES = ROOT / "sites"
sys.path.insert(0, str(Path(__file__).resolve().parent))

import generate_icons  # noqa: E402  (sibling module)

TEMPLATE_NAMESPACE = "com.valghora.wrapper"
TEMPLATE_SDK = 37
VERSION_EPOCH = datetime.datetime(2024, 1, 1, tzinfo=datetime.timezone.utc)

HEX = re.compile(r"^#[0-9A-Fa-f]{6}$")
HOST = re.compile(r"^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
KEYCODE = re.compile(r"^KEYCODE_[A-Z0-9_]+$")
APP_ID_RE = re.compile(r"^[a-z][a-z0-9]*$")
PACKAGE_RE = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$")
MODIFIERS = ("CTRL", "SHIFT", "ALT", "META")
ICON_KEYS = ("ICON_SVG", "ICON_RASTER", "ICON_LETTER")


class ConfigError(Exception):
    pass


# ---------- Schema ----------

def _str(v):
    return isinstance(v, str)


def _bool(v):
    return isinstance(v, bool)


def _int(v):
    return isinstance(v, int) and not isinstance(v, bool)


def _str_list(v):
    return isinstance(v, list) and all(isinstance(x, str) for x in v)


def _dims(v):
    return isinstance(v, list) and len(v) == 2 and all(_int(x) and x > 0 for x in v)


def _shortcut(v):
    return isinstance(v, dict)


def _shortcuts(v):
    return isinstance(v, list) and all(isinstance(x, dict) for x in v)


# key -> (type check, description used in errors)
SCHEMA = {
    "APP_NAME": (_str, "string"),
    "APP_ID": (_str, "string"),
    "PACKAGE_PREFIX": (_str, "string"),
    "VERSION_CODE": (lambda v: _int(v) or v == "auto", 'integer or "auto"'),
    "VERSION_NAME": (_str, "string"),
    "MIN_SDK": (_int, "integer"),
    "TARGET_SDK": (_int, "integer"),
    "COMPILE_SDK": (_int, "integer"),
    "START_URL": (_str, "string"),
    "INTERNAL_DOMAINS": (_str_list, "list of strings"),
    "OAUTH_DOMAINS": (_str_list, "list of strings"),
    "USER_AGENT_MODE": (lambda v: v in ("auto", "desktop", "mobile"), '"auto", "desktop" or "mobile"'),
    "USER_AGENT_OVERRIDE": (_str, "string"),
    "UA_STORE_REDIRECT_FALLBACK": (_bool, "boolean"),
    "UA_USER_TOGGLE": (_bool, "boolean"),
    "ICON_SVG": (_str, "file path"),
    "ICON_RASTER": (_str, "file path"),
    "ICON_RASTER_MASKABLE": (_bool, "boolean"),
    "ICON_LETTER": (_str, "single letter/digit or \"auto\""),
    "ICON_BACKGROUND_HEX": (_str, "#RRGGBB"),
    "ICON_FOREGROUND_HEX": (_str, '#RRGGBB or "auto"'),
    "HEADER_SURFACE_HEX": (_str, "#RRGGBB"),
    "HEADER_SURFACE_HEX_NIGHT": (_str, '#RRGGBB or ""'),
    "ALLOW_MICROPHONE": (_bool, "boolean"),
    "ALLOW_CAMERA": (_bool, "boolean"),
    "ALLOW_NOTIFICATIONS": (_bool, "boolean"),
    "ALLOW_PROTECTED_MEDIA": (_bool, "boolean"),
    "ENABLE_MULTI_WINDOW": (_bool, "boolean"),
    "KEEP_ALIVE_WHEN_HIDDEN": (_bool, "boolean"),
    "PAUSE_ON_POWER_SAVE": (_bool, "boolean"),
    "NATIVE_CONTEXT_MENU": (_bool, "boolean"),
    "RELEASE_SIGNING": (lambda v: v in ("auto", "unsigned"), '"auto" or "unsigned"'),
    "DEFAULT_WINDOW_DP": (_dims, "[width, height] in dp"),
    "MIN_WINDOW_DP": (_dims, "[width, height] in dp"),
    "APP_SHORTCUTS": (_shortcuts, "list of {label, key, modifiers} tables"),
    "NEW_WINDOW_SHORTCUT": (_shortcut, "{key, modifiers} table"),
}
REQUIRED = (
    "APP_NAME", "PACKAGE_PREFIX", "VERSION_CODE", "VERSION_NAME", "MIN_SDK", "TARGET_SDK", "COMPILE_SDK",
    "START_URL", "INTERNAL_DOMAINS", "OAUTH_DOMAINS", "USER_AGENT_MODE", "USER_AGENT_OVERRIDE",
    "UA_STORE_REDIRECT_FALLBACK", "UA_USER_TOGGLE", "ICON_BACKGROUND_HEX", "ICON_FOREGROUND_HEX",
    "ICON_RASTER_MASKABLE", "HEADER_SURFACE_HEX", "HEADER_SURFACE_HEX_NIGHT", "ALLOW_MICROPHONE",
    "ALLOW_CAMERA", "ALLOW_NOTIFICATIONS", "ALLOW_PROTECTED_MEDIA", "ENABLE_MULTI_WINDOW",
    "KEEP_ALIVE_WHEN_HIDDEN", "PAUSE_ON_POWER_SAVE", "NATIVE_CONTEXT_MENU", "RELEASE_SIGNING",
    "DEFAULT_WINDOW_DP", "MIN_WINDOW_DP", "APP_SHORTCUTS",
)


# ---------- Loading ----------

def _rel(path):
    try:
        return path.relative_to(ROOT)
    except ValueError:
        return path


def site_ids():
    return sorted(p.parent.name for p in SITES.glob("*/site.toml"))


def site_dir(site_id):
    return SITES / site_id


def _load_toml(path):
    try:
        with open(path, "rb") as fh:
            return tomllib.load(fh)
    except tomllib.TOMLDecodeError as exc:
        raise ConfigError(f"{_rel(path)}: {exc}") from exc


def load(site_id):
    """Returns the merged, validated config with derived values (keys as in the spec)."""
    path = site_dir(site_id) / "site.toml"
    if not path.is_file():
        known = ", ".join(site_ids()) or "none"
        raise ConfigError(f"unknown site '{site_id}' (no {_rel(path)}); known sites: {known}")
    merged = {}
    for layer in (SITES / "defaults.toml", SITES / "defaults.local.toml", path):
        if layer.is_file():
            merged.update(_load_toml(layer))
    errors = validate(merged, site_id)
    if errors:
        raise ConfigError(f"sites/{site_id}/site.toml is invalid:\n  - " + "\n  - ".join(errors))
    return derive(merged, site_id)


def contrast_ratio(a, b):
    def luminance(hex_color):
        def channel(c):
            c = c / 255
            return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
        r, g, b_ = (int(hex_color[i:i + 2], 16) for i in (1, 3, 5))
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b_)
    la, lb = luminance(a), luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def is_dark(hex_color):
    """WCAG rule from the spec: dark when contrast with white >= contrast with black."""
    return contrast_ratio(hex_color, "#FFFFFF") >= contrast_ratio(hex_color, "#000000")


def derive_app_id(app_name):
    app_id = re.sub(r"[^a-z0-9]", "", app_name.lower())
    if not app_id or app_id[0].isdigit():
        app_id = "app" + app_id
    return app_id


# ---------- Validation ----------

def _host_of(url):
    m = re.match(r"^https://([^/:?#@]+)(?::\d+)?(?:[/?#]|$)", url)
    return m.group(1).lower().rstrip(".") if m else None


def _matches(host, domains):
    return any(host == d or host.endswith("." + d) for d in domains)


def validate(cfg, site_id):
    errors = []
    for key in sorted(set(cfg) - set(SCHEMA)):
        hint = [k for k in SCHEMA if k.replace("_", "") == key.upper().replace("_", "")]
        errors.append(f"unknown key {key}" + (f" (did you mean {hint[0]}?)" if hint else ""))
    for key in REQUIRED:
        if key not in cfg:
            errors.append(f"missing required key {key}")
    for key, value in cfg.items():
        check = SCHEMA.get(key)
        if check and not check[0](value):
            errors.append(f"{key} must be {check[1]}, got {value!r}")
    if errors:
        return errors

    app_id = cfg.get("APP_ID") or derive_app_id(cfg["APP_NAME"])
    if not cfg["APP_NAME"].strip():
        errors.append("APP_NAME must not be empty")
    if not APP_ID_RE.match(app_id):
        errors.append(f"APP_ID {app_id!r} must match {APP_ID_RE.pattern}")
    if app_id != site_id:
        errors.append(f"site directory 'sites/{site_id}' must be named after APP_ID '{app_id}'")
    if not PACKAGE_RE.match(cfg["PACKAGE_PREFIX"] + "." + app_id):
        errors.append(f"PACKAGE_PREFIX {cfg['PACKAGE_PREFIX']!r} does not form a valid package name")
    if _str(cfg["VERSION_NAME"]) and not re.fullmatch(r"[0-9A-Za-z][0-9A-Za-z._+-]*", cfg["VERSION_NAME"]):
        errors.append("VERSION_NAME must start with a letter or digit and contain only letters, digits and . _ + - "
                      "(it names the APK folder)")
    if _int(cfg["VERSION_CODE"]) and not 1 <= cfg["VERSION_CODE"] <= 2100000000:
        errors.append("VERSION_CODE must be between 1 and 2100000000")
    for key in ("MIN_SDK", "TARGET_SDK", "COMPILE_SDK"):
        if cfg[key] != TEMPLATE_SDK:
            errors.append(f"{key} must be {TEMPLATE_SDK}: the template code targets API {TEMPLATE_SDK} only")

    for key in ("INTERNAL_DOMAINS", "OAUTH_DOMAINS"):
        if key == "INTERNAL_DOMAINS" and not cfg[key]:
            errors.append("INTERNAL_DOMAINS must list at least one domain")
        for domain in cfg[key]:
            if not HOST.match(domain):
                errors.append(f"{key}: {domain!r} must be a bare lowercase hostname (no scheme, port or path)")
    host = _host_of(cfg["START_URL"])
    if host is None:
        errors.append(f"START_URL must be an https:// URL, got {cfg['START_URL']!r}")
    elif not _matches(host, cfg["INTERNAL_DOMAINS"]):
        errors.append(f"START_URL host {host!r} is not covered by INTERNAL_DOMAINS")

    icons = [k for k in ICON_KEYS if k in cfg]
    if len(icons) != 1:
        errors.append(f"set exactly one of {', '.join(ICON_KEYS)} (found {len(icons) or 'none'})")
    for key in ("ICON_SVG", "ICON_RASTER"):
        if key in cfg:
            path = site_dir(site_id) / cfg[key]
            if "://" in cfg[key]:
                errors.append(f"{key} must be a file in sites/{site_id}/ (run discovery to download URLs)")
            elif not path.is_file():
                errors.append(f"{key}: sites/{site_id}/{cfg[key]} does not exist")
    if "ICON_LETTER" in cfg and not re.fullmatch(r"[A-Za-z0-9]|auto", cfg["ICON_LETTER"]):
        errors.append('ICON_LETTER must be one letter or digit, or "auto"')
    if "ICON_RASTER" not in cfg and cfg["ICON_RASTER_MASKABLE"]:
        errors.append("ICON_RASTER_MASKABLE only applies with ICON_RASTER")

    for key in ("ICON_BACKGROUND_HEX", "HEADER_SURFACE_HEX"):
        if not HEX.match(cfg[key]):
            errors.append(f"{key} must be #RRGGBB, got {cfg[key]!r}")
    if cfg["ICON_FOREGROUND_HEX"] != "auto" and not HEX.match(cfg["ICON_FOREGROUND_HEX"]):
        errors.append(f'ICON_FOREGROUND_HEX must be #RRGGBB or "auto", got {cfg["ICON_FOREGROUND_HEX"]!r}')
    if cfg["HEADER_SURFACE_HEX_NIGHT"] and not HEX.match(cfg["HEADER_SURFACE_HEX_NIGHT"]):
        errors.append("HEADER_SURFACE_HEX_NIGHT must be #RRGGBB or empty")

    if cfg["MIN_WINDOW_DP"][0] > cfg["DEFAULT_WINDOW_DP"][0] or cfg["MIN_WINDOW_DP"][1] > cfg["DEFAULT_WINDOW_DP"][1]:
        errors.append("MIN_WINDOW_DP must not exceed DEFAULT_WINDOW_DP")

    shortcuts = list(cfg["APP_SHORTCUTS"])
    if "NEW_WINDOW_SHORTCUT" in cfg:
        shortcuts.append({"label": "New Window", **cfg["NEW_WINDOW_SHORTCUT"]})
    for i, sc in enumerate(shortcuts):
        where = f"APP_SHORTCUTS[{i}]" if i < len(cfg["APP_SHORTCUTS"]) else "NEW_WINDOW_SHORTCUT"
        extra = set(sc) - {"label", "key", "modifiers"}
        if extra:
            errors.append(f"{where}: unknown fields {sorted(extra)}")
        if not isinstance(sc.get("label"), str) or not sc["label"].strip():
            errors.append(f"{where}: label must be a non-empty string")
        if not isinstance(sc.get("key"), str) or not KEYCODE.match(sc["key"]):
            errors.append(f"{where}: key must match {KEYCODE.pattern}")
        mods = sc.get("modifiers", [])
        if not _str_list(mods) or any(m not in MODIFIERS for m in mods):
            errors.append(f"{where}: modifiers must be a list of {', '.join(MODIFIERS)}")
    return errors


# ---------- Derived values ----------

def derive(cfg, site_id):
    out = dict(cfg)
    out["APP_ID"] = cfg.get("APP_ID") or derive_app_id(cfg["APP_NAME"])
    out["PACKAGE_NAME"] = f"{cfg['PACKAGE_PREFIX']}.{out['APP_ID']}"
    out["CORE_PACKAGE"] = f"{TEMPLATE_NAMESPACE}.core"
    out["NOTIFICATION_CHANNEL_ID"] = f"{out['APP_ID']}_notifications"
    out["HEADER_NIGHT"] = cfg["HEADER_SURFACE_HEX_NIGHT"] or cfg["HEADER_SURFACE_HEX"]
    out["USES_NIGHT_RESOURCES"] = out["HEADER_NIGHT"].upper() != cfg["HEADER_SURFACE_HEX"].upper()
    out["HEADER_IS_DARK"] = is_dark(cfg["HEADER_SURFACE_HEX"])
    out["HEADER_NIGHT_IS_DARK"] = is_dark(out["HEADER_NIGHT"])
    out["ALLOWED_ORIGIN_RULES"] = [r for d in cfg["INTERNAL_DOMAINS"] for r in (f"https://{d}", f"https://*.{d}")]
    out["UA_FEATURES_ACTIVE"] = cfg["USER_AGENT_OVERRIDE"] == ""
    if cfg["ICON_FOREGROUND_HEX"] == "auto":
        bg = cfg["ICON_BACKGROUND_HEX"]
        out["ICON_FOREGROUND_HEX"] = "#FFFFFF" if contrast_ratio(bg, "#FFFFFF") >= contrast_ratio(bg, "#000000") else "#000000"
    out["ICON_MODE"] = next(k for k in ICON_KEYS if k in cfg).removeprefix("ICON_").lower()
    if cfg.get("ICON_LETTER") == "auto":
        out["ICON_LETTER"] = next((c for c in cfg["APP_NAME"].upper() if c.isascii() and c.isalnum()), "A")
    if cfg["VERSION_CODE"] == "auto":
        now = datetime.datetime.now(datetime.timezone.utc)
        out["VERSION_CODE"] = int((now - VERSION_EPOCH).total_seconds() // 60)
    signed = cfg["RELEASE_SIGNING"] != "unsigned"
    out["DEBUG_APK_NAME"] = f"{out['APP_ID']}-debug.apk"
    out["RELEASE_APK_NAME"] = f"{out['APP_ID']}-release{'' if signed else '-unsigned'}.apk"
    out["RELEASE_APK_PATH"] = f"template/build/outputs/apk/release/template-release{'' if signed else '-unsigned'}.apk"
    out["SITE_DIR"] = str(site_dir(site_id))
    out["APK_DIR"] = f"sites/{site_id}/apks/{cfg['VERSION_NAME']}"
    return out


# ---------- Generation ----------

def android_string(value):
    """Escapes a value for a <string> resource."""
    value = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    value = value.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    if value[:1] in ("@", "?"):
        value = "\\" + value
    return value


def render_values(c, night=False):
    color = c["HEADER_NIGHT"] if night else c["HEADER_SURFACE_HEX"]
    name = "" if night else f'\n    <string name="app_name" translatable="false">{android_string(c["APP_NAME"])}</string>'
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f'<!-- Generated by scripts/site_config.py from sites/{c["APP_ID"]}/site.toml. Do not edit. -->\n'
        f"<resources>{name}\n"
        f'    <color name="header_surface">{color.upper()}</color>\n'
        "</resources>\n"
    )


def render_manifest(c):
    """Overlay on the template's superset manifest: removes what the site disables, sets window values."""
    removed = []
    if not c["ALLOW_MICROPHONE"]:
        removed += [("uses-feature", "android.hardware.microphone"),
                    ("uses-permission", "android.permission.RECORD_AUDIO"),
                    ("uses-permission", "android.permission.MODIFY_AUDIO_SETTINGS")]
    if not c["ALLOW_CAMERA"]:
        removed += [("uses-feature", "android.hardware.camera"),
                    ("uses-feature", "android.hardware.camera.autofocus"),
                    ("uses-permission", "android.permission.CAMERA")]
    # POST_NOTIFICATIONS stays declared even when ALLOW_NOTIFICATIONS is false: the notification code
    # is compiled in and lint requires the declaration; it is only requested when notifications are on.
    launch_mode = "singleInstancePerTask" if c["ENABLE_MULTI_WINDOW"] else "singleTask"
    multi_instance = "true" if c["ENABLE_MULTI_WINDOW"] else "false"
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f'<!-- Generated by scripts/site_config.py from sites/{c["APP_ID"]}/site.toml. Do not edit. -->',
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android"',
        '    xmlns:tools="http://schemas.android.com/tools">',
        "",
    ]
    lines += [f'    <{tag}\n        android:name="{name}"\n        tools:node="remove" />' for tag, name in removed]
    if removed:
        lines.append("")
    lines += [
        "    <application>",
        "        <activity",
        '            android:name=".MainActivity"',
        f'            android:launchMode="{launch_mode}"',
        '            tools:replace="android:launchMode">',
        "            <layout",
        f'                android:defaultWidth="{c["DEFAULT_WINDOW_DP"][0]}dp"',
        f'                android:defaultHeight="{c["DEFAULT_WINDOW_DP"][1]}dp"',
        '                android:gravity="center"',
        f'                android:minWidth="{c["MIN_WINDOW_DP"][0]}dp"',
        f'                android:minHeight="{c["MIN_WINDOW_DP"][1]}dp" />',
        "            <property",
        '                android:name="android.window.PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI"',
        f'                android:value="{multi_instance}"',
        '                tools:replace="android:value" />',
        "        </activity>",
        "    </application>",
        "",
        "</manifest>",
        "",
    ]
    return "\n".join(lines)


def render_profile(c):
    """The profile JSON the template reads at launch (core/.../profile/Profile.kt)."""
    profile = {
        "schemaVersion": 1,
        "name": c["APP_NAME"],
        "appId": c["APP_ID"],
        "startUrl": c["START_URL"],
        "internalDomains": c["INTERNAL_DOMAINS"],
        "oauthDomains": c["OAUTH_DOMAINS"],
        "userAgentMode": c["USER_AGENT_MODE"],
        "userAgentOverride": c["USER_AGENT_OVERRIDE"],
        "uaStoreRedirectFallback": c["UA_STORE_REDIRECT_FALLBACK"],
        "uaUserToggle": c["UA_USER_TOGGLE"],
        "iconBackground": c["ICON_BACKGROUND_HEX"],
        "iconForeground": c["ICON_FOREGROUND_HEX"],
        "headerColor": c["HEADER_SURFACE_HEX"],
        "allowMicrophone": c["ALLOW_MICROPHONE"],
        "allowCamera": c["ALLOW_CAMERA"],
        "allowNotifications": c["ALLOW_NOTIFICATIONS"],
        "allowProtectedMedia": c["ALLOW_PROTECTED_MEDIA"],
        "enableMultiWindow": c["ENABLE_MULTI_WINDOW"],
        "keepAliveWhenHidden": c["KEEP_ALIVE_WHEN_HIDDEN"],
        "pauseOnPowerSave": c["PAUSE_ON_POWER_SAVE"],
        "nativeContextMenu": c["NATIVE_CONTEXT_MENU"],
        "defaultWindowDp": {"width": c["DEFAULT_WINDOW_DP"][0], "height": c["DEFAULT_WINDOW_DP"][1]},
        "minWindowDp": {"width": c["MIN_WINDOW_DP"][0], "height": c["MIN_WINDOW_DP"][1]},
        "appShortcuts": [{"label": sc["label"], "key": sc["key"], "modifiers": sc.get("modifiers", [])}
                         for sc in c["APP_SHORTCUTS"]],
    }
    if c["HEADER_SURFACE_HEX_NIGHT"]:
        profile["headerColorNight"] = c["HEADER_SURFACE_HEX_NIGHT"]
    if "NEW_WINDOW_SHORTCUT" in c:
        profile["newWindowShortcut"] = {"key": c["NEW_WINDOW_SHORTCUT"]["key"],
                                        "modifiers": c["NEW_WINDOW_SHORTCUT"].get("modifiers", [])}
    return json.dumps(profile, indent=2, ensure_ascii=False) + "\n"


def generate(c, assets_out, res_out, manifest_out):
    import shutil

    for directory in (assets_out, res_out):
        if directory.exists():
            shutil.rmtree(directory)
        directory.mkdir(parents=True)
    (assets_out / "uniwrap").mkdir()
    (assets_out / "uniwrap" / "profile.json").write_text(render_profile(c), encoding="utf-8")
    (res_out / "values").mkdir()
    (res_out / "values" / "site.xml").write_text(render_values(c), encoding="utf-8")
    if c["USES_NIGHT_RESOURCES"]:
        (res_out / "values-night").mkdir()
        (res_out / "values-night" / "site.xml").write_text(render_values(c, night=True), encoding="utf-8")
    manifest_out.parent.mkdir(parents=True, exist_ok=True)
    manifest_out.write_text(render_manifest(c), encoding="utf-8")
    generate_icons.generate(c, Path(c["SITE_DIR"]), res_out)


# ---------- Built APKs ----------

APK_VERSIONS_KEPT = 3


def prune_apks(site_id, keep=APK_VERSIONS_KEPT):
    """Deletes sites/<id>/apks/<version>/ folders beyond the `keep` most recently built ones.

    Recency is the newest file in each folder, then the version number (a fresh clone gives every
    file the same time). The current VERSION_NAME is always kept. Returns the removed version names.
    """
    import shutil

    root = site_dir(site_id) / "apks"
    if not root.is_dir():
        return []
    current = load(site_id)["VERSION_NAME"]

    def built_at(folder):
        return max((f.stat().st_mtime for f in folder.iterdir()), default=folder.stat().st_mtime)

    def version_key(name):
        return [(0, int(part), "") if part.isdigit() else (1, 0, part) for part in re.split(r"(\d+)", name) if part]

    folders = sorted((d for d in root.iterdir() if d.is_dir()),
                     key=lambda d: (d.name == current, built_at(d), version_key(d.name)), reverse=True)
    removed = []
    for folder in folders[keep:]:
        shutil.rmtree(folder)
        removed.append(folder.name)
    return removed


# ---------- CLI ----------

def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    p_list = sub.add_parser("list")
    p_list.add_argument("--json", action="store_true")
    p_validate = sub.add_parser("validate")
    group = p_validate.add_mutually_exclusive_group(required=True)
    group.add_argument("--site")
    group.add_argument("--all", action="store_true")
    for name in ("show", "properties", "prune-apks"):
        sub.add_parser(name).add_argument("--site", required=True)
    p_gen = sub.add_parser("generate")
    p_gen.add_argument("--site", required=True)
    p_gen.add_argument("--assets-out", required=True, type=Path)
    p_gen.add_argument("--res-out", required=True, type=Path)
    p_gen.add_argument("--manifest-out", required=True, type=Path)
    args = parser.parse_args()

    try:
        if args.command == "list":
            ids = site_ids()
            print(json.dumps(ids) if args.json else "\n".join(ids))
        elif args.command == "validate":
            failed = False
            for site_id in site_ids() if args.all else [args.site]:
                try:
                    load(site_id)
                    print(f"ok      {site_id}")
                except (ConfigError, generate_icons.IconError) as exc:
                    failed = True
                    print(f"invalid {site_id}: {exc}", file=sys.stderr)
            return 1 if failed else 0
        elif args.command == "show":
            print(json.dumps(load(args.site), indent=2))
        elif args.command == "properties":
            c = load(args.site)
            props = {
                "siteId": args.site,
                "appId": c["APP_ID"],
                "applicationId": c["PACKAGE_NAME"],
                "namespace": TEMPLATE_NAMESPACE,
                "versionCode": c["VERSION_CODE"],
                "versionName": c["VERSION_NAME"],
                "minSdk": c["MIN_SDK"],
                "targetSdk": c["TARGET_SDK"],
                "compileSdk": c["COMPILE_SDK"],
                "releaseSigning": c["RELEASE_SIGNING"],
                "debugApkName": c["DEBUG_APK_NAME"],
                "releaseApkName": c["RELEASE_APK_NAME"],
                "releaseApkPath": c["RELEASE_APK_PATH"],
                "apkDir": c["APK_DIR"],
            }
            print("\n".join(f"{k}={v}" for k, v in props.items()))
        elif args.command == "generate":
            generate(load(args.site), args.assets_out, args.res_out, args.manifest_out)
        elif args.command == "prune-apks":
            for version in prune_apks(args.site):
                print(f"removed old APKs: sites/{args.site}/apks/{version}/")
        return 0
    except (ConfigError, generate_icons.IconError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
