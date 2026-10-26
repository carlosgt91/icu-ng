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
