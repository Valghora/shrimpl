"""Tests for applying Claude's review (scripts/enrich_site.py) offline. Run: python3 -m unittest discover -s scripts/tests"""

import shutil
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SCRIPTS))

import enrich_site  # noqa: E402
import site_config  # noqa: E402

DRAFT = """# Drafted by scripts/discover_site.py
APP_NAME = "Example Chat"  # page title (medium)
START_URL = "https://chat.example.com/"  # requested URL (high)
INTERNAL_DOMAINS = ["example.com", "googletagmanager.com"]  # observed (medium)
ICON_LETTER = "E"  # no usable brand icon found (low)
ICON_BACKGROUND_HEX = "#3367D6"  # web manifest theme_color (medium)
HEADER_SURFACE_HEX = "#FFFFFF"  # theme-color meta (high)
ALLOW_MICROPHONE = false  # signals (low)
"""

EMPTY_PATCH = {"APP_NAME": None, "INTERNAL_DOMAINS_ADD": [], "INTERNAL_DOMAINS_REMOVE": [], "ALLOW_MICROPHONE": None,
               "ALLOW_CAMERA": None, "ALLOW_NOTIFICATIONS": None, "USER_AGENT_MODE": None, "APP_SHORTCUTS": [],
               "reasons": []}


class ApplyPatchTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.original_sites = site_config.SITES
        site_config.SITES = self.tmp
        shutil.copy(SCRIPTS.parent / "sites" / "defaults.toml", self.tmp / "defaults.toml")
        (self.tmp / "examplechat").mkdir()
        self.path = self.tmp / "examplechat" / "site.toml"
        self.path.write_text(DRAFT, encoding="utf-8")

    def tearDown(self):
        site_config.SITES = self.original_sites
        shutil.rmtree(self.tmp)

    def test_no_changes(self):
        self.assertEqual([], enrich_site.apply_patch("examplechat", EMPTY_PATCH))
        self.assertEqual(DRAFT, self.path.read_text())

    def test_applies_allowed_keys_and_keeps_directory(self):
        patch = {**EMPTY_PATCH,
                 "APP_NAME": "Chat",
                 "INTERNAL_DOMAINS_ADD": ["examplecdn.net", "not a domain"],
                 "INTERNAL_DOMAINS_REMOVE": ["googletagmanager.com", "example.com"],
                 "ALLOW_MICROPHONE": True,
                 "USER_AGENT_MODE": "desktop",
                 "APP_SHORTCUTS": [{"label": "Search", "key": "KEYCODE_K", "modifiers": ["CTRL"]},
                                   {"label": "Bad", "key": "k", "modifiers": []}],
                 "reasons": [{"key": "APP_NAME", "reason": "Launcher name is Chat."}]}
        changes = enrich_site.apply_patch("examplechat", patch)
        self.assertEqual(5, len(changes))
        c = site_config.load("examplechat")
        self.assertEqual(("Chat", "examplechat"), (c["APP_NAME"], c["APP_ID"]))
        # The start URL's own domain is never removed; invalid hostnames are ignored.
        self.assertEqual(["example.com", "examplecdn.net"], c["INTERNAL_DOMAINS"])
        self.assertTrue(c["ALLOW_MICROPHONE"])
        self.assertEqual("desktop", c["USER_AGENT_MODE"])
        self.assertEqual([{"label": "Search", "key": "KEYCODE_K", "modifiers": ["CTRL"]}], c["APP_SHORTCUTS"])
        text = self.path.read_text()
        self.assertIn('APP_NAME = "Chat"  # Claude review: Launcher name is Chat.', text)
        self.assertIn("# ---------- Claude review ----------", text)

    def test_invalid_result_is_rolled_back(self):
        patch = {**EMPTY_PATCH, "APP_SHORTCUTS": [{"label": " ", "key": "KEYCODE_K", "modifiers": ["CTRL"]}]}
        with self.assertRaises(enrich_site.EnrichError):
            enrich_site.apply_patch("examplechat", patch)
        self.assertEqual(DRAFT, self.path.read_text())

    def test_request_uses_structured_output(self):
        request = enrich_site.build_request("examplechat")
        self.assertEqual("claude-opus-5-5", request["model"])
        self.assertEqual("json_schema", request["output_config"]["format"]["type"])
        self.assertIn("Example Chat", request["messages"][0]["content"])
        schema = request["output_config"]["format"]["schema"]
        self.assertEqual(set(schema["properties"]), set(schema["required"]))


if __name__ == "__main__":
    unittest.main()
