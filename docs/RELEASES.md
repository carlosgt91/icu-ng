# ICU NG — tak.gov submissions

One entry per submitted version: what went in, what tak.gov returned, and why the
next version exists. A resubmission is always a new version.

| Version | versionCode | Submitted | tak.gov result |
|---|---|---|---|
| 1.0   | — | 2026-10-23 | Signed (2026-10-24) |
| 1.1   | — | 2026-10-24 | No return filed |
| 1.1.1 | 101015080 | 2026-10-25 | **Build failed** (returned 2026-10-25) |
| 1.1.2 | 101025080 | 2026-10-26 | Superceded before submission |
| 1.1.3 | 101035080 | 2026-10-26 | Superceded before submission |
| 1.1.4 | 101045080 | 2026-10-26 | Superceded |
| 1.1.5 | 101055080 | 2026-10-26 | Pending |
| 1.1.6 | 101065080 | 2026-10-28 | Signed (2026-10-28) |
| 1.1.7 | 101075080 | — | Not yet submitted |
| 1.1.8 | 101085080 | 2026-10-31 | Signed (2026-10-31) |
| 1.1.9 | 101095080 | 2026-10-31 | Signed (2026-10-31) |
| 1.2.0 | 102005080 | 2026-11-11 | Signed (2026-11-11), **not distributed** |
| 1.2.1 | 102015080 | 2026-11-11 | Signed (2026-11-11) |
| 1.2.2 | 102025080 | 2026-11-12 | Signed (2026-11-12) |

## 1.2.2 — signed 2026-11-12, released 2026-11-13

- The main page is one row (camera, Quality, Settings, Stream/Stop), so the
  preview shows. Protocol, URL, QR and the KLV marking move to a *Settings*
  page ("Connection and KLV"), like *Quality*. The "Camera" caption is gone:
  the spinner already says Back/Front/USB.
- Manual (6 languages): new panel screenshot and a "Connection and KLV" slide.
- Lint clean: `SelfVideoDetailHandler` no longer uses `java.util.function.Supplier`
  (API 24, the plugin's minSdk is 21); camera and microphone declared optional.
- Fortify: two comments reworded (Password in Comment). The other three findings
  are required by the standards: SHA-1 for the RFC 4122 v5 UUIDs of MISB ST 1204,
  and HMAC-SHA1 / AES-ECB building AES-CM in RootEncoder's SRTP (RFC 3711).

## 1.2.1 — signed 2026-11-11, released 2026-11-12

1.2.0 without ICU NG Capture: stopping a stream that was recording closed ATAK.
RootEncoder 2.8.1's recorder calls `kotlinx.coroutines.BuildersKt.runBlockingK`,
and inside ATAK's process ATAK's older kotlinx-coroutines is the one loaded
(`NoSuchMethodError`). The Capture app has its own copy and was fine (S21 test,
165 s MP4 through a network cut). 1.2.1 records with Capture only: without it
the box is off and says it needs the app. Found on the signed 1.2.0, S21 with
official ATAK 5.8.0.5, 2026-11-11.

Signed 1.2.1 tested on the S21 (ATAK 5.8.0.5), 2026-11-12: without Capture the
record box is off and stopping a stream leaves ATAK running; with Capture 1.2.1
the MP4 records, the Video Feed Manager entry goes active/inactive (HTTP 200),
and a 20 s Wi-Fi + data cut reconnects in 3 s with the MP4 intact (204 s, no gaps).

## 1.2.0 — signed 2026-11-11, not distributed

- The plugin retries a dropped connection by itself while ATAK is in front
  (2, 4, 8, 16 s, then every 30 s) instead of stopping in error. The Capture app
  already did.
- Settings can come from a `.pref` file (Import Manager, data package, MDM) and
  apply without restarting ATAK; a bad value only resets that setting. See
  `docs/PREF_FILE.md` and `docs/icu-ng-settings-template.pref`.
- Optional local MP4 recording of every stream (Movies/ICU NG), in the plugin and
  in the Capture app. It survives dropped connections: only the network side
  reconnects. No KLV in the MP4.
- The stream is listed in the TAK Server's Video Feed Manager (POST /Marti/vcm with
  the connection's client certificate), marked inactive on stop. On by default.

## 1.1.1 — rejected

Contents: 1.1 plus the synthetic CFR clock for WinTAK (`ba81d48`) and the RTSP
8554 announcement in the CoT when streaming over RTSPS (`da51164`). The version was
bumped (`f1d6c5e`) without recording why.

tak.gov's build failed at `:app:mergeCivReleaseNativeLibs`:

```
Could not resolve com.github.pedroSG94.RootEncoder:rtsp:2.8.1.
  > No matching variant of project :rootencoder-rtsp was found ... No variants exist.
```

**Cause:** the submission zip did not contain `rootencoder-rtsp/`.
`settings.gradle` includes it, so on tak.gov it was an empty project. The zip was
built without `SUBMISSION_EXTRA_PATHS=rootencoder-rtsp`, and without the
clean-extract build that would have caught it.

Fortify also flagged two items (no APK, so they did not block on their own):

- *Hardcoded Password* (critical), in `StreamControllerConnectionEntryTest`: a
  `publish:password@` URL. The test also carried a real server IP and stream
  name.
- *Password in Comment* (low), in `StreamSettings.urlHasCredentials`.

## 1.1.2

- The zip includes `rootencoder-rtsp/` again. `submission-zip.sh` now adds every
  module that `settings.gradle` includes and fails the zip when one is missing.
- The tests use `video.example.com` and build the userinfo without a literal
  password; the comment has been reworded.
- Same functionality as 1.1.1.

## 1.1.3

- Wraps the CoT video announcement in `<__video>` with canonical `url` (`?tcp`),
  `uid`, and `sensor` attributes, matching OpenTAK_ICU and UAS Tool.
- Sets `rtspReliable = 1` in `ConnectionEntry` and builds `ConnectionEntry` as a child
  of `<__video>`.
- Fixes receiving ATAK clients showing disabled/unresponsive 'Play' (Reproducir)
  buttons on the map marker when streaming.

## 1.1.4

- Injects `<__video>` directly into ATAK's SA/PPLI beacon using `CotMapComponent.getInstance().addAdditionalDetail()`
  and broadcasts `REPORT_LOCATION` on stream start/stop.
  (In ATAK, `CotDetailManager` only handles external drawing layers and is never called for the self marker).
- Guarantees immediate CoT broadcast with `<__video>` upon streaming without waiting for periodic location cycle.

## 1.1.5

- Adds dedicated Quality settings page with back navigation, hiding main controls for a cleaner pane interface.
- Adds H.265 (HEVC) video codec selection alongside standard H.264 (AVC) to halve bandwidth usage on degraded/tactical radio links.
- Adds 250 kbps ultra-low bitrate preset for narrow-band MANET/SATCOM environments.
- Fixes KLV telemetry recovery when switching cameras mid-stream from USB to phone camera.

## 1.1.6

- KLV carries the frame's four ground corners (ST 0601 tags 26-33 as offsets
  from the frame center, and tags 82-89 as full lat/lon), so a receiving ATAK
  can drape the video on its map. Corners come from the phone's attitude and
  the camera's field of view (`Footprint`); a corner ray that does not reach
  the ground leaves the tags out.

## 1.1.7

STANAG 4609 (MISB) compliance, and the three problems tak-video-restreamer's
stream check reported on 1.1.6 (VFR, H.264 High, KLV codec "unknown").

- KLV carries the MISB ST 0102 Security Local Set (tag 48: classification,
  GENC three-letter classifying/object country, version 12) and an optional
  Mission ID (tag 3). Classification, country and mission are set under the
  KLV checkbox; the country defaults to the phone's region and must be three
  letters for a stream with KLV to start.
- Every H.264/H.265 frame carries a MISB ST 0604 Precision Time Stamp
  (`MISPmicrosectime` user-data SEI) when KLV is on, so receivers can match
  picture and metadata.
- Constant frame rate: the GL pipeline repeats the last picture when the
  camera drops one (`setForceRender`) and caps the rate (`forceFpsLimit`);
  the RTP CFR grid now uses the chosen fps instead of a fixed 30 (15/24 fps
  streams were snapped to a 30 fps grid and looked variable).
- H.264 is encoded in Main profile (level by resolution) instead of the
  encoder's default High; if an encoder refuses it, its own choice is kept.
- Same encoder changes in ICU NG Capture.
- The restreamer's "KLV track not tagged as KLV (codec: unknown)" is ffmpeg
  not mapping RFC 6597 `smpte336m` back to its KLV codec; nothing to change
  in the publisher.
- Not tested on a device before submission: verify with the restreamer's
  stream check and a KLV validator (jMISB) on the returned build.

## 1.1.8

Full MISB ST 0902 minimum metadata set, a sensor cone that follows the
camera and reaches other ATAK users, and a validator for the KLV that
actually reaches the server.

- KLV adds the ST 0902 (MISMMS) items 1.1.7 lacked: platform pitch and roll
  (tags 6/7, 0: the platform is the operator, held level; camera pitch and
  twist stay sensor-relative), sensor relative roll (tag 20, the picture's
  real twist from the rotation sensor), target width (tag 22, frame width at
  the frame center) and the MISB ST 1204 MIIS Core Identifier (tag 94:
  virtual sensor and platform IDs, name-based v5 UUIDs from the ATAK device
  UID and the camera, so they stay the same across sessions).
- Generated packets pass jMISB 1.12 and the stanag4609 0.4.0 verifier
  (strict ST 0601, ST 0902 MISMMS with the ST 0102 security items) with no
  issues. H.264 Main at levels 3/3.1/4 is within the MISP-approved levels.
- Sensor cone: it used the GPS track heading, a fixed 60 degrees and 200 m.
  It now takes the camera's true azimuth, the lens's horizontal field of
  view and the ground distance to the frame center from the KLV telemetry,
  falling back to the old cone without KLV (SRT, USB camera, KLV off).
- The cone was never in the self SA: ATAK's SensorDetailHandler only writes
  `<sensor>` for markers with the `sensorFov` meta value, and only from the
  meta values addFovToMap sets (SensorFOV.setMetrics does not). The self
  marker now carries the value while streaming and every update goes
  through addFovToMap (checked against the 5.8 SDK bytecode).
- `tools/klv-check/validate-stream.sh` (not in the submission zip): records
  the stream from the server over SRT (MediaMTX remuxes the RFC 6597 track
  into MPEG-TS) and checks it with jMISB per packet and the stanag4609
  verifier on the whole capture. Versions and SHA-256 sums pinned.
- Manual (6 languages): the cone, and the STANAG 4609 line that 1.1.7 only
  added in English and Ukrainian.
- Not tested on a device before submission: check that another ATAK sees
  the cone and that it goes when streaming stops, and run
  `validate-stream.sh` on a live stream.

## 1.1.9

Fixes from the first live check of the signed 1.1.8: an S21 streaming RTSP
to tak-video-restreamer, recorded over SRT with `tools/klv-check`.

What 1.1.8 got right on the device: jMISB parsed every packet (0 errors,
time stamps increasing, all ST 0902 tags present), ST 0604 time stamps on
1743 of 1743 frames, H.264 Main level 3.0, frame corners and the sensor cone
on another ATAK as intended. What the ST 0902 / ST 1402 checks found:

- Mission ID (tag 3) missing when the field is left empty; ST 0902 requires
  it. Now it defaults to the callsign and the UTC date the stream starts
  (e.g. `ALFA-1-20261031`).
- Sensor relative roll (tag 20) missing whenever the camera looked steeply
  down (-85 degrees and below in the test), the very case where the video
  drapes on the map: the twist was taken from the vertical parts of the
  image axes, which vanish there. It is now measured against the camera
  axis's own level frame (`Footprint.roll`), undefined only within about a
  degree of vertical, where the last value is kept.
- KLV every second; ST 1402 allows at most 0.7 s between metadata time
  stamps. Now twice per second.
- Manual: 1.1.8 left the old STANAG 4609 line next to the new one in the
  Spanish, German, French and Portuguese manuals; the KLV rate and the
  mission default are updated in all six.

Found but not in the plugin: the restreamer's MPEG-TS (MediaMTX, and ffmpeg
alike) carries KLV as stream_type 0x06 with PTS and stream_id 0xFC, which
ST 1402 allows neither as asynchronous (0xBD, no PTS) nor as synchronous
(0x15) carriage, and flags its AAC as MPEG-4 ADTS under stream_type 0x0F.
A NATO-grade 4609 output needs a conformant remuxer on the server.

