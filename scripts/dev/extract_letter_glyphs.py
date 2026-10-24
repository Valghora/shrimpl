#!/usr/bin/env python3
"""Extracts A-Z and 0-9 outlines from a TrueType font into scripts/letter_glyphs.json.

Developer tool, run once; builds read only the committed JSON. Standard library only.

    python3 scripts/dev/extract_letter_glyphs.py /usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf
"""

import json
import struct
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "letter_glyphs.json"
CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"


def tables(data):
    count = struct.unpack(">H", data[4:6])[0]
    out = {}
    for i in range(count):
        tag, _, offset, length = struct.unpack(">4sIII", data[12 + 16 * i:28 + 16 * i])
        out[tag.decode("latin-1")] = data[offset:offset + length]
    return out


def cmap_format4(cmap):
    count = struct.unpack(">H", cmap[2:4])[0]
    for i in range(count):
        platform, encoding, offset = struct.unpack(">HHI", cmap[4 + 8 * i:12 + 8 * i])
        if platform == 3 and encoding == 1 and struct.unpack(">H", cmap[offset:offset + 2])[0] == 4:
            sub = cmap[offset:]
            seg = struct.unpack(">H", sub[6:8])[0] // 2
            ends = struct.unpack(f">{seg}H", sub[14:14 + 2 * seg])
            starts = struct.unpack(f">{seg}H", sub[16 + 2 * seg:16 + 4 * seg])
            deltas = struct.unpack(f">{seg}h", sub[16 + 4 * seg:16 + 6 * seg])
            range_base = 16 + 6 * seg
            ranges = struct.unpack(f">{seg}H", sub[range_base:range_base + 2 * seg])

            def lookup(code):
                for k in range(seg):
                    if starts[k] <= code <= ends[k]:
                        if ranges[k] == 0:
                            return (code + deltas[k]) & 0xFFFF
                        pos = range_base + 2 * k + ranges[k] + 2 * (code - starts[k])
                        glyph = struct.unpack(">H", sub[pos:pos + 2])[0]
                        return (glyph + deltas[k]) & 0xFFFF if glyph else 0
                return 0

            return lookup
    raise SystemExit("font has no Windows Unicode BMP (3,1) format-4 cmap")


def glyph_points(glyf, start, end):
    g = glyf[start:end]
    contours = struct.unpack(">h", g[0:2])[0]
    if contours < 0:
        raise SystemExit("composite glyphs are not supported")
    bbox = struct.unpack(">hhhh", g[2:10])
    end_pts = struct.unpack(f">{contours}H", g[10:10 + 2 * contours])
    n = end_pts[-1] + 1 if contours else 0
    pos = 10 + 2 * contours
    pos += 2 + struct.unpack(">H", g[pos:pos + 2])[0]
    flags = []
    while len(flags) < n:
        flag = g[pos]
        pos += 1
        flags.append(flag)
        if flag & 8:
            flags.extend([flag] * g[pos])
            pos += 1

    def coords(short_bit, same_bit):
        nonlocal pos
        values, value = [], 0
        for flag in flags:
            if flag & short_bit:
                delta = g[pos]
                pos += 1
                value += delta if flag & same_bit else -delta
            elif not flag & same_bit:
                value += struct.unpack(">h", g[pos:pos + 2])[0]
                pos += 2
            values.append(value)
        return values

    xs = coords(2, 16)
    ys = coords(4, 32)
    result, first = [], 0
    for last in end_pts:
        result.append([(xs[i], ys[i], bool(flags[i] & 1)) for i in range(first, last + 1)])
        first = last + 1
    return bbox, result


def contour_path(points, ascender):
    pts = [(x, ascender - y, on) for x, y, on in points]
    if not any(on for _, _, on in pts):
        (x0, y0, _), (x1, y1, _) = pts[0], pts[1]
        pts.insert(0, ((x0 + x1) / 2, (y0 + y1) / 2, True))
    while not pts[0][2]:
        pts.append(pts.pop(0))
    start = pts[0]
    parts = [f"M {start[0]} {start[1]}"]
    pending = None
    for x, y, on in pts[1:] + [start]:
        if on:
            parts.append(f"Q {pending[0]} {pending[1]} {x} {y}" if pending else f"L {x} {y}")
            pending = None
        elif pending:
            mx, my = (pending[0] + x) / 2, (pending[1] + y) / 2
            parts.append(f"Q {pending[0]} {pending[1]} {mx} {my}")
            pending = (x, y)
        else:
            pending = (x, y)
    parts.append("Z")
    return " ".join(parts)


def main():
    font = Path(sys.argv[1])
    t = tables(font.read_bytes())
    units = struct.unpack(">H", t["head"][18:20])[0]
    long_loca = struct.unpack(">h", t["head"][50:52])[0] == 1
    ascender = struct.unpack(">h", t["hhea"][4:6])[0]
    num_glyphs = struct.unpack(">H", t["maxp"][4:6])[0]
    loca = (struct.unpack(f">{num_glyphs + 1}I", t["loca"][:4 * (num_glyphs + 1)]) if long_loca
            else [v * 2 for v in struct.unpack(f">{num_glyphs + 1}H", t["loca"][:2 * (num_glyphs + 1)])])
    lookup = cmap_format4(t["cmap"])
    glyphs = {}
    for ch in CHARS:
        gid = lookup(ord(ch))
        (x_min, y_min, x_max, y_max), contours = glyph_points(t["glyf"], loca[gid], loca[gid + 1])
        glyphs[ch] = {
            "bbox": [x_min, ascender - y_max, x_max, ascender - y_min],
            "d": " ".join(contour_path(c, ascender) for c in contours),
        }
    OUT.write_text(json.dumps({
        "source": f"{font.name} (DejaVu fonts: Bitstream Vera license; see scripts/letter_glyphs.NOTICE)",
        "unitsPerEm": units,
        "fillRule": "nonzero",
        "glyphs": glyphs,
    }, indent=1) + "\n", encoding="utf-8")
    print(f"wrote {OUT} ({len(glyphs)} glyphs)")


if __name__ == "__main__":
    main()
