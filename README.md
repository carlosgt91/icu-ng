<div align="center">

# ATAK Plugin — ICU NG (Next Generation)

[![ATAK-CIV](https://img.shields.io/badge/ATAK--CIV-5.8%2B-blue.svg)](https://tak.gov)
[![Standards](https://img.shields.io/badge/Standards-MISB%20ST%200601%20%7C%20ST%200902%20%7C%20STANAG%204609-success.svg)](https://gwg.nga.mil/)
[![Protocols](https://img.shields.io/badge/Protocols-RTSP%20%7C%20RTSPS%20%7C%20SRT-orange.svg)](#protocols-and-security)
[![Release](https://img.shields.io/badge/Release-v1.1.9-brightgreen.svg)](https://github.com/carlosgt91/icu-ng/releases/tag/v1.1.9)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](LICENSE)

**Professional, low-latency tactical video streaming plugin for ATAK-CIV.**  
*Native camera broadcast with STANAG 4609 / MISB KLV metadata, dynamic SA sensor FOV footprint, and background capture support.*

---

[English](README.md) • [Español](README.es.md) • [Deutsch](README.de.md) • [Українська](README.uk.md) • [Français](README.fr.md) • [Português](README.pt.md)

</div>

---

## 📌 Overview

**ICU NG** is a modern, drop-in replacement for the legacy *TAK ICU* utility, living directly inside ATAK as a first-class plugin. It allows an operator on the ground to broadcast real-time video and audio from internal device cameras (back/front) or external USB (UVC) feeds directly to **RTSP, RTSPS (TLS), or SRT** video servers.

Streams are tagged with the operator's callsign and automatically announced via ATAK's native Video Connection Manager (`<__video>` CoT). The active camera footprint is dynamically drawn on team members' maps, complete with real-time sensor FOV cones and video corner projection.

```
       [ Device Camera / USB UVC ]
                   │
                   ▼
       ┌───────────────────────┐
       │     ICU NG Plugin     │ ◄─── Telemetry (Rotation Vector, GPS, Terrain)
       │ (or Capture Companion)│ ───► MISB ST 0601 / ST 0902 KLV Injection (2 Hz)
       └───────────────────────┘
                   │
    ┌──────────────┴──────────────┐
    ▼ (RTSP / RTSPS / SRT)        ▼ (CoT Video Announcement)
┌───────────────────────┐   ┌───────────────────────┐
│ MediaMTX / Restreamer │   │      TAK Server       │
└───────────────────────┘   └───────────────────────┘
            │                           │
            └──────────────┬────────────┘
                           ▼
              [ Remote ATAK Clients / TOC ]
           (Streams with Draped Map Footprint)
```

---

## 🚀 Downloads (Latest Release: v1.1.9)

Pre-built APKs are available under [GitHub Releases](https://github.com/carlosgt91/icu-ng/releases/tag/v1.1.9):

| Package | File | Description |
| :--- | :--- | :--- |
| **ATAK Plugin** | [ATAK-Plugin-IcuNg-1.1.9-813b18c6-5.8.0-civ-release.apk](https://github.com/carlosgt91/icu-ng/releases/download/v1.1.9/ATAK-Plugin-IcuNg-1.1.9-813b18c6-5.8.0-civ-release.apk) | Native ATAK-CIV 5.8+ plugin package |
| **Companion App** | [ICU-NG-Capture-1.1.9-release.apk](https://github.com/carlosgt91/icu-ng/releases/download/v1.1.9/ICU-NG-Capture-1.1.9-release.apk) | Standalone background service for screen-off streaming |

### SHA-256 Checksums

```text
540a5438499391b80c161145b147224888935620cce2320d7ffa525dca632ef3  ATAK-Plugin-IcuNg-1.1.9-813b18c6-5.8.0-civ-release.apk
214f14cf95a484510dad0727bd7a3c6e602ffb329b4899334cb07e1748a770e8  ICU-NG-Capture-1.1.9-release.apk
```

---

## 🎯 Key Capabilities

### 1. Zero-Friction Tactical UI
* **On-Map REC Button:** Tap to start/stop broadcasting using saved presets. Long-press to open the quick settings overlay; drag to reposition along the screen edge.
* **Radial Menu Integration:** Start and monitor live streams directly from the operator's self-marker radial menu.
* **QR Code Configuration:** Instantly provision stream URLs, credentials, and SRT passphrases via camera QR scans.

### 2. NATO / STANAG 4609 & MISB KLV Compliance
* **MISB ST 0601 & ST 0902:** Generates conformant KLV metadata packets at **2 Hz** (ST 1402 compliant):
  * Platform Position & Ellipsoid Altitude (Tags 13, 14, 15).
  * Camera Azimuth, Elevation, and Relative Roll (Tags 5, 6, 7, 20).
  * Horizontal Field of View (Tag 16) & Target Width (Tag 22).
  * Terrain-Projected Frame Center and Corner Ground Coordinates (Tags 23–26).
  * Security Local Set (MISB ST 0102, Tag 48) & Default Mission ID (Tag 3).
  * MIIS Core Identifier (MISB ST 1204, Tag 94) with stable UUID v5 for sensor/platform tracking.
* **MISB ST 0604 Precision Time Stamps:** Injected as SEI headers on every H.264/H.265 frame.
* **Video Drape / Terrain Mapping:** Receiving systems can project the live camera perspective directly onto ATAK elevation maps.

### 3. Dynamic Sensor FOV Footprint
* Unlike legacy plugins that render static circular cones based on GPS heading, ICU NG computes the **true camera orientation vector**.
* Injects real-time `<sensor>` FOV geometry into Cursor on Target (`REPORT_LOCATION`), showing team members the exact viewing frustum and ground intersection.

### 4. Background & Screen-Off Streaming
* In Android, background camera access is revoked when the display turns off.
* ICU NG bridges seamlessly with **ICU NG Capture**, a dedicated companion service (`capture/`) holding a compliant `camera|microphone` foreground service.
* Keep streaming while the screen is locked, preserving battery life and operational discipline during covert operations.

### 5. Multi-Protocol & High-Performance Encoding
* Protocols supported: **RTSP** (RFC 2326), **RTSPS** (TLS encrypted), and **SRT** (Secure Reliable Transport with AES encryption and passphrase).
* Codecs: **H.264 (AVC) Main Profile** and **H.265 (HEVC)**.
* Constant Frame Rate (CFR) clock generator and configurable adaptive bitrate.
* Full compatibility with internal lenses and external **USB UVC / OTG** bodycams or scope optics.


---

## 🔒 Security & ATO (Authority to Operate)

Designed from the ground up for strict military and enterprise IT environments:

| Protocol | Default Port | Direction | Encryption / Transport | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **RTSP** | `8554` | Outbound only | Clear TCP | Legacy video ingest |
| **RTSPS** | `8555` | Outbound only | TLS over TCP | Secure enterprise streaming |
| **SRT** | `8890` | Outbound only | UDP / AES-128/256 | Resilient, low-latency delivery |

* **Zero Inbound Ports:** No listening sockets or open network attack vectors on the mobile EUD.
* **Zero Telemetry / Air-Gapped:** Zero cloud dependencies, zero external tracking, zero unauthorized network calls.
* **Local IPC Security:** Communication between the plugin and the companion app is locked strictly to ATAK signature/package permissions via AIDL.

---

## 🛠️ Building & Development

### Prerequisites
* Java 17 (OpenJDK / Temurin)
* Android SDK (API 34–36, Build Tools 34.0.0+)
* ATAK-CIV SDK (5.8.0.7+)

### Compilation
1. Define your SDK location in `local.properties`:
   ```properties
   sdk.path=/path/to/ATAK-CIV-5.8.0.7-SDK
   ```
2. Build debug and release APKs:
   ```bash
   ./gradlew assembleCivDebug
   ./gradlew assembleCivRelease
   ```
   *(To build the companion app: `./gradlew :capture:assembleRelease`)*.

---

## 📄 License & Attributions

* Licensed under the **[Apache License 2.0](LICENSE)**.
* Streaming pipeline powered by modified [RootEncoder](https://github.com/pedroSG94/RootEncoder) (Apache-2.0) with custom RFC 6597 KLV track injection (`rootencoder-rtsp/`).
* USB Camera support via [UVCAndroid](https://github.com/herohan/UVCAndroid) (Apache-2.0).
* QR decoding via [ZXing](https://github.com/zxing/zxing) (Apache-2.0).

---

<div align="center">
  <sub>Maintained by <b><a href="https://github.com/carlosgt91">carlosgt91</a></b> • Verified for ATAK-CIV Ecosystem</sub>
</div>
