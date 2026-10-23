#!/usr/bin/env bash
# Builds ICU NG Capture signed with its release key, on the host (the key is
# not visible inside the atak-dev build container, on purpose).
#
#   tools/build-capture-release.sh            -> capture/build/outputs/apk/release/
#
# Needs ~/.config/atak-plugins/keys/icung-capture.properties (see
# capture/build.gradle) and an unpacked ATAK SDK (only because the gradle
# project also configures the plugin module).
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ATAK_SDK:-$(ls -d "$HOME"/atak-sdk/ATAK-CIV-* | sort -V | tail -1)}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
./gradlew -q -Psdk.path="$SDK" -Ptakdev.plugin="$SDK/atak-gradle-takdev.jar" :capture:assembleRelease
APK=capture/build/outputs/apk/release/capture-release.apk
"$ANDROID_HOME"/build-tools/*/apksigner verify --print-certs "$APK" | grep -E "DN|SHA-256" | head -2
echo "$APK"
