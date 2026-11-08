#!/usr/bin/env bash
# Installs the emulator and a system image, creates an AVD, boots it headless and waits until it is
# ready for instrumented tests (docs/SHRIMPL_SPEC.md R12.4, docs/BUILD_NOTES.md, "Emulator").
#
# It needs hardware virtualization (/dev/kvm): GitHub's Linux runners have it, the cloud container
# Claude Code works in does not, and Android's emulator can't run ARM images on an x86 host.
#
# Env:
#   ANDROID_HOME    SDK location (default /opt/android-sdk)
#   EMULATOR_IMAGE  system image (default: Android 37.2, Google APIs, x86_64, 16 KB pages)
#   EMULATOR_DIR    where logs go (default ./emulator-out)
set -euo pipefail

export ANDROID_HOME="$(realpath -m "${ANDROID_HOME:-/opt/android-sdk}")"
IMAGE="${EMULATOR_IMAGE:-system-images;android-37.2;google_apis_ps16k;x86_64}"
OUT="${EMULATOR_DIR:-$PWD/emulator-out}"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
ADB="$ANDROID_HOME/platform-tools/adb"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"

log() { echo "[start_emulator] $*"; }
mkdir -p "$OUT" "$ANDROID_AVD_HOME"

if [[ ! -e /dev/kvm ]]; then
  echo "ERROR: no /dev/kvm. The x86_64 emulator needs hardware virtualization; run this on a GitHub runner or a machine with KVM." >&2
  exit 1
fi
if [[ ! -w /dev/kvm ]]; then
  log "making /dev/kvm usable for this user"
  echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules >/dev/null
  sudo udevadm control --reload-rules
  sudo udevadm trigger --name-match=kvm
fi

log "installing emulator and $IMAGE"
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" --install "emulator" "$IMAGE" 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS' | tail -3

log "creating the AVD"
echo no | "$AVDMANAGER" create avd -n ci -k "$IMAGE" -d pixel_6 --force 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS' | tail -2
cat >> "$ANDROID_AVD_HOME/ci.avd/config.ini" <<'INI'
hw.ramSize=3072
disk.dataPartition.size=3G
hw.keyboard=yes
INI

log "starting the emulator"
nohup "$ANDROID_HOME/emulator/emulator" -avd ci -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu swiftshader_indirect -no-metrics -accel on > "$OUT/emulator.log" 2>&1 &
echo $! > "$OUT/emulator.pid"

# adb wait-for-device never returns if the emulator died at once (no space, no KVM), so watch the process.
emulator_died() {
  if ! kill -0 "$(cat "$OUT/emulator.pid")" 2>/dev/null; then
    echo "ERROR: the emulator exited; its log:" >&2
    tail -30 "$OUT/emulator.log" >&2
    exit 1
  fi
}
log "waiting for the emulator to connect"
for _ in $(seq 1 90); do
  emulator_died
  [[ "$("$ADB" get-state 2>/dev/null || true)" == "device" ]] && break
  sleep 2
done
[[ "$("$ADB" get-state 2>/dev/null || true)" == "device" ]] || { echo "ERROR: the emulator didn't connect in 3 minutes" >&2; tail -30 "$OUT/emulator.log" >&2; exit 1; }
log "waiting for Android to boot"
for _ in $(seq 1 240); do
  emulator_died
  [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] && break
  sleep 2
done
[[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] || { echo "ERROR: Android didn't finish booting in 8 minutes" >&2; exit 1; }

# A 2-core emulator that has just booted makes system apps (launcher, Google Play services, Settings) miss
# their deadlines, and Android then puts an "isn't responding" dialog over whatever a test is doing (found
# 2026-11-08: a run's installs all timed out behind one). Don't show error dialogs, and let startup settle.
"$ADB" shell settings put global hide_error_dialogs 1
log "letting the system settle"
sleep 30

# Tests tap and type: no animations, no lock screen, no soft keyboard in the way.
"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0
"$ADB" shell settings put secure show_ime_with_hard_keyboard 0
"$ADB" shell input keyevent 82 || true
"$ADB" shell wm dismiss-keyguard || true
"$ADB" shell input keyevent KEYCODE_HOME || true
log "ready: $("$ADB" shell getprop ro.build.version.release | tr -d '\r') (API $("$ADB" shell getprop ro.build.version.sdk | tr -d '\r'))"
