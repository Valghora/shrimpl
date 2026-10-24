#!/usr/bin/env bash
# Creates a dedicated signing key for sideloaded builds.
#
# Android only installs an update if it is signed with the same key as the installed app. The
# default debug key differs per machine (and per CI run), so builds from different places cannot
# update each other. Sign every build with this one key instead.
#
#   scripts/create_sideload_key.sh [KEYSTORE]     (default: ~/.android/sideload.jks)
#
# The password is generated and stored next to the keystore in KEYSTORE.password (mode 600).
# Back both files up: losing the key means uninstalling the apps to update them.
set -euo pipefail

keystore="${1:-$HOME/.android/sideload.jks}"
password_file="$keystore.password"
alias="sideload"

if [[ -e "$keystore" ]]; then
  echo "error: $keystore already exists; refusing to overwrite it" >&2
  exit 1
fi
command -v keytool >/dev/null || { echo "error: keytool not found (it ships with the JDK)" >&2; exit 1; }

mkdir -p "$(dirname "$keystore")"
umask 077
password="${SIDELOAD_KEYSTORE_PASSWORD:-$(python3 -c 'import secrets; print(secrets.token_urlsafe(24))')}"
printf '%s' "$password" > "$password_file"
keytool -genkeypair -keystore "$keystore" -storetype PKCS12 -alias "$alias" \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$password" -dname "CN=Universal Android Wrapper sideload" >/dev/null 2>&1

cat <<EOF
Created $keystore (alias "$alias"); password in $password_file.

Use it for local builds: add to ~/.gradle/gradle.properties
  sideload.keystore=$keystore
  sideload.storePassword=<contents of $password_file>

Use it in GitHub Actions (run from the repository):
  base64 -w0 "$keystore" | gh secret set SIDELOAD_KEYSTORE_B64
  gh secret set SIDELOAD_KEYSTORE_PASSWORD < "$password_file"

Release builds then sign with this key instead of the debug key. Apps already installed with the
debug key must be uninstalled once before the first sideload-signed update.
EOF
