#!/usr/bin/env bash
# Records a live ICU NG stream from the video server and checks its KLV.
#
#   tools/klv-check/validate-stream.sh <url> [seconds]
#   tools/klv-check/validate-stream.sh --file capture.ts
#
# <url> is where the server serves the stream as MPEG-TS with KLV. With a
# MediaMTX-based server (tak-video-restreamer) that is its SRT read URL:
#
#   "srt://<server>:8890?streamid=read:<path>"
#
# (add ":<user>:<pass>" after the path if reading needs credentials). The
# phone publishes KLV over RTSP as an RFC 6597 track; MediaMTX remuxes it
# into the TS it serves over SRT. ffmpeg's own RTSP client can't read RFC
# 6597 tracks, so an rtsp:// URL here records video only.
#
# Two independent checks: jMISB (Java) on every KLV packet, then the
# stanag4609 verifier (Python) on the whole capture: transport, PTS/PCR
# cadence, ST 0604 time stamps, MISP codec profile/level and the ST 0902
# minimum metadata set.
#
# Needs ffmpeg/ffprobe, Java 17+ and Python 3.10+ with venv. jMISB (MIT)
# comes from Maven Central and stanag4609 (MIT) from PyPI, both into
# ~/.cache/icung-klv-check on first use, pinned to exact versions and
# SHA-256 sums. The capture is kept in a temp dir and its path printed;
# nothing is uploaded anywhere.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
cache="${XDG_CACHE_HOME:-$HOME/.cache}/icung-klv-check"
maven="https://repo1.maven.org/maven2"

# path sha256
jars=(
  "org/jmisb/jmisb-api/1.12.0/jmisb-api-1.12.0.jar 1199e2b5baec5523207b720803fa1497808d867cfa08327ef9c5253a08473ebc"
  "org/jmisb/jmisb-core-common/1.12.0/jmisb-core-common-1.12.0.jar f66a8fb79ec628fb3579941afbce202c4251d91d6693005a54e86a606ea948fb"
  "org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar d3ef575e3e4979678dc01bf1dcce51021493b4d11fb7f1be8ad982877c16a1c0"
)

stanag4609_sha256="0e12c000623522a374474c982ed928861d4d6811fbc0db74e88b40b80b3e92b0"

usage() {
  sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'
  exit 64
}

fetch_jars() {
  mkdir -p "$cache"
  for entry in "${jars[@]}"; do
    read -r path sum <<< "$entry"
    local file="$cache/$(basename "$path")"
    if [[ ! -f "$file" ]] || ! echo "$sum  $file" | sha256sum -c --quiet - 2>/dev/null; then
      curl -fsSL -o "$file.tmp" "$maven/$path"
      if ! echo "$sum  $file.tmp" | sha256sum -c --quiet -; then
        rm -f "$file.tmp"
        echo "checksum mismatch for $(basename "$path"), not using it" >&2
        exit 1
      fi
      mv "$file.tmp" "$file"
    fi
  done
}

fetch_verifier() {
  local venv="$cache/stanag4609-0.4.0"
  if [[ ! -x "$venv/bin/stanag4609-verify" ]]; then
    python3 -m venv "$venv"
    printf 'stanag4609==0.4.0 --hash=sha256:%s\n' "$stanag4609_sha256" > "$cache/requirements.txt"
    "$venv/bin/python" -I -m pip install -q --require-hashes --only-binary :all: \
      -r "$cache/requirements.txt"
  fi
  verifier="$venv/bin/stanag4609-verify"
}

# Prints a URL with any user:pass stripped, for logs.
redact() {
  sed -E 's#(://)[^@/]*@#\1***@#; s#(streamid=[^:&]*:[^:&]*):[^&]*#\1:***#' <<< "$1"
}

[[ $# -ge 1 ]] || usage
command -v ffmpeg >/dev/null || { echo "ffmpeg not found" >&2; exit 1; }
command -v java >/dev/null || { echo "java not found" >&2; exit 1; }

if [[ "$1" == "--file" ]]; then
  [[ $# -eq 2 ]] || usage
  capture="$2"
else
  url="$1"
  seconds="${2:-30}"
  work="$(mktemp -d -t icung-klv-XXXXXX)"
  capture="$work/capture.ts"
  echo "recording ${seconds}s from $(redact "$url")"
  ffmpeg -hide_banner -loglevel error -y -t "$seconds" -i "$url" \
    -map 0 -c copy -f mpegts "$capture"
  echo "capture: $capture"
fi

echo
echo "== streams"
ffprobe -hide_banner -loglevel error -show_entries stream=index,codec_type,codec_name,profile,width,height,avg_frame_rate \
  -of compact=p=0 "$capture" || true
if ! ffprobe -hide_banner -loglevel error -select_streams d -show_entries stream=codec_name \
    -of csv=p=0 "$capture" | grep -q klv; then
  echo "no KLV data stream in the capture" >&2
  exit 2
fi

echo
echo "== KLV (jMISB 1.12)"
fetch_jars
status=0
java -cp "$cache/*" "$here/KlvCheck.java" "$capture" 2>/dev/null || status=$?

echo
echo "== STANAG 4609 / MISB (stanag4609 0.4.0, ST 0902 MISMMS profile)"
fetch_verifier
report="$(mktemp -t icung-verify-XXXXXX.txt)"
vstatus=0
"$verifier" --profile mismms "$capture" > "$report" || vstatus=$?
grep -v '^[[:space:]]*\[PASS\]' "$report" || true
rm -f "$report"
[[ $status -ne 0 ]] || status=$vstatus
echo "(passing checks hidden; full report: $verifier --format html \"$capture\")"
exit "$status"
