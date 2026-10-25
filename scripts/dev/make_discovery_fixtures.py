#!/usr/bin/env python3
"""Records discovery fixtures for the Kotlin port (core/src/test/resources/discovery/).

For each site: collects full evidence with scripts/discover/collect.js, runs the Python rules
(scripts/discover_site.py derive()) while recording every URL they fetch, and saves:

    <name>/evidence.json    the evidence, in the Kotlin Evidence shape (core/.../discovery/Evidence.kt)
    <name>/responses.json   every recorded fetch: url -> base64 body, or null if it failed
    <name>/expected.json    the Python draft: app id, fields with source and confidence, icon, notes

plus simple-icons.json (the entries any fixture could match) and public_suffix_list.dat.
Needs node + playwright and network access. Run from the repository root:

    python3 scripts/dev/make_discovery_fixtures.py
"""
import base64
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, "scripts")
import discover_site  # noqa: E402

OUT = Path("core/src/test/resources/discovery")
SITES = {
    "example": "https://example.com",
    "github": "https://github.com",
    "squoosh": "https://squoosh.app",
    "discord": "https://discord.com/app",
}
NOT_RECORDED = (discover_site.PSL_URL, discover_site.SIMPLE_ICONS_DATA)


def kotlin_evidence(raw):
    """collect.js output -> the Kotlin Evidence shape (signals as booleans, stable field names)."""
    d = raw["desktop"]
    page = d.get("page")
    desktop = {
        "requestedUrl": d.get("requestedUrl"),
        "finalUrl": d.get("finalUrl"),
        "status": d.get("status"),
        "error": d.get("error"),
        "headers": {k.lower(): v for k, v in (d.get("headers") or {}).items()},
        "redirectChain": d.get("redirectChain") or [],
        "requests": [{"url": r["url"], "type": r["type"]} for r in d.get("requests", [])],
        "signals": {k: bool(v) for k, v in (d.get("signals") or {}).items()},
        "manifestUrl": d.get("manifestUrl"),
        "manifest": d.get("manifest") if isinstance(d.get("manifest"), dict) else None,
    }
    if page:
        desktop["page"] = {
            "title": page.get("title") or "",
            "metas": [{"name": m["name"], "content": m["content"], "media": m.get("media", "")} for m in page.get("metas", [])],
            "links": [{"rel": l["rel"], "href": l["href"], "sizes": l.get("sizes", ""), "color": l.get("color", "")}
                      for l in page.get("links", [])],
            "themeColors": [{"color": t.get("color", ""), "media": t.get("media", "")} for t in page.get("themeColors", [])],
            "topColor": page.get("topColor"),
        }

    def slim(v):
        v = v or {}
        return {k: v.get(k) for k in ("status", "error", "finalUrl", "title", "topColor", "text")} | {
            "navigations": v.get("navigations") or []}

    return {k: v for k, v in {"desktop": desktop, "dark": slim(raw.get("dark")), "webview": slim(raw.get("webview"))}.items()}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    psl = discover_site.PublicSuffixList()
    shutil.copy(discover_site.CACHE / "public_suffix_list.dat", OUT / "public_suffix_list.dat")
    all_icons = json.loads((discover_site.CACHE / "simple-icons.json").read_bytes())
    wanted_all = set()
    original_fetch = discover_site.fetch

    for name, url in SITES.items():
        print(f"collecting {url}", file=sys.stderr)
        proc = subprocess.run(["node", str(discover_site.COLLECTOR), url], capture_output=True, text=True, check=True)
        raw = json.loads(proc.stdout)
        responses = {}

        def recording_fetch(u, timeout=30):
            if u in NOT_RECORDED:
                return original_fetch(u, timeout)
            try:
                data = original_fetch(u, timeout)
            except OSError:
                responses[u] = None
                raise
            responses[u] = base64.b64encode(data).decode()
            return data

        discover_site.fetch = recording_fetch
        try:
            app_id, fields, icon, notes = discover_site.derive(raw, url, psl)
        finally:
            discover_site.fetch = original_fetch

        d = raw["desktop"]
        manifest = d.get("manifest") if isinstance(d.get("manifest"), dict) else {}
        meta = {m["name"]: m["content"] for m in (d.get("page") or {}).get("metas", [])}
        site = psl.registrable(discover_site.host_of(d.get("finalUrl") or url) or "")
        names = [manifest.get("short_name"), manifest.get("name"), meta.get("application-name"),
                 meta.get("apple-mobile-web-app-title"), meta.get("og:site_name"), site.split(".")[0]]
        wanted_all |= {discover_site.title_to_slug(n) for n in names if n and discover_site.title_to_slug(n)}

        expected = {
            "requestedUrl": url,
            "appId": app_id,
            "fields": {k: {"value": f.value, "source": f.source, "confidence": f.confidence} for k, f in fields.items()},
            "icon": {k: v for k, v in icon.items() if k != "data"}
            | ({"sha256": hashlib.sha256(icon["data"]).hexdigest()} if "data" in icon else {}),
            "notes": notes,
        }
        directory = OUT / name
        directory.mkdir(exist_ok=True)
        (directory / "evidence.json").write_text(json.dumps(kotlin_evidence(raw), indent=1, ensure_ascii=False) + "\n")
        (directory / "responses.json").write_text(json.dumps(responses, indent=1) + "\n")
        (directory / "expected.json").write_text(json.dumps(expected, indent=1, ensure_ascii=False) + "\n")
        print(f"  {name}: {app_id}, icon {icon['mode']} ({icon['source']}), {len(responses)} fetches", file=sys.stderr)

    # Every simple-icons entry any fixture name could match, plus a few unrelated ones.
    def keys(entry):
        slug = entry.get("slug") or discover_site.title_to_slug(entry["title"])
        aliases = {discover_site.title_to_slug(a) for a in (entry.get("aliases", {}) or {}).get("aka", [])}
        return {slug, discover_site.title_to_slug(entry["title"])} | aliases

    subset = [e for e in all_icons if keys(e) & wanted_all] + all_icons[:5]
    (OUT / "simple-icons.json").write_text(json.dumps(subset, indent=1, ensure_ascii=False) + "\n")
    print(f"simple-icons subset: {len(subset)} entries", file=sys.stderr)


if __name__ == "__main__":
    main()
