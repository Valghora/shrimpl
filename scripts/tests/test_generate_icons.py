"""Tests for scripts/generate_icons.py. Run: python3 -m unittest discover -s scripts/tests"""

import shutil
import struct
import sys
import tempfile
import unittest
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import generate_icons as gi  # noqa: E402


def png(width, height):
    raw = b"".join(b"\x00" + b"\x10\x80\xf0\xff" * width for _ in range(height))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


class NormalizationTest(unittest.TestCase):
    def test_arc_flags_and_chained_decimals(self):
        self.assertEqual(
            "M 20.317 4.3698 a 19.7913 19.7913 0 0 0 -4.8851 -1.5152 a 0.0741 0.0741 0 0 0 -0.0785 0.0371",
            gi.android_safe_path(["M20.317 4.3698a19.7913 19.7913 0 00-4.8851-1.5152.0741.0741 0 00-.0785.0371"]),
        )

    def test_implicit_lineto_and_first_relative_moveto(self):
        self.assertEqual("M 1 1 l 2 2 l 3 3 M 0.5 0.5 L 100 -0.15",
                         gi.android_safe_path(["m1 1 2 2 3 3", "M.5.5L1e2-1.5e-1"]))

    def test_number_formatting(self):
        self.assertEqual(["0", "0", "1.2346", "-3", "0.0001"],
                         [gi.fmt(v) for v in (-0.0, 0.00001, 1.234567, -3.0, 0.0001)])

    def test_rejects_bad_path_data(self):
        for d in ("1 2 L 3 4", "M 1", "A 1 1 0 2 0 3 3", "M 1 2 X 3"):
            with self.subTest(d=d), self.assertRaises(gi.IconError):
                gi.android_safe_path([d])

    def test_verify_rules(self):
        self.assertEqual([], gi.check_path_data("M 1 1 A 1 1 0 1 0 3 3 Z"))
        self.assertTrue(gi.check_path_data("M 1 1 A 1 1 0 2 0 3 3"))
        self.assertTrue(gi.check_path_data("M .5 1"))
        self.assertTrue(gi.check_path_data("M 1"))
        self.assertTrue(gi.check_path_data("M 1  1"))
        self.assertTrue(gi.check_path_data("M 1e2 1"))


class SvgSourceTest(unittest.TestCase):
    SVG = '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">{}</svg>'

    def test_accepts_glyph_and_reads_fill_rule(self):
        box, paths, rule = gi.parse_svg(self.SVG.format(
            '<title>x</title><g style="fill-rule:evenodd"><path fill="#fff" d="M0 0h1v1z"/></g>').encode())
        self.assertEqual(((0, 0, 24, 24), ["M0 0h1v1z"], "evenodd"), (box, paths, rule))

    def test_rejects_non_glyphs(self):
        for body in ('<circle r="1"/><path d="M0 0h1v1z"/>', '<path transform="scale(2)" d="M0 0h1z"/>',
                     '<path fill="none" stroke="#000" d="M0 0h1z"/>', '<defs/><path d="M0 0h1z"/>',
                     '<path fill="url(#g)" d="M0 0h1z"/>', ""):
            with self.subTest(body=body), self.assertRaises(gi.IconError):
                gi.parse_svg(self.SVG.format(body).encode())


class RasterInfoTest(unittest.TestCase):
    def test_png_dimensions(self):
        self.assertEqual(("png", 256, 256), gi.raster_info(png(256, 256)))

    def test_webp_lossless_dimensions(self):
        bits = (300 - 1) | ((200 - 1) * 0x4000)
        data = b"RIFF" + b"\0" * 4 + b"WEBP" + b"VP8L" + b"\0" * 4 + b"\x2f" + bits.to_bytes(4, "little")
        self.assertEqual(("webp", 300, 200), gi.raster_info(data))

    def test_rejects_other_formats(self):
        with self.assertRaises(gi.IconError):
            gi.raster_info(b"GIF89a....")


class GenerateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def config(self, mode, **extra):
        return {"ICON_MODE": mode, "ICON_BACKGROUND_HEX": "#0B57D0", "ICON_FOREGROUND_HEX": "#FFFFFF",
                "APP_NAME": "Gmail", **extra}

    def test_letter_icons_verify(self):
        res = self.tmp / "res"
        gi.generate(self.config("letter", ICON_LETTER="g"), self.tmp, res)
        self.assertEqual(0, gi.verify(res))
        self.assertIn("ic_launcher_foreground", (res / "mipmap-anydpi/ic_launcher.xml").read_text())

    def test_raster_icons_use_inset_and_letter_monochrome(self):
        (self.tmp / "icon.png").write_bytes(png(512, 512))
        res = self.tmp / "res"
        gi.generate(self.config("raster", ICON_RASTER="icon.png", ICON_RASTER_MASKABLE=True), self.tmp, res)
        self.assertEqual(0, gi.verify(res))
        self.assertTrue((res / "drawable-nodpi/ic_launcher_raster.png").is_file())
        self.assertIn('android:inset="9dp"', (res / "drawable/ic_launcher_foreground.xml").read_text())
        self.assertIn("@drawable/ic_launcher_monochrome", (res / "mipmap-anydpi/ic_launcher.xml").read_text())

    def test_raster_must_be_square_and_large_enough(self):
        for size in ((300, 200), (96, 96)):
            (self.tmp / "icon.png").write_bytes(png(*size))
            with self.subTest(size=size), self.assertRaises(gi.IconError):
                gi.generate(self.config("raster", ICON_RASTER="icon.png", ICON_RASTER_MASKABLE=False),
                            self.tmp, self.tmp / "res")

    def test_svg_icons_match_spec_transform(self):
        (self.tmp / "icon.svg").write_text(
            '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M0 0h24v24H0z"/></svg>')
        res = self.tmp / "res"
        gi.generate(self.config("svg", ICON_SVG="icon.svg"), self.tmp, res)
        foreground = (res / "drawable/ic_launcher_foreground.xml").read_text()
        self.assertIn('android:scaleX="2.25"', foreground)
        self.assertIn('android:translateX="27"', foreground)
        self.assertEqual(0, gi.verify(res))

    def test_regeneration_removes_stale_files(self):
        (self.tmp / "icon.png").write_bytes(png(512, 512))
        res = self.tmp / "res"
        gi.generate(self.config("raster", ICON_RASTER="icon.png", ICON_RASTER_MASKABLE=False), self.tmp, res)
        gi.generate(self.config("letter", ICON_LETTER="G"), self.tmp, res)
        self.assertFalse((res / "drawable-nodpi").exists())
        self.assertEqual(0, gi.verify(res))


if __name__ == "__main__":
    unittest.main()
