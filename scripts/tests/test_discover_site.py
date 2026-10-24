"""Tests for scripts/discover_site.py (offline). Run: python3 -m unittest discover -s scripts/tests"""

import sys
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import discover_site as ds  # noqa: E402


def psl(rules=("com", "app", "co.uk", "net", "gg"), wildcards=("ck",), exceptions=("www.ck",)):
    instance = ds.PublicSuffixList.__new__(ds.PublicSuffixList)
    instance.rules, instance.wildcards, instance.exceptions = set(rules), set(wildcards), set(exceptions)
    return instance


def evidence(**desktop):
    page = {"title": "Example Mail | Inbox", "metas": [], "links": [], "topColor": "#FFFFFF",
            "themeColors": [], "bodyColor": "#FFFFFF"}
    page.update(desktop.pop("page", {}))
    base = {"finalUrl": "https://mail.example.com/inbox", "redirectChain": ["https://mail.example.com/inbox"],
            "requests": [], "signals": {}, "headers": {}, "page": page}
    base.update(desktop)
    return {"desktop": base, "dark": {"topColor": "#FFFFFF"}, "webview": {"status": 200, "title": "", "text": "",
                                                                          "navigations": []}}


class HelpersTest(unittest.TestCase):
    def test_registrable_domain(self):
        p = psl()
        self.assertEqual("discordapp.com", p.registrable("cdn.discordapp.com"))
        self.assertEqual("bbc.co.uk", p.registrable("www.bbc.co.uk"))
        self.assertEqual("discord.gg", p.registrable("discord.gg"))
        self.assertEqual("foo.bar.ck", p.registrable("a.foo.bar.ck"))
        self.assertEqual("www.ck", p.registrable("www.ck"))

    def test_title_to_slug_matches_simple_icons(self):
        self.assertEqual("dotenv", ds.title_to_slug(".ENV"))
        self.assertEqual("cplusplus", ds.title_to_slug("C++"))
        self.assertEqual("dotnet", ds.title_to_slug(".NET"))
        self.assertEqual("handm", ds.title_to_slug("H&M"))
        self.assertEqual("citroen", ds.title_to_slug("Citroën"))

    def test_parse_color(self):
        self.assertEqual("#AABBCC", ds.parse_color("#abc"))
        self.assertEqual("#112233", ds.parse_color("rgb(17, 34, 51)"))
        self.assertIsNone(ds.parse_color("rgba(0, 0, 0, 0)"))
        self.assertIsNone(ds.parse_color("#11223300"))
        self.assertEqual("#FFFFFF", ds.parse_color("White"))
        self.assertIsNone(ds.parse_color("var(--brand)"))

    def test_clean_title_prefers_brand_segment(self):
        self.assertEqual("Example Mail", ds.clean_title("Example Mail | Inbox", "example"))
        self.assertEqual("Inbox", ds.clean_title("Welcome - Inbox", "zzz"))


class DeriveTest(unittest.TestCase):
    def derive(self, ev, url="https://mail.example.com/inbox"):
        with mock.patch.object(ds, "match_simple_icon", return_value=None), \
             mock.patch.object(ds, "choose_icon", return_value={"mode": "letter", "source": "none", "confidence": "low"}):
            return ds.derive(ev, url, psl())

    def test_metadata_without_manifest(self):
        ev = evidence(page={"metas": [{"name": "og:site_name", "content": "Example Mail", "media": ""}],
                            "themeColors": [{"color": "#1A73E8", "media": ""},
                                            {"color": "#202124", "media": "(prefers-color-scheme: dark)"}]},
                      requests=[{"url": "https://api.example.com/v1", "type": "fetch"},
                                {"url": "https://static.examplecdn.net/a.js", "type": "script"},
                                {"url": "https://www.google-analytics.com/c", "type": "xhr"}],
                      signals={"getUserMedia": [], "showNotification": ["x"]})
        app_id, fields, icon, notes = self.derive(ev)
        self.assertEqual("examplemail", app_id)
        self.assertEqual("Example Mail", fields["APP_NAME"].value)
        self.assertEqual("https://mail.example.com/inbox", fields["START_URL"].value)
        self.assertEqual(["example.com", "examplecdn.net"], fields["INTERNAL_DOMAINS"].value)
        self.assertEqual("#1A73E8", fields["HEADER_SURFACE_HEX"].value)
        self.assertEqual("#202124", fields["HEADER_SURFACE_HEX_NIGHT"].value)
        self.assertTrue(fields["ALLOW_NOTIFICATIONS"].value)
        self.assertFalse(fields["ALLOW_CAMERA"].value)
        self.assertEqual("E", icon["letter"])
        self.assertTrue(any("google-analytics.com" in n for n in notes))

    def test_manifest_start_url_and_name_win(self):
        ev = evidence(manifestUrl="https://mail.example.com/manifest.json",
                      manifest={"short_name": "Mail", "name": "Example Mail", "start_url": "/?source=pwa",
                                "theme_color": "#C5221F"})
        _, fields, _, _ = self.derive(ev)
        self.assertEqual("Mail", fields["APP_NAME"].value)
        self.assertEqual("https://mail.example.com/?source=pwa", fields["START_URL"].value)
        self.assertEqual("#C5221F", fields["ICON_BACKGROUND_HEX"].value)

    def test_redirect_to_other_host_keeps_requested_path(self):
        ev = evidence(finalUrl="https://www.example.com/login")
        _, fields, _, _ = self.derive(ev, "https://example.com/app")
        self.assertEqual("https://www.example.com/app", fields["START_URL"].value)

    def test_webview_block_and_store_redirect_notes(self):
        ev = evidence()
        ev["webview"] = {"status": 403, "title": "Unsupported browser", "text": "", "navigations": [
            "market://details?id=com.example"]}
        _, _, _, notes = self.derive(ev)
        self.assertTrue(any(n.startswith("WARNING: the site may refuse Android WebViews") for n in notes))
        self.assertTrue(any("Play Store" in n for n in notes))


class TomlOutputTest(unittest.TestCase):
    def test_toml_values(self):
        self.assertEqual('["a", "b"]', ds.toml_value(["a", "b"]))
        self.assertEqual("true", ds.toml_value(True))
        self.assertEqual('"Café \\"x\\""', ds.toml_value('Café "x"'))


if __name__ == "__main__":
    unittest.main()
