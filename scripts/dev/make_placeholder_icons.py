#!/usr/bin/env python3
"""Draws the template's placeholder icon layers (a white globe on blue-gray) as PNGs.

Shrimple replaces these files in every web app it builds (docs/SHRIMPLE_SPEC.md R7.4, R10.3).
Run from the repository root: python3 scripts/dev/make_placeholder_icons.py
"""
import math
import struct
import zlib
from pathlib import Path

OUT = Path("template/src/main/res/drawable-nodpi")
BACKGROUND = (0x60, 0x7D, 0x8B)
SAMPLES = 3  # supersampling per axis


def png(path, size, pixel):
    rows = bytearray()
    for y in range(size):
        rows.append(0)
        for x in range(size):
            rows += bytes(pixel(x, y))
    data = zlib.compress(bytes(rows), 9)

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body))

    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", data) + chunk(b"IEND", b""))


def globe(u, v, radius, stroke):
    """True if (u, v), relative to the center, is on a stroke of the globe outline."""
    r = math.hypot(u, v)
    if abs(r - radius) <= stroke / 2:
        return True  # outline
    if r > radius:
        return False
    if abs(v) <= stroke / 2:
        return True  # equator
    if abs(abs(v) - radius * 0.5) <= stroke / 2 and r < radius:
        return True  # two latitude lines
    meridian = math.hypot(u / 0.45, v)  # vertical ellipse, half as wide
    return abs(meridian - radius) <= stroke / 2 / 0.45 * 0.6 or abs(u) <= stroke / 2


def glyph_layer(size, radius, stroke):
    center = size / 2

    def pixel(x, y):
        hits = 0
        for sy in range(SAMPLES):
            for sx in range(SAMPLES):
                u = x + (sx + 0.5) / SAMPLES - center
                v = y + (sy + 0.5) / SAMPLES - center
                hits += globe(u, v, radius, stroke)
        alpha = round(255 * hits / SAMPLES ** 2)
        return (255, 255, 255, alpha)

    return pixel


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    png(OUT / "ic_launcher_background.png", 432, lambda x, y: (*BACKGROUND, 255))
    # Foreground and monochrome: the globe inside the adaptive icon's 66 percent safe zone (radius 144 px).
    png(OUT / "ic_launcher_foreground.png", 432, glyph_layer(432, 100, 14))
    png(OUT / "ic_launcher_monochrome.png", 432, glyph_layer(432, 100, 16))  # bolder: themed icons are small
    # Notification small icon: white silhouette, 96 px (24 dp at xxxhdpi).
    png(OUT / "ic_stat_notify.png", 96, glyph_layer(96, 38, 6))


if __name__ == "__main__":
    main()
