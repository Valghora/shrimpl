#!/usr/bin/env python3
"""Renders a site's generated icons as a preview sheet (SVG, optionally PNG via Playwright).

Shows the adaptive icon under circle and squircle masks, the themed (monochrome) icon and the
status-bar notification icon, so a new site's icon can be checked before installing.

    python3 scripts/preview_icons.py RES_DIR --out preview.svg [--png preview.png]
"""

import argparse
import base64
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

A = "{http://schemas.android.com/apk/res/android}"


def vector_to_svg_group(path):
    """Converts an Android <vector> drawable into an SVG <g> in its own viewport units."""
    root = ET.parse(path).getroot()

    def convert(element):
        out = []
        for child in element:
            tag = child.tag
            if tag == "group":
                sx, sy = child.get(A + "scaleX", "1"), child.get(A + "scaleY", "1")
                tx, ty = child.get(A + "translateX", "0"), child.get(A + "translateY", "0")
                out.append(f'<g transform="translate({tx} {ty}) scale({sx} {sy})">{convert(child)}</g>')
            elif tag == "path":
                rule = "evenodd" if child.get(A + "fillType") == "evenOdd" else "nonzero"
                out.append(f'<path d="{child.get(A + "pathData")}" fill="{child.get(A + "fillColor", "none")}" fill-rule="{rule}"/>')
        return "".join(out)

    return root.get(A + "viewportWidth"), convert(root)


def layer(res, name):
    """SVG markup for drawable `name`, scaled to the 108-unit adaptive canvas."""
    xml = res / "drawable" / f"{name}.xml"
    root = ET.parse(xml).getroot()
    if root.tag == "inset":
        target = root.get(A + "drawable").removeprefix("@drawable/")
        inset = float(root.get(A + "inset").removesuffix("dp"))
        image = next(res.glob(f"drawable*/{target}.*"))
        mime = "image/webp" if image.suffix == ".webp" else "image/png"
        data = base64.b64encode(image.read_bytes()).decode()
        size = 108 - 2 * inset
        return f'<image x="{inset}" y="{inset}" width="{size}" height="{size}" href="data:{mime};base64,{data}"/>'
    viewport, body = vector_to_svg_group(xml)
    scale = 108 / float(viewport)
    return f'<g transform="scale({scale})">{body}</g>'


def monochrome_name(res):
    adaptive = ET.parse(res / "mipmap-anydpi" / "ic_launcher.xml").getroot()
    mono = adaptive.find("monochrome")
    return mono.get(A + "drawable").removeprefix("@drawable/") if mono is not None else None


def render(res):
    background, foreground = layer(res, "ic_launcher_background"), layer(res, "ic_launcher_foreground")
    mono_name = monochrome_name(res)
    mono = layer(res, mono_name) if mono_name else ""
    notify_viewport, notify = vector_to_svg_group(res / "drawable" / "ic_stat_notify.xml")
    squircle = "M54 18 C81 18 90 27 90 54 C90 81 81 90 54 90 C27 90 18 81 18 54 C18 27 27 18 54 18 Z"
    cells = [
        ("circle", f'<clipPath id="c"><circle cx="54" cy="54" r="36"/></clipPath>'
                   f'<g clip-path="url(#c)">{background}{foreground}</g>'),
        ("squircle", f'<clipPath id="s"><path d="{squircle}"/></clipPath>'
                     f'<g clip-path="url(#s)">{background}{foreground}</g>'),
        ("themed", '<clipPath id="t"><circle cx="54" cy="54" r="36"/></clipPath>'
                   f'<g clip-path="url(#t)"><rect width="108" height="108" fill="#D7E3FF"/>'
                   f'<g class="themed">{mono}</g></g>'
                   if mono else '<text x="54" y="58" font-size="9" text-anchor="middle">no themed icon</text>'),
        ("status bar", f'<rect x="18" y="18" width="72" height="72" rx="10" fill="#202124"/>'
                       f'<g transform="translate(30 30) scale({48 / float(notify_viewport)})">{notify}</g>'),
    ]
    width = 140 * len(cells)
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="150" viewBox="0 0 {width} 150">',
             # Themed icons paint every monochrome path in the system theme color.
             '<style>.themed path{fill:#1B2D4F}</style>',
             '<rect width="100%" height="100%" fill="#F1F3F4"/>']
    for i, (label, body) in enumerate(cells):
        parts.append(f'<g transform="translate({16 + 140 * i} 8)">{body}'
                     f'<text x="54" y="130" font-family="sans-serif" font-size="12" text-anchor="middle">{label}</text></g>')
    parts.append("</svg>")
    return "".join(parts)


PNG_SCRIPT = """
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const [svgPath, pngPath] = process.argv.slice(1);  // node -e: args start at argv[1]
  const browser = await chromium.launch();
  const page = await browser.newPage({ deviceScaleFactor: 2 });
  await page.setContent(`<body style="margin:0">${fs.readFileSync(svgPath, 'utf8')}</body>`);
  await page.locator('svg').screenshot({ path: pngPath });
  await browser.close();
})().catch(e => { console.error(e.message); process.exit(1); });
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("res_dir", type=Path)
    parser.add_argument("--out", type=Path, required=True, help="SVG preview to write")
    parser.add_argument("--png", type=Path, help="also render a PNG (needs node + playwright)")
    args = parser.parse_args()
    svg = render(args.res_dir)
    args.out.write_text(svg, encoding="utf-8")
    print(f"wrote {args.out}")
    if args.png:
        if not shutil.which("node"):
            print("node not found; skipping PNG", file=sys.stderr)
            return 1
        subprocess.run(["node", "-e", PNG_SCRIPT, str(args.out), str(args.png)], check=True)
        print(f"wrote {args.png}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
