#!/usr/bin/env python3
"""Refreshes Shrimple's bundled discovery data (shrimple/src/main/assets/discovery/):

    public_suffix_list.dat   https://publicsuffix.org/list/ (MPL 2.0)

simple-icons are not bundled: Shrimple looks a brand up by name at https://cdn.simpleicons.org/<slug>.
Run from the repository root: python3 scripts/dev/update_shrimple_data.py
"""
import urllib.request
from pathlib import Path

OUT = Path("shrimple/src/main/assets/discovery")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen("https://publicsuffix.org/list/public_suffix_list.dat", timeout=120) as response:
        (OUT / "public_suffix_list.dat").write_bytes(response.read())
    print(f"PSL {(OUT / 'public_suffix_list.dat').stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
