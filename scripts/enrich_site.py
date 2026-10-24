#!/usr/bin/env python3
"""Optional: asks Claude to review a drafted sites/<id>/site.toml and fill in the judgment calls.

Discovery (scripts/discover_site.py) gets the mechanical facts right; some keys need knowledge
of the site itself: keyboard shortcuts, sister domains, whether calls/video are used, and whether
a mobile or desktop user agent works better. This script sends the draft plus the discovery
evidence to Claude and applies only those keys, then re-validates the site. Code is never edited.

    pip install anthropic                     # only this optional step needs it
    export ANTHROPIC_API_KEY=...              # or `ant auth login`
    python3 scripts/enrich_site.py --site example

Options: --dry-run prints the request without sending it; --response FILE applies a saved
response (JSON) instead of calling the API.
"""

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import site_config  # noqa: E402

MODEL = "claude-opus-5-5"
MAX_EVIDENCE_HOSTS = 120

SYSTEM_PROMPT = """You review configuration for an Android app that wraps one website in a WebView.
A discovery script drafted the config from a headless-browser visit; you fill in what it cannot
know. Use your knowledge of the site plus the evidence provided. Change a key only when you are
confident; return null (or an empty list) to leave it as drafted. Keep each reason to one sentence.

- APP_NAME: the product's usual short name as shown under a launcher icon.
- INTERNAL_DOMAINS_ADD: registrable domains the site itself owns and navigates or signs in on
  (sister domains, short-link domains, CDNs that serve its pages). Never third-party analytics,
  ad, font or generic CDN domains. INTERNAL_DOMAINS_REMOVE: drafted domains that are third-party.
- ALLOW_MICROPHONE / ALLOW_CAMERA: true only if the web app offers voice/video calls, recording or
  scanning; ALLOW_NOTIFICATIONS: true if the web app sends web notifications for messages/alerts.
- USER_AGENT_MODE: "mobile" or "desktop" only if the site is known to work badly with the other
  one in a mobile browser; otherwise null (the app chooses by device size).
- APP_SHORTCUTS: the web app's own documented keyboard shortcuts that people use most (at most 8),
  as Android key names (KEYCODE_K, KEYCODE_SLASH, KEYCODE_ENTER, ...) with modifiers from CTRL,
  SHIFT, ALT, META. Use Ctrl, not Cmd. Never include Ctrl+R, F5, Ctrl+=, Ctrl+-, Ctrl+0 (reserved
  by the app). Empty list if you do not know the site's shortcuts for certain."""

NULLABLE_BOOL = {"type": ["boolean", "null"]}
PATCH_SCHEMA = {
    "type": "object",
    "properties": {
        "APP_NAME": {"type": ["string", "null"]},
        "INTERNAL_DOMAINS_ADD": {"type": "array", "items": {"type": "string"}},
        "INTERNAL_DOMAINS_REMOVE": {"type": "array", "items": {"type": "string"}},
        "ALLOW_MICROPHONE": NULLABLE_BOOL,
        "ALLOW_CAMERA": NULLABLE_BOOL,
        "ALLOW_NOTIFICATIONS": NULLABLE_BOOL,
        "USER_AGENT_MODE": {"type": ["string", "null"], "enum": ["mobile", "desktop", None]},
        "APP_SHORTCUTS": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "label": {"type": "string"},
                    "key": {"type": "string"},
                    "modifiers": {"type": "array", "items": {"type": "string", "enum": list(site_config.MODIFIERS)}},
                },
                "required": ["label", "key", "modifiers"],
                "additionalProperties": False,
            },
        },
        "reasons": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {"key": {"type": "string"}, "reason": {"type": "string"}},
                "required": ["key", "reason"],
                "additionalProperties": False,
            },
        },
    },
    "required": ["APP_NAME", "INTERNAL_DOMAINS_ADD", "INTERNAL_DOMAINS_REMOVE", "ALLOW_MICROPHONE",
                 "ALLOW_CAMERA", "ALLOW_NOTIFICATIONS", "USER_AGENT_MODE", "APP_SHORTCUTS", "reasons"],
    "additionalProperties": False,
}


class EnrichError(Exception):
    pass


def evidence_summary(site_dir):
    """A compact view of discovery.json: enough to judge, small enough to send."""
    path = site_dir / "discovery.json"
    if not path.is_file():
        return {"note": "no discovery.json (site was not drafted by discover_site.py)"}
    evidence = json.loads(path.read_text(encoding="utf-8"))
    desktop = evidence.get("desktop", {})
    page = desktop.get("page") or {}
    manifest = desktop.get("manifest") if isinstance(desktop.get("manifest"), dict) else {}
    return {
        "final_url": desktop.get("finalUrl"),
        "title": page.get("title"),
        "metas": [m for m in page.get("metas", []) if m["name"] in (
            "description", "application-name", "og:site_name", "og:title", "apple-mobile-web-app-title")],
        "manifest": {k: manifest.get(k) for k in ("name", "short_name", "start_url", "scope", "display") if k in manifest},
        "hosts_contacted": desktop.get("hosts", [])[:MAX_EVIDENCE_HOSTS],
        "script_signals": {k: bool(v) for k, v in desktop.get("signals", {}).items()},
        "webview_check": {k: evidence.get("webview", {}).get(k) for k in ("status", "title", "finalUrl")},
    }


def build_request(site_id):
    site_dir = site_config.site_dir(site_id)
    draft = (site_dir / "site.toml").read_text(encoding="utf-8")
    content = (
        f"Draft config (sites/{site_id}/site.toml):\n\n{draft}\n\n"
        f"Discovery evidence:\n\n{json.dumps(evidence_summary(site_dir), indent=1)}"
    )
    return {
        "model": MODEL,
        "max_tokens": 16000,
        "system": SYSTEM_PROMPT,
        "messages": [{"role": "user", "content": content}],
        "output_config": {"effort": "high", "format": {"type": "json_schema", "schema": PATCH_SCHEMA}},
    }


def call_claude(request):
    try:
        import anthropic
    except ImportError as exc:
        raise EnrichError("the optional enrich step needs the Anthropic SDK: pip install anthropic") from exc

    client = anthropic.Anthropic()
    try:
        # Server-side fallback: if a safety classifier declines, the API retries on Anthropic's
        # recommended fallback model within the same call.
        response = client.beta.messages.create(
            **request,
            betas=["server-side-fallback-2026-07-01"],
            fallbacks="default",
        )
    except anthropic.AuthenticationError as exc:
        raise EnrichError("authentication failed: set ANTHROPIC_API_KEY or run `ant auth login`") from exc
    except anthropic.PermissionDeniedError as exc:
        raise EnrichError(f"the API key cannot use {MODEL}: {exc.message}") from exc
    except anthropic.BadRequestError as exc:
        raise EnrichError(f"request rejected: {exc.message}") from exc
    except anthropic.RateLimitError as exc:
        raise EnrichError("rate limited after retries; try again later") from exc
    except anthropic.APIStatusError as exc:
        raise EnrichError(f"API error {exc.status_code}: {exc.message}") from exc
    except anthropic.APIConnectionError as exc:
        raise EnrichError(f"could not reach the API: {exc}") from exc

    if response.stop_reason == "refusal":
        category = getattr(response.stop_details, "category", None) if response.stop_details else None
        raise EnrichError(f"Claude declined to review this site (category: {category or 'unspecified'})")
    if response.stop_reason == "max_tokens":
        raise EnrichError("response was cut off (max_tokens)")
    text = next((block.text for block in response.content if block.type == "text"), None)
    if text is None:
        raise EnrichError("response had no text content")
    return json.loads(text)


# ---------- Applying the patch ----------

def toml_value(value):
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, list):
        return "[" + ", ".join(toml_value(v) for v in value) + "]"
    if isinstance(value, dict):
        return "{ " + ", ".join(f"{k} = {toml_value(v)}" for k, v in value.items()) + " }"
    return json.dumps(value, ensure_ascii=False)


def set_key(text, key, value, reason):
    """Replaces `KEY = ...` (single-line) or appends it; keeps the reason as a trailing comment."""
    line = f"{key} = {toml_value(value)}  # Claude review: {reason}"
    pattern = re.compile(rf"^{key}\s*=.*$", re.M)
    if pattern.search(text):
        return pattern.sub(lambda _: line, text, count=1)
    if "# ---------- Claude review ----------" not in text:
        text = text.rstrip("\n") + "\n\n# ---------- Claude review ----------\n"
    return text + line + "\n"


def apply_patch(site_id, patch):
    """Applies the allowed keys of patch to sites/<id>/site.toml; returns the list of changes."""
    path = site_config.site_dir(site_id) / "site.toml"
    original = path.read_text(encoding="utf-8")
    current = site_config.load(site_id)
    reasons = {r["key"]: r["reason"] for r in patch.get("reasons", [])}
    reason = lambda key: reasons.get(key, "suggested").replace("\n", " ")  # noqa: E731
    text, changes = original, []

    name = (patch.get("APP_NAME") or "").strip()
    if name and name != current["APP_NAME"]:
        text = set_key(text, "APP_NAME", name, reason("APP_NAME"))
        if site_config.derive_app_id(name) != site_id and not re.search(r"^APP_ID\s*=", text, re.M):
            text = set_key(text, "APP_ID", site_id, "keeps the site directory name")
        changes.append(f"APP_NAME: {current['APP_NAME']!r} -> {name!r}")

    add = [d.strip().lower() for d in patch.get("INTERNAL_DOMAINS_ADD", []) if d.strip()]
    remove = {d.strip().lower() for d in patch.get("INTERNAL_DOMAINS_REMOVE", [])}
    start_host = re.match(r"https://([^/:?#]+)", current["START_URL"]).group(1)
    remove = {d for d in remove if not (start_host == d or start_host.endswith("." + d))}
    domains = [d for d in current["INTERNAL_DOMAINS"] if d not in remove]
    domains += [d for d in add if d not in domains and site_config.HOST.match(d)]
    if domains != current["INTERNAL_DOMAINS"]:
        why = "; ".join(filter(None, [reasons.get("INTERNAL_DOMAINS_ADD"), reasons.get("INTERNAL_DOMAINS_REMOVE")]))
        text = set_key(text, "INTERNAL_DOMAINS", domains, why or "suggested")
        changes.append(f"INTERNAL_DOMAINS: {current['INTERNAL_DOMAINS']} -> {domains}")

    for key in ("ALLOW_MICROPHONE", "ALLOW_CAMERA", "ALLOW_NOTIFICATIONS"):
        value = patch.get(key)
        if isinstance(value, bool) and value != current[key]:
            text = set_key(text, key, value, reason(key))
            changes.append(f"{key}: {current[key]} -> {value}")

    mode = patch.get("USER_AGENT_MODE")
    if mode in ("mobile", "desktop") and mode != current["USER_AGENT_MODE"]:
        text = set_key(text, "USER_AGENT_MODE", mode, reason("USER_AGENT_MODE"))
        changes.append(f"USER_AGENT_MODE: {current['USER_AGENT_MODE']} -> {mode}")

    shortcuts = [s for s in patch.get("APP_SHORTCUTS", []) if site_config.KEYCODE.match(s.get("key", ""))]
    if shortcuts and not current["APP_SHORTCUTS"]:
        text = set_key(text, "APP_SHORTCUTS", shortcuts, reason("APP_SHORTCUTS"))
        changes.append(f"APP_SHORTCUTS: {len(shortcuts)} shortcuts")

    if not changes:
        return []
    path.write_text(text, encoding="utf-8")
    try:
        site_config.load(site_id)
    except site_config.ConfigError as exc:
        path.write_text(original, encoding="utf-8")
        raise EnrichError(f"the suggested changes do not validate, so none were applied:\n{exc}") from exc
    return changes


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--site", required=True)
    parser.add_argument("--dry-run", action="store_true", help="print the request instead of sending it")
    parser.add_argument("--response", type=Path, help="apply a saved JSON response instead of calling the API")
    args = parser.parse_args()
    try:
        site_config.load(args.site)
        request = build_request(args.site)
        if args.dry_run:
            print(json.dumps(request, indent=1))
            return 0
        patch = json.loads(args.response.read_text(encoding="utf-8")) if args.response else call_claude(request)
        changes = apply_patch(args.site, patch)
        print(f"sites/{args.site}/site.toml: " + ("; ".join(changes) if changes else "no changes suggested"),
              file=sys.stderr)
        return 0
    except (EnrichError, site_config.ConfigError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
