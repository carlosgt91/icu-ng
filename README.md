[English](README.md) | [Español](README.es.md) | [Deutsch](README.de.md) | [Українська](README.uk.md) | [Français](README.fr.md) | [Português](README.pt.md)

ATAK Plugin — ICU NG

Camera streaming for ATAK: video and audio from the device's cameras (back,
front or USB/UVC) to an RTSP, RTSPS or SRT video server, with MISB ST 0601 KLV
telemetry, announced on the map so other ATAK users can open it from their
Video list. A replacement for TAK ICU that lives inside ATAK.

User manual: inside ATAK, Settings → Tool Preferences → Specific Tool
Preferences → ICU NG (source: docs/user_manual/).

_________________________________________________________________
DOWNLOADS

Pre-built APKs for the latest release (v1.1.5) are available on [GitHub Releases](https://github.com/carlosgt91/icu-ng/releases/latest):

- 📦 **[ATAK Plugin — ICU NG v1.1.5](https://github.com/carlosgt91/icu-ng/releases/download/v1.1.5/ATAK-Plugin-IcuNg-1.1.5-5.8.0-civ-release.apk)** (civRelease, signed for ATAK-CIV 5.8+)
- 📱 **[Companion App — ICU NG Capture v1.1.5](https://github.com/carlosgt91/icu-ng/releases/download/v1.1.5/ICU-NG-Capture-1.1.5-release.apk)** (Required for background & screen-off streaming)

_________________________________________________________________
PURPOSE AND CAPABILITIES

ICU NG lets an operator stream what the device's camera sees without leaving
ATAK and without setting the stream up by hand in another app each time. The
stream is named with the operator's callsign and announced through ATAK's video
connection manager, so the rest of the team can watch it from ATAK.

- REC button on the map: tap to start/stop with the saved settings, long press
  for the settings pane, long press and drag to move it along the right edge.
- Settings pane with live preview: camera, protocol, server URL (typed or read
  from a QR code), SRT passphrase and encryption, quality (resolution, fps,
  bitrate, adaptive bitrate, microphone).
- MISB ST 0601 KLV over RTSP/RTSPS, once per second: sensor position and
  altitude, camera azimuth and elevation (rotation vector sensor), field of
  view, frame center on the terrain (ATAK elevation data), callsign, and the
  frame's four corners on the ground when the camera looks down, so receivers
  can drape the video on their map.
- Sensor field-of-view cone on the self marker while streaming.
- Stream button on the self marker's radial menu.

Background streaming. A plugin runs inside ATAK's process, and ATAK's only
foreground service is of type "location", so Android stops the plugin's camera
access when the screen goes off. To keep streaming with the screen off, the
camera capture can be done by an optional companion app, ICU NG Capture
(capture/ in the source repository; an ordinary Android app, not part of this
submission), which holds a camera/microphone foreground service. The plugin
remains the whole user interface and drives the app; without the app installed
the plugin streams by itself, in the foreground only.

_________________________________________________________________
STATUS

Version 1.1.5. Verified on ATAK-CIV 5.8.0.7 (developer build): emulator (Android
14) for every feature, including the civRelease build; field-tested on a
Samsung tablet (Android 16) streaming over RTSP to a MediaMTX-based restreamer
through a VPN. Verified on hardware: internal cameras and external USB (UVC) cameras over USB OTG.

Prepared for tak.gov third-party submission.

_________________________________________________________________
POINT OF CONTACTS

carlosgt91
https://github.com/carlosgt91/icu-ng/issues

_________________________________________________________________
PORTS REQUIRED

(This is important for ATO, networking, and other security concerns)

  Outbound only, and only while the operator is streaming, to the one video
  server the operator configured (URL typed in the pane or read from a QR):

  - RTSP:  TCP to the server's RTSP port (MediaMTX default 8554).
  - RTSPS: TCP + TLS to the server's RTSPS port (MediaMTX default 8555).
  - SRT:   UDP to the server's SRT port (MediaMTX default 8890).

  No inbound ports and no listening sockets. No traffic to or from the TAK
  server of its own: the stream is announced as a video connection through
  ATAK's own APIs, carried in the self marker's CoT by ATAK. Nothing is sent
  when not streaming; with no network the plugin simply cannot connect.

  Local IPC with the ICU NG Capture companion app, when installed: activity
  launches (start/stop) and a bound AIDL service (status, preview surface,
  KLV packets). The app accepts calls from the ATAK package only.

_________________________________________________________________
EQUIPMENT REQUIRED

  Android device supported by ATAK-CIV 5.8, with a camera. Camera and
  microphone permissions (ATAK's, or ICU NG Capture's when installed). No
  storage use beyond ATAK's preferences.

_________________________________________________________________
EQUIPMENT SUPPORTED

  The device's own cameras. USB video class (UVC) cameras over USB OTG. A
  video server accepting RTSP/RTSPS/SRT publish, e.g. MediaMTX or
  raytheonbbn/tak-video-restreamer.

_________________________________________________________________
COMPILATION

  Standard ATAK plugin build. Set sdk.path in local.properties to an unpacked
  ATAK CIV SDK, then:

      ./gradlew assembleCivDebug
      ./gradlew assembleCivRelease

  ext.ATAK_VERSION in app/build.gradle selects the ATAK release to target.
  The project has two modules: app (the plugin) and rootencoder-rtsp (see
  below). In the source repository a third one, capture (the companion app),
  is included when present and is not needed to build the plugin.

_________________________________________________________________
DEVELOPER NOTES

  - Streaming is RootEncoder 2.8.1 (pedroSG94, Apache-2.0), StreamBase API.
    rootencoder-rtsp/ is a copy of RootEncoder's rtsp module, modified to
    announce and send a KLV track (RFC 6597, application/smpte336m) next to
    video and audio; see rootencoder-rtsp/NOTICE.md. The app substitutes it
    for the published rtsp artifact.
  - USB cameras: UVCAndroid 1.0.13 (herohan). Its native code calls back into
    Java by name, hence its proguard keep rules.
  - QR decoding: ZXing core.
  - Settings live in ATAK's default preferences (keys icung.*), so the REC
    button can stream without the pane ever having been opened.
  - versionCode is derived from PLUGIN_VERSION and ATAK_VERSION (not from
    git), so builds made from this zip carry a meaningful versionCode.
  - The companion-app interface: app/src/main/aidl and CaptureLink.java on the
    plugin side; the same AIDL files in the app.
