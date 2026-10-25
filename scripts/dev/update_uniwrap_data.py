#!/usr/bin/env python3
"""Refreshes UniWrap's bundled discovery data (uniwrap/src/main/assets/discovery/):

    public_suffix_list.dat   https://publicsuffix.org/list/ (MPL 2.0)
    simple-icons.json        simple-icons metadata plus each glyph's SVG path (CC0; brands are trademarks)

Run from the repository root: python3 scripts/dev/update_uniwrap_data.py [simple-icons version]
"""
import io
import json
import re
import sys
import tarfile
import urllib.request
from pathlib import Path

SIMPLE_ICONS_VERSION = sys.argv[1] if len(sys.argv) > 1 else "16.35.0"
OUT = Path("uniwrap/src/main/assets/discovery")
PATH = re.compile(rb'<path d="([^"]+)"')


def get(url):
    with urllib.request.urlopen(url, timeout=120) as response:
        return response.read()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "public_suffix_list.dat").write_bytes(get("https://publicsuffix.org/list/public_suffix_list.dat"))

    tgz = get(f"https://registry.npmjs.org/simple-icons/-/simple-icons-{SIMPLE_ICONS_VERSION}.tgz")
    with tarfile.open(fileobj=io.BytesIO(tgz)) as tar:
        data = json.load(tar.extractfile("package/data/simple-icons.json"))
        entries = []
        for icon in data:
            svg = tar.extractfile(f"package/icons/{icon['slug']}.svg").read()
            match = PATH.search(svg)
            if not match:
                raise SystemExit(f"no path in {icon['slug']}.svg")
            entry = {k: icon[k] for k in ("title", "slug", "hex", "source") if k in icon}
            if icon.get("guidelines"):
                entry["guidelines"] = icon["guidelines"]
            aka = (icon.get("aliases") or {}).get("aka")
            if aka:
                entry["aliases"] = {"aka": aka}
            entry["path"] = match.group(1).decode()
            entries.append(entry)
    (OUT / "simple-icons.json").write_text(json.dumps(entries, separators=(",", ":"), ensure_ascii=False))
    (OUT / "VERSIONS").write_text(f"simple-icons {SIMPLE_ICONS_VERSION}\npublic_suffix_list fetched with this script\n")
    print(f"{len(entries)} icons; {(OUT / 'simple-icons.json').stat().st_size // 1024} KB; "
          f"PSL {(OUT / 'public_suffix_list.dat').stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
