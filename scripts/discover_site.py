#!/usr/bin/env python3
"""Drafts sites/<id>/site.toml for a website, ready to build.

Visits the site with headless Chromium (scripts/discover/collect.js), then derives the app name,
start URL, domains, colors, icon and capabilities. Every generated key carries a comment with its
source and confidence, so the uncertain ones can be reviewed (by you, or by scripts/enrich_site.py).

    python3 scripts/discover_site.py https://example.com [--id ID] [--force]

Writes sites/<id>/site.toml, the chosen icon file, and sites/<id>/discovery.json (evidence,
gitignored). Standard library only; needs `node` with the `playwright` package for collection.
"""

import argparse
import base64
import datetime
import json
import os
import re
import ssl
import subprocess
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import generate_icons  # noqa: E402
import site_config  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
COLLECTOR = Path(__file__).resolve().parent / "discover" / "collect.js"
CACHE = Path(os.environ.get("XDG_CACHE_HOME", Path.home() / ".cache")) / "universal-android-wrapper"
PSL_URL = "https://publicsuffix.org/list/public_suffix_list.dat"
SIMPLE_ICONS_DATA = "https://raw.githubusercontent.com/simple-icons/simple-icons/develop/data/simple-icons.json"
SIMPLE_ICONS_SVG = "https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/{slug}.svg"
USER_AGENT = "universal-android-wrapper-discovery/1.0"
WEBVIEW_BLOCK_PATTERNS = [
    r"disallowed_useragent",
    r"browser (or app )?(may not be|is not) (secure|supported)",
    r"(this|your) browser is not supported",
    r"unsupported browser",
    r"please (use|open this in) (a|another|your) (supported )?browser",
    r"embedded browsers? (are|is) not supported",
]
NAMED_COLORS = {"white": "#FFFFFF", "black": "#000000", "red": "#FF0000", "blue": "#0000FF",
                "green": "#008000", "gray": "#808080", "grey": "#808080", "orange": "#FFA500"}


# ---------- Networking ----------

def fetch(url, timeout=30):
    if url.startswith("data:"):
        header, _, payload = url.partition(",")
        return base64.b64decode(payload) if ";base64" in header else urllib.parse.unquote_to_bytes(payload)
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=timeout, context=ssl.create_default_context()) as response:
        return response.read()


def cached(name, url, max_age_days):
    path = CACHE / name
    if path.is_file() and time.time() - path.stat().st_mtime < max_age_days * 86400:
        return path.read_bytes()
    try:
        data = fetch(url, timeout=60)
    except OSError as exc:
        if path.is_file():
            print(f"warning: could not refresh {name} ({exc}); using cached copy", file=sys.stderr)
            return path.read_bytes()
        raise
    CACHE.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return data


# ---------- Public Suffix List ----------

class PublicSuffixList:
    def __init__(self):
        try:
            text = cached("public_suffix_list.dat", PSL_URL, 30).decode("utf-8")
        except OSError as exc:
            print(f"warning: Public Suffix List unavailable ({exc}); using last-two-labels rule", file=sys.stderr)
            text = ""
        self.rules, self.wildcards, self.exceptions = set(), set(), set()
        for line in text.splitlines():
            line = line.strip()
            if not line or line.startswith("//"):
                continue
            if line.startswith("!"):
                self.exceptions.add(line[1:])
            elif line.startswith("*."):
                self.wildcards.add(line[2:])
            else:
                self.rules.add(line)

    def registrable(self, host):
        """eTLD+1 of host, e.g. cdn.discordapp.com -> discordapp.com."""
        labels = host.lower().rstrip(".").split(".")
        for i in range(len(labels)):
            candidate = ".".join(labels[i:])
            if candidate in self.exceptions:
                return ".".join(labels[i:])
            parent = ".".join(labels[i + 1:])
            if candidate in self.rules or parent in self.wildcards:
                return ".".join(labels[i - 1:]) if i > 0 else None
        return ".".join(labels[-2:]) if len(labels) >= 2 else None


# ---------- Helpers ----------

def host_of(url):
    try:
        return (urllib.parse.urlsplit(url).hostname or "").lower() or None
    except ValueError:
        return None


def parse_color(value):
    if not value:
        return None
    value = value.strip().lower()
    if value in NAMED_COLORS:
        return NAMED_COLORS[value]
    m = re.fullmatch(r"#([0-9a-f]{3,8})", value)
    if m:
        h = m.group(1)
        if len(h) in (3, 4):
            h = "".join(c * 2 for c in h[:3])
        elif len(h) == 8 and int(h[6:], 16) < 128:
            return None
        return "#" + h[:6].upper() if len(h) >= 6 else None
    m = re.fullmatch(r"rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)(?:[,\s/]+([\d.]+%?))?\s*\)", value)
    if m:
        alpha = m.group(4)
        if alpha and (float(alpha.rstrip("%")) / (100 if alpha.endswith("%") else 1)) < 0.5:
            return None
        return "#" + "".join(f"{min(int(m.group(i)), 255):02X}" for i in (1, 2, 3))
    return None


def title_to_slug(title):
    """simple-icons' titleToSlug."""
    replacements = {"+": "plus", ".": "dot", "&": "and", "đ": "d", "ħ": "h", "ı": "i", "ĸ": "k",
                    "ŀ": "l", "ł": "l", "ß": "ss", "ŧ": "t", "ø": "o"}
    slug = "".join(replacements.get(ch, ch) for ch in title.lower())
    slug = unicodedata.normalize("NFD", slug)
    return re.sub(r"[^a-z0-9]", "", slug)


def clean_title(title, token):
    parts = [p.strip() for p in re.split(r"\s+[|\-–—:·•]\s+", title) if p.strip()]
    for part in parts:
        if token and token in title_to_slug(part):
            return part
    return parts[-1] if parts else ""


class Field:
    """A config value plus where it came from, for the TOML comments."""

    def __init__(self, value, source, confidence):
        self.value, self.source, self.confidence = value, source, confidence


# ---------- Derivation ----------

def derive(evidence, requested_url, psl, forced_id=None):
    d = evidence["desktop"]
    page = d.get("page") or {}
    metas = page.get("metas") or []
    links = page.get("links") or []
    manifest = d.get("manifest") if isinstance(d.get("manifest"), dict) else {}
    manifest_url = d.get("manifestUrl") or ""
    final_url = d.get("finalUrl") or requested_url
    if d.get("error") and not page:
        raise SystemExit(f"error: could not load {requested_url}: {d['error']}")

    start_host = host_of(final_url) or host_of(requested_url)
    site = psl.registrable(start_host) or start_host
    token = site.split(".")[0]
    meta = {m["name"]: m["content"] for m in metas}
    fields, notes = {}, []

    # ----- Start URL -----
    # Keep the URL that was asked for: redirects on the same host are usually logged-out bounces
    # (e.g. /app -> /login). A redirect to another host of the same site (example.com ->
    # www.example.com) keeps the requested path on the new host.
    requested = urllib.parse.urlsplit(requested_url)
    final = urllib.parse.urlsplit(final_url)
    if final.hostname == requested.hostname or not final.hostname:
        start = Field(requested_url, "requested URL", "high")
    elif psl.registrable(final.hostname) == psl.registrable(requested.hostname or ""):
        start = Field(urllib.parse.urlunsplit(("https", final.netloc, requested.path or "/", requested.query, "")),
                      "requested path on the redirected host", "high")
    else:
        start = Field(final_url, "final URL after redirects (different site)", "medium")
    if manifest.get("start_url"):
        candidate = urllib.parse.urljoin(manifest_url, manifest["start_url"])
        if host_of(candidate) and psl.registrable(host_of(candidate)) == site and candidate.startswith("https://"):
            start = Field(candidate, "web manifest start_url", "high")
    if not start.value.startswith("https://"):
        start = Field("https://" + start_host + "/", "https origin of the site", "medium")
    fields["START_URL"] = start

    # ----- Domains -----
    observed = {}
    for request in d.get("requests", []):
        host = host_of(request["url"])
        if not host or request["url"].startswith(("data:", "blob:")):
            continue
        reg = psl.registrable(host)
        if reg:
            observed.setdefault(reg, set()).add(request["type"])
    internal = [site]
    for url in d.get("redirectChain", []) + [manifest_url]:
        reg = psl.registrable(host_of(url) or "") if url else None
        if reg and reg not in internal and token in reg:
            internal.append(reg)
    for reg in sorted(observed):
        if reg not in internal and len(token) >= 3 and token in reg.split(".")[0]:
            internal.append(reg)
    api_hosts = sorted(r for r, types in observed.items()
                       if r not in internal and types & {"xhr", "fetch", "websocket", "eventsource", "document"})
    fields["INTERNAL_DOMAINS"] = Field(
        internal, f"site domain + observed domains containing '{token}'", "medium")
    if api_hosts:
        notes.append("Other domains the page called (not internal; add any that belong to the site): "
                     + ", ".join(api_hosts[:15]))

    # ----- Name -----
    simple = match_simple_icon([manifest.get("short_name"), manifest.get("name"), meta.get("application-name"),
                                meta.get("apple-mobile-web-app-title"), meta.get("og:site_name"), token], site, psl)
    name_candidates = [
        (manifest.get("short_name") if 0 < len(manifest.get("short_name") or "") <= 15 else None, "web manifest short_name"),
        (manifest.get("name"), "web manifest name"),
        (simple["title"] if simple and simple["domain_match"] else None, "simple-icons brand title"),
        (meta.get("application-name"), "application-name meta"),
        (meta.get("apple-mobile-web-app-title"), "apple-mobile-web-app-title meta"),
        (meta.get("og:site_name"), "og:site_name meta"),
        (clean_title(page.get("title") or "", token), "page title"),
        (token.capitalize(), "domain name"),
    ]
    name, source = next((n.strip(), s) for n, s in name_candidates if n and n.strip())
    fields["APP_NAME"] = Field(name, source, "high" if "manifest" in source or "simple-icons" in source else "medium")

    # ----- Colors -----
    theme_light = theme_dark = None
    for tc in page.get("themeColors", []):
        media = tc.get("media", "")
        if "dark" in media:
            theme_dark = theme_dark or parse_color(tc["color"])
        else:
            theme_light = theme_light or parse_color(tc["color"])
    top_light = parse_color(page.get("topColor"))
    top_dark = parse_color((evidence.get("dark") or {}).get("topColor"))
    manifest_theme = parse_color(manifest.get("theme_color"))
    manifest_bg = parse_color(manifest.get("background_color"))
    header = next(f for f in [
        Field(theme_light, "theme-color meta", "high"),
        Field(top_light, "page background at the top edge (light mode)", "medium"),
        Field(manifest_theme, "web manifest theme_color", "medium"),
        Field("#FFFFFF", "default", "low"),
    ] if f.value)
    fields["HEADER_SURFACE_HEX"] = header
    night = Field("", "same as light (no dark variant detected)", "medium")
    if theme_dark and theme_dark != header.value:
        night = Field(theme_dark, "theme-color meta for prefers-color-scheme: dark", "high")
    elif top_dark and top_dark != header.value and top_dark != top_light:
        night = Field(top_dark, "page background at the top edge (dark mode)", "medium")
    fields["HEADER_SURFACE_HEX_NIGHT"] = night

    # ----- Icon -----
    app_id = forced_id or site_config.derive_app_id(name)
    icon = choose_icon(simple, links, manifest, manifest_url, final_url)
    if icon["mode"] == "svg":
        background = Field(icon["color"], icon["color_source"], "high") if icon.get("color") else None
    elif icon["mode"] == "raster" and not icon["maskable"]:
        background = Field(manifest_bg, "web manifest background_color", "medium") if manifest_bg else None
    else:
        background = None
    background = background or next(f for f in [
        Field(manifest_theme, "web manifest theme_color", "medium"),
        Field(theme_light, "theme-color meta", "medium"),
        Field(header.value, "header color", "low"),
    ] if f.value)
    fields["ICON_BACKGROUND_HEX"] = background
    if icon["mode"] == "letter":
        icon["letter"] = next((c for c in name.upper() if c.isascii() and c.isalnum()), "A")

    # ----- Capabilities -----
    signals = {k: bool(v) for k, v in d.get("signals", {}).items()}
    policy = (d.get("headers") or {}).get("permissions-policy", "")
    media_policy = {k: re.search(rf"{k}=\(\s*\)", policy) is None and k in policy for k in ("microphone", "camera")}
    uses_media = signals.get("getUserMedia") or signals.get("webRtc")
    fields["ALLOW_MICROPHONE"] = Field(bool(uses_media or media_policy["microphone"]),
                                       "getUserMedia/WebRTC in page scripts or Permissions-Policy", "low")
    fields["ALLOW_CAMERA"] = Field(bool(signals.get("getUserMedia") or media_policy["camera"]),
                                   "getUserMedia in page scripts or Permissions-Policy", "low")
    notifies = signals.get("notificationPermission") or signals.get("showNotification") or signals.get("pushManager")
    fields["ALLOW_NOTIFICATIONS"] = Field(bool(notifies), "Notification/showNotification/Push in page scripts", "medium")

    # ----- WebView compatibility -----
    webview = evidence.get("webview") or {}
    blob = " ".join(str(webview.get(k) or "") for k in ("title", "text", "error"))
    blocked = [p for p in WEBVIEW_BLOCK_PATTERNS if re.search(p, blob, re.I)]
    if (webview.get("status") or 0) >= 400 or blocked:
        notes.append(f"WARNING: the site may refuse Android WebViews (status {webview.get('status')}, "
                     f"matched: {', '.join(blocked) or 'HTTP error'}). The app may not work.")
    if any(n.startswith(("market:", "intent:")) or "play.google.com/store/apps" in n for n in webview.get("navigations", [])):
        notes.append("With a mobile user agent the site redirects to the Play Store; the store-redirect "
                     "fallback (UA_STORE_REDIRECT_FALLBACK) will switch to the desktop site.")
    if not manifest:
        notes.append("No web manifest found; name and colors come from page metadata.")
    return app_id, fields, icon, notes


def match_simple_icon(names, site, psl):
    """Finds the simple-icons entry for the brand; None if nothing matches confidently."""
    try:
        icons = json.loads(cached("simple-icons.json", SIMPLE_ICONS_DATA, 7))
    except (OSError, ValueError) as exc:
        print(f"warning: simple-icons data unavailable ({exc})", file=sys.stderr)
        return None
    wanted = {title_to_slug(n) for n in names if n and title_to_slug(n)}
    best = None
    for entry in icons:
        slug = entry.get("slug") or title_to_slug(entry["title"])
        aliases = {title_to_slug(a) for a in (entry.get("aliases", {}) or {}).get("aka", [])}
        if not ({slug, title_to_slug(entry["title"])} | aliases) & wanted:
            continue
        domains = {psl.registrable(host_of(entry.get(k, "")) or "") for k in ("source", "guidelines")}
        candidate = {"slug": slug, "title": entry["title"], "hex": "#" + entry["hex"].upper(),
                     "domain_match": site in domains}
        if best is None or (candidate["domain_match"] and not best["domain_match"]):
            best = candidate
    return best


def choose_icon(simple, links, manifest, manifest_url, page_url):
    """Picks the best icon source: simple-icons glyph > mask-icon glyph > raster > letter."""
    if simple:
        try:
            data = fetch(SIMPLE_ICONS_SVG.format(slug=simple["slug"]))
            generate_icons.parse_svg(data)
            how = "brand domain matched" if simple["domain_match"] else "brand name matched"
            return {"mode": "svg", "data": data, "ext": "svg", "color": simple["hex"],
                    "color_source": f"simple-icons brand color ({simple['title']})",
                    "source": f"simple-icons '{simple['slug']}' ({how})",
                    "confidence": "high" if simple["domain_match"] else "medium"}
        except (OSError, generate_icons.IconError) as exc:
            print(f"warning: simple-icons '{simple['slug']}' unusable: {exc}", file=sys.stderr)
    for link in links:
        if "mask-icon" in link["rel"].split():
            try:
                data = fetch(link["href"])
                generate_icons.parse_svg(data)
                return {"mode": "svg", "data": data, "ext": "svg", "color": parse_color(link.get("color")),
                        "color_source": "mask-icon color", "source": "Safari pinned-tab icon (mask-icon)",
                        "confidence": "medium"}
            except (OSError, generate_icons.IconError):
                pass

    candidates = []
    for icon in manifest.get("icons", []) or []:
        if not isinstance(icon, dict) or not icon.get("src"):
            continue
        sizes = [int(a) for s in str(icon.get("sizes", "")).split() if (m := re.fullmatch(r"(\d+)x(\d+)", s))
                 for a in [m.group(1)] if m.group(1) == m.group(2)]
        purpose = str(icon.get("purpose", "any")).split()
        candidates.append({"url": urllib.parse.urljoin(manifest_url, icon["src"]), "size": max(sizes, default=0),
                           "maskable": "maskable" in purpose, "source": "web manifest icon"})
    for link in links:
        rels = link["rel"].split()
        if "apple-touch-icon" in rels or "icon" in rels:
            sizes = [int(m.group(1)) for s in link.get("sizes", "").split()
                     if (m := re.fullmatch(r"(\d+)x\1", s))]
            candidates.append({"url": link["href"], "size": max(sizes, default=0), "maskable": False,
                               "source": "apple-touch-icon" if "apple-touch-icon" in rels else "page icon link"})
    candidates.append({"url": urllib.parse.urljoin(page_url, "/apple-touch-icon.png"), "size": 180,
                       "maskable": False, "source": "/apple-touch-icon.png"})
    candidates.sort(key=lambda c: (c["maskable"], c["size"]), reverse=True)
    for candidate in candidates:
        try:
            data = fetch(candidate["url"])
            ext, width, height = generate_icons.raster_info(data)
        except (OSError, ValueError, generate_icons.IconError):
            continue
        if width == height and width >= generate_icons.MIN_RASTER_PX:
            kind = "maskable " if candidate["maskable"] else ""
            return {"mode": "raster", "data": data, "ext": ext, "maskable": candidate["maskable"],
                    "source": f"{candidate['source']} ({kind}{width}px {ext})", "confidence": "medium"}
    return {"mode": "letter", "source": "no usable brand icon found", "confidence": "low"}


# ---------- Output ----------

def toml_value(value):
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, list):
        return "[" + ", ".join(toml_value(v) for v in value) + "]"
    return json.dumps(value, ensure_ascii=False)


def write_site(app_id, fields, icon, notes, url, force):
    directory = site_config.SITES / app_id
    if (directory / "site.toml").exists() and not force:
        raise SystemExit(f"error: sites/{app_id}/site.toml exists; use --force to overwrite or --id to pick another id")
    directory.mkdir(parents=True, exist_ok=True)
    for old in directory.glob("icon.*"):
        old.unlink()
    lines = [
        f"# Drafted by scripts/discover_site.py from {url} on {datetime.date.today().isoformat()}.",
        "# Review the low/medium-confidence keys, then build with: ./wrap --site " + app_id,
        "# Keys not set here come from sites/defaults.toml (see docs/SITE_CONFIG.md).",
    ]
    lines += [f"# NOTE: {n}" for n in notes]
    lines.append("")
    if fields["APP_NAME"].value and site_config.derive_app_id(fields["APP_NAME"].value) != app_id:
        lines.append(f"APP_ID = {toml_value(app_id)}")

    def emit(key, field):
        lines.append(f"{key} = {toml_value(field.value)}  # {field.source} ({field.confidence})")

    emit("APP_NAME", fields["APP_NAME"])
    emit("START_URL", fields["START_URL"])
    emit("INTERNAL_DOMAINS", fields["INTERNAL_DOMAINS"])
    lines.append("")
    if icon["mode"] == "letter":
        emit("ICON_LETTER", Field(icon["letter"], icon["source"], icon["confidence"]))
    else:
        filename = f"icon.{icon['ext']}"
        (directory / filename).write_bytes(icon["data"])
        key = "ICON_SVG" if icon["mode"] == "svg" else "ICON_RASTER"
        emit(key, Field(filename, icon["source"], icon["confidence"]))
        if icon["mode"] == "raster" and icon["maskable"]:
            lines.append("ICON_RASTER_MASKABLE = true")
    emit("ICON_BACKGROUND_HEX", fields["ICON_BACKGROUND_HEX"])
    emit("HEADER_SURFACE_HEX", fields["HEADER_SURFACE_HEX"])
    if fields["HEADER_SURFACE_HEX_NIGHT"].value:
        emit("HEADER_SURFACE_HEX_NIGHT", fields["HEADER_SURFACE_HEX_NIGHT"])
    lines.append("")
    for key in ("ALLOW_MICROPHONE", "ALLOW_CAMERA", "ALLOW_NOTIFICATIONS"):
        emit(key, fields[key])
    lines += ["", "# Keyboard shortcuts cannot be discovered; list the site's own shortcuts here if you like:",
              '# APP_SHORTCUTS = [{ label = "Search", key = "KEYCODE_K", modifiers = ["CTRL"] }]', ""]
    (directory / "site.toml").write_text("\n".join(lines), encoding="utf-8")
    return directory


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("url")
    parser.add_argument("--id", help="site id (default: derived from the app name)")
    parser.add_argument("--force", action="store_true", help="overwrite an existing site")
    parser.add_argument("--evidence", type=Path, help="reuse a saved discovery.json instead of visiting the site")
    args = parser.parse_args()

    url = args.url if re.match(r"(?i)^https?://", args.url) else "https://" + args.url
    if args.evidence:
        evidence = json.loads(args.evidence.read_text(encoding="utf-8"))
    else:
        print(f"visiting {url} (light, dark and Android WebView user agents)...", file=sys.stderr)
        proc = subprocess.run(["node", str(COLLECTOR), url], capture_output=True, text=True)
        if proc.returncode != 0:
            raise SystemExit(f"error: collection failed: {proc.stderr.strip()}")
        evidence = json.loads(proc.stdout)
    if args.id and not site_config.APP_ID_RE.match(args.id):
        raise SystemExit(f"error: --id must match {site_config.APP_ID_RE.pattern}")

    app_id, fields, icon, notes = derive(evidence, url, PublicSuffixList(), args.id)
    directory = write_site(app_id, fields, icon, notes, url, args.force)
    trimmed = dict(evidence)
    trimmed["desktop"] = {k: v for k, v in evidence["desktop"].items() if k != "requests"}
    trimmed["desktop"]["hosts"] = sorted({h for r in evidence["desktop"].get("requests", []) if (h := host_of(r["url"]))})
    (directory / "discovery.json").write_text(json.dumps(trimmed, indent=1), encoding="utf-8")

    try:
        site_config.load(app_id)
    except (site_config.ConfigError, generate_icons.IconError) as exc:
        print(f"warning: the draft needs fixing before it builds:\n{exc}", file=sys.stderr)
        print(directory / "site.toml")
        return 1
    print(f"wrote {directory.relative_to(ROOT)}/site.toml ({icon['mode']} icon)", file=sys.stderr)
    for note in notes:
        print(f"  note: {note}", file=sys.stderr)
    print(app_id)
    return 0


if __name__ == "__main__":
    sys.exit(main())
