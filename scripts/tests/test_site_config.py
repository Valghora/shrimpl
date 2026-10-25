"""Tests for scripts/site_config.py. Run: python3 -m unittest discover -s scripts/tests"""

import json
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SCRIPTS))

import site_config  # noqa: E402

DISCORD = {
    "APP_NAME": "Discord",
    "START_URL": "https://discord.com/app",
    "INTERNAL_DOMAINS": ["discord.com", "discordapp.com"],
    "ICON_SVG": "icon.svg",
    "ICON_BACKGROUND_HEX": "#5865F2",
    "HEADER_SURFACE_HEX": "#1E1F22",
}


def toml(values):
    def value(v):
        if isinstance(v, bool):
            return "true" if v else "false"
        if isinstance(v, (int, float)):
            return str(v)
        if isinstance(v, list):
            return "[" + ", ".join(value(x) for x in v) + "]"
        if isinstance(v, dict):
            return "{ " + ", ".join(f"{k} = {value(x)}" for k, x in v.items()) + " }"
        return '"' + str(v).replace("\\", "\\\\").replace('"', '\\"') + '"'
    return "\n".join(f"{k} = {value(v)}" for k, v in values.items()) + "\n"


class SiteConfigTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.original_sites = site_config.SITES
        site_config.SITES = self.tmp
        shutil.copy(SCRIPTS.parent / "sites" / "defaults.toml", self.tmp / "defaults.toml")

    def tearDown(self):
        site_config.SITES = self.original_sites
        shutil.rmtree(self.tmp)

    def make_site(self, site_id, values, icon=True):
        directory = self.tmp / site_id
        directory.mkdir()
        (directory / "site.toml").write_text(toml(values), encoding="utf-8")
        if icon:
            (directory / "icon.svg").write_text(
                '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M0 0h24v24H0z"/></svg>')
        return directory

    def errors(self, values, site_id="discord"):
        self.make_site(site_id, values)
        with self.assertRaises(site_config.ConfigError) as ctx:
            site_config.load(site_id)
        return str(ctx.exception)

    def test_valid_site_and_derived_values(self):
        self.make_site("discord", DISCORD)
        c = site_config.load("discord")
        self.assertEqual("discord", c["APP_ID"])
        self.assertEqual("com.valghora.pwa.discord", c["PACKAGE_NAME"])
        self.assertEqual("com.valghora.wrapper.core", c["CORE_PACKAGE"])
        self.assertEqual("#FFFFFF", c["ICON_FOREGROUND_HEX"])  # auto: white on blurple
        self.assertTrue(c["HEADER_IS_DARK"])
        self.assertFalse(c["USES_NIGHT_RESOURCES"])
        self.assertEqual("svg", c["ICON_MODE"])
        self.assertIsInstance(c["VERSION_CODE"], int)
        self.assertGreater(c["VERSION_CODE"], 1000000)
        self.assertEqual(["https://discord.com", "https://*.discord.com",
                          "https://discordapp.com", "https://*.discordapp.com"], c["ALLOWED_ORIGIN_RULES"])
        self.assertEqual("discord-release.apk", c["RELEASE_APK_NAME"])
        self.assertEqual("sites/discord/apks/1.0.0", c["APK_DIR"])

    def test_auto_foreground_is_black_on_light_background(self):
        self.make_site("discord", {**DISCORD, "ICON_BACKGROUND_HEX": "#FFEB3B"})
        self.assertEqual("#000000", site_config.load("discord")["ICON_FOREGROUND_HEX"])

    def test_unknown_key_suggests_correction(self):
        message = self.errors({**DISCORD, "APPNAME": "x"})
        self.assertIn("unknown key APPNAME", message)
        self.assertIn("did you mean APP_NAME?", message)

    def test_start_url_must_be_internal(self):
        self.assertIn("not covered by INTERNAL_DOMAINS", self.errors({**DISCORD, "START_URL": "https://example.com/"}))

    def test_start_url_must_be_https(self):
        self.assertIn("must be an https:// URL", self.errors({**DISCORD, "START_URL": "http://discord.com/"}))

    def test_domains_must_be_bare_hostnames(self):
        message = self.errors({**DISCORD, "INTERNAL_DOMAINS": ["https://discord.com", "Discord.com"]})
        self.assertIn("'https://discord.com' must be a bare lowercase hostname", message)
        self.assertIn("'Discord.com' must be a bare lowercase hostname", message)

    def test_exactly_one_icon_source(self):
        self.assertIn("set exactly one of", self.errors({**DISCORD, "ICON_LETTER": "D"}))

    def test_icon_file_must_exist_locally(self):
        values = {**DISCORD, "ICON_SVG": "https://example.com/icon.svg"}
        self.assertIn("must be a file in sites/discord/", self.errors(values))

    def test_directory_must_match_app_id(self):
        self.assertIn("must be named after APP_ID 'discord'", self.errors(DISCORD, site_id="chat"))

    def test_version_name_must_be_a_safe_folder_name(self):
        self.assertIn("VERSION_NAME must start with a letter or digit", self.errors({**DISCORD, "VERSION_NAME": "../1.0"}))

    def test_sdk_is_fixed_by_template(self):
        self.assertIn("MIN_SDK must be 37", self.errors({**DISCORD, "MIN_SDK": 26}))

    def test_shortcut_validation(self):
        values = {**DISCORD, "APP_SHORTCUTS": [{"label": "", "key": "k", "modifiers": ["HYPER"]}]}
        self.make_site("discord", values)
        with self.assertRaises(site_config.ConfigError) as ctx:
            site_config.load("discord")
        message = str(ctx.exception)
        self.assertIn("label must be a non-empty string", message)
        self.assertIn("key must match", message)
        self.assertIn("modifiers must be a list of CTRL", message)

    def test_unknown_site_lists_known_sites(self):
        self.make_site("discord", DISCORD)
        with self.assertRaises(site_config.ConfigError) as ctx:
            site_config.load("nope")
        self.assertIn("known sites: discord", str(ctx.exception))

    def test_explicit_app_id_and_version(self):
        self.make_site("chat", {**DISCORD, "APP_ID": "chat", "VERSION_CODE": 7, "RELEASE_SIGNING": "unsigned"})
        c = site_config.load("chat")
        self.assertEqual(("chat", 7), (c["APP_ID"], c["VERSION_CODE"]))
        self.assertEqual("chat-release-unsigned.apk", c["RELEASE_APK_NAME"])

    def test_letter_auto_uses_first_alphanumeric(self):
        values = {k: v for k, v in DISCORD.items() if k != "ICON_SVG"}
        self.make_site("3dprint", {**values, "APP_NAME": "3D Print", "APP_ID": "3dprint", "ICON_LETTER": "auto"}, icon=False)
        with self.assertRaises(site_config.ConfigError):  # ids may not start with a digit
            site_config.load("3dprint")
        self.make_site("app3dprint", {**values, "APP_NAME": "3D Print", "ICON_LETTER": "auto"}, icon=False)
        c = site_config.load("app3dprint")
        self.assertEqual(("letter", "3"), (c["ICON_MODE"], c["ICON_LETTER"]))

    def test_manifest_overlay_removes_disabled_capabilities(self):
        # The template declares the superset; the overlay removes what the site disables.
        self.make_site("discord", {**DISCORD, "ALLOW_MICROPHONE": True, "ENABLE_MULTI_WINDOW": False})
        manifest = site_config.render_manifest(site_config.load("discord"))
        self.assertNotIn("RECORD_AUDIO", manifest)
        self.assertNotIn("android.hardware.microphone", manifest)
        self.assertIn('android:name="android.permission.CAMERA"\n        tools:node="remove"', manifest)
        self.assertIn('android:name="android.hardware.camera.autofocus"\n        tools:node="remove"', manifest)
        self.assertNotIn("POST_NOTIFICATIONS", manifest)
        self.assertIn('android:launchMode="singleTask"', manifest)
        self.assertIn('android:value="false"', manifest)
        self.assertIn('android:defaultWidth="1280dp"', manifest)

    def test_manifest_overlay_removes_nothing_when_everything_is_enabled(self):
        self.make_site("discord", {**DISCORD, "ALLOW_MICROPHONE": True, "ALLOW_CAMERA": True})
        manifest = site_config.render_manifest(site_config.load("discord"))
        self.assertNotIn('tools:node="remove"', manifest)
        self.assertIn('android:launchMode="singleInstancePerTask"', manifest)
        self.assertIn('android:value="true"', manifest)

    def test_profile_json(self):
        values = {**DISCORD, "APP_NAME": 'Discord "beta"', "APP_ID": "discord", "HEADER_SURFACE_HEX_NIGHT": "#000000",
                  "NEW_WINDOW_SHORTCUT": {"key": "KEYCODE_N", "modifiers": ["CTRL", "SHIFT"]},
                  "APP_SHORTCUTS": [{"label": "Search", "key": "KEYCODE_K", "modifiers": ["CTRL"]}]}
        self.make_site("discord", values)
        profile = json.loads(site_config.render_profile(site_config.load("discord")))
        self.assertEqual('Discord "beta"', profile["name"])
        self.assertEqual("discord", profile["appId"])
        self.assertEqual("#FFFFFF", profile["iconForeground"])  # "auto" resolved
        self.assertEqual("#000000", profile["headerColorNight"])
        self.assertEqual({"width": 1280, "height": 800}, profile["defaultWindowDp"])
        self.assertEqual({"key": "KEYCODE_N", "modifiers": ["CTRL", "SHIFT"]}, profile["newWindowShortcut"])
        self.assertEqual([{"label": "Search", "key": "KEYCODE_K", "modifiers": ["CTRL"]}], profile["appShortcuts"])
        self.assertEqual("auto", profile["userAgentMode"])

    def test_android_string_escaping(self):
        self.assertEqual("Bob\\'s &amp; \\\"Co\\\"", site_config.android_string('Bob\'s & "Co"'))
        self.assertEqual("\\@home", site_config.android_string("@home"))

    def test_night_resources_only_when_colors_differ(self):
        self.make_site("discord", {**DISCORD, "HEADER_SURFACE_HEX": "#FFFFFF", "HEADER_SURFACE_HEX_NIGHT": "#121212"})
        c = site_config.load("discord")
        self.assertTrue(c["USES_NIGHT_RESOURCES"])
        self.assertFalse(c["HEADER_IS_DARK"])
        self.assertTrue(c["HEADER_NIGHT_IS_DARK"])
        self.assertIn("#121212", site_config.render_values(c, night=True))

    def test_generate_writes_all_outputs(self):
        self.make_site("discord", DISCORD)
        out = self.tmp / "out"
        site_config.generate(site_config.load("discord"), out / "assets", out / "res", out / "manifest" / "AndroidManifest.xml")
        self.assertTrue((out / "assets/uniwrap/profile.json").is_file())
        self.assertTrue((out / "res/values/site.xml").is_file())
        self.assertTrue((out / "res/mipmap-anydpi/ic_launcher.xml").is_file())
        self.assertTrue((out / "manifest/AndroidManifest.xml").is_file())


    def test_prune_apks_keeps_last_three_versions_built(self):
        self.make_site("discord", {**DISCORD, "VERSION_NAME": "1.0.0"})
        apks = self.tmp / "discord" / "apks"
        # Built in this order; 1.0.0 (the current version) was rebuilt first, long ago.
        for age, version in ((50, "1.0.0"), (40, "0.9.0"), (30, "2.0.0"), (20, "1.5.0"), (10, "1.6.0")):
            (apks / version).mkdir(parents=True)
            apk = apks / version / "discord-debug.apk"
            apk.write_bytes(b"apk")
            os.utime(apk, (1_000_000 - age, 1_000_000 - age))
        self.assertEqual(["2.0.0", "0.9.0"], site_config.prune_apks("discord"))
        self.assertEqual(["1.0.0", "1.5.0", "1.6.0"], sorted(p.name for p in apks.iterdir()))
        self.assertEqual([], site_config.prune_apks("discord"))

    def test_prune_apks_after_fresh_clone_uses_version_order(self):
        self.make_site("discord", {**DISCORD, "VERSION_NAME": "1.10.0"})
        apks = self.tmp / "discord" / "apks"
        for version in ("1.2.0", "1.9.0", "1.10.0", "1.8.0"):
            (apks / version).mkdir(parents=True)
            apk = apks / version / "discord-release.apk"
            apk.write_bytes(b"apk")
            os.utime(apk, (1_000_000, 1_000_000))
        self.assertEqual(["1.2.0"], site_config.prune_apks("discord"))

    def test_prune_apks_without_builds(self):
        self.make_site("discord", DISCORD)
        self.assertEqual([], site_config.prune_apks("discord"))


class RepositorySitesTest(unittest.TestCase):
    def test_every_committed_site_is_valid(self):
        for site_id in site_config.site_ids():
            with self.subTest(site=site_id):
                site_config.load(site_id)


if __name__ == "__main__":
    unittest.main()
