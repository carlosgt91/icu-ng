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
| 1.1.8 | 101085080 | — | Not yet submitted |

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

