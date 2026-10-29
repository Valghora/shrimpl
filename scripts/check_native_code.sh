#!/usr/bin/env bash
# Enforces the architecture rule (docs/SHRIMPLE_SPEC.md R0): every APK runs natively on ARM and x86
# devices with no translation layer (no NativeBridge / Houdini / Rosetta-style emulation).
#
#   scripts/check_native_code.sh --none APK...       the APK must contain no native code at all
#                                                     (the template, and so every generated web app)
#   scripts/check_native_code.sh --all-abis APK...   native libraries are allowed only when each one
#                                                     ships for all four ABIs (Shrimple's third-party libs)
#
# The four ABIs: arm64-v8a, armeabi-v7a, x86_64, x86. A library present for only some of them would
# force the others to run it translated (or not at all).
set -euo pipefail

ABIS=(arm64-v8a armeabi-v7a x86_64 x86)
mode="${1:-}"
shift || true
if [[ "$mode" != "--none" && "$mode" != "--all-abis" ]] || [[ $# -eq 0 ]]; then
  echo "usage: $0 --none|--all-abis APK..." >&2
  exit 2
fi

fail=0
for apk in "$@"; do
  libs="$(unzip -Z1 "$apk" | grep -E '^lib/[^/]+/[^/]+\.so$' || true)"
  if [[ "$mode" == "--none" ]]; then
    if [[ -n "$libs" ]]; then
      echo "::error::$apk contains native code:"; echo "$libs"; fail=1
    else
      echo "$apk: no native code"
    fi
    continue
  fi
  # --all-abis: every library name must exist under every ABI, and no other ABI directories.
  others="$(echo "$libs" | grep -vE "^lib/($(IFS='|'; echo "${ABIS[*]}"))/" || true)"
  if [[ -n "$others" ]]; then
    echo "::error::$apk has libraries for unexpected ABIs:"; echo "$others"; fail=1
  fi
  names="$(echo "$libs" | sed -E 's|^lib/[^/]+/||' | sort -u)"
  for name in $names; do
    for abi in "${ABIS[@]}"; do
      if ! grep -qx "lib/$abi/$name" <<<"$libs"; then
        echo "::error::$apk: $name is missing for $abi (that ABI would need translation)"; fail=1
      fi
    done
  done
  echo "$apk: $(echo "$names" | grep -c . || true) native libraries, each for ${ABIS[*]}"
done
exit $fail
