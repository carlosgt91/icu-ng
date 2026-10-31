<div align="center">

# ATAK-Plugin — ICU NG (Next Generation)

[![ATAK-CIV](https://img.shields.io/badge/ATAK--CIV-5.8%2B-blue.svg)](https://tak.gov)
[![Standards](https://img.shields.io/badge/Standards-MISB%20ST%200601%20%7C%20ST%200902%20%7C%20STANAG%204609-success.svg)](https://gwg.nga.mil/)
[![Protokolle](https://img.shields.io/badge/Protokolle-RTSP%20%7C%20RTSPS%20%7C%20SRT-orange.svg)](#sicherheit-und-ato-vorgaben)
[![Release](https://img.shields.io/badge/Release-v1.1.9-brightgreen.svg)](https://github.com/carlosgt91/icu-ng/releases/tag/v1.1.9)
[![Lizenz](https://img.shields.io/badge/Lizenz-Apache%202.0-lightgrey.svg)](LICENSE)

**Professionelles, latenzarmes taktisches Video-Streaming-Plugin für ATAK-CIV.**  
*Native Kameraübertragung mit STANAG 4609 / MISB KLV-Metadaten, dynamischem SA-Sensorkegel (FOV) auf der Karte und Hintergrundaufzeichnung.*

---

[English](README.md) • [Español](README.es.md) • [Deutsch](README.de.md) • [Українська](README.uk.md) • [Français](README.fr.md) • [Português](README.pt.md)

</div>

---

## 📌 Übersicht

**ICU NG** ist ein moderner, direkter Ersatz für das ältere Dienstprogramm *TAK ICU*, das als vollwertiges Plugin direkt in ATAK integriert ist. Es ermöglicht einem Bediener im Einsatz, Live-Video und -Audio von den internen Gerätekameras (Rückseite/Vorderseite) oder externen USB-Quellen (UVC) direkt an **RTSP-, RTSPS- (TLS) oder SRT-**Videoserver zu übertragen.

Streams werden mit dem Rufzeichen (*Callsign*) des Bedieners benannt und automatisch über ATAKs native Video-Verbindungsverwaltung (`<__video>` CoT) angekündigt. Der aktive Sichtbereich der Kamera wird dynamisch auf den Karten der Teammitglieder dargestellt – inklusive Echtzeit-Sensorkegel und Projektion der Bildecken auf das Gelände.

```
       [ Gerätekamera / USB UVC ]
                   │
                   ▼
       ┌───────────────────────┐
       │     ICU NG Plugin     │ ◄─── Telemetrie (Rotationsvektor, GPS, Gelände)
       │ (oder Begleit-App)    │ ───► MISB ST 0601 / ST 0902 KLV-Injektion (2 Hz)
       └───────────────────────┘
                   │
    ┌──────────────┴──────────────┐
    ▼ (RTSP / RTSPS / SRT)        ▼ (CoT Video-Ankündigung)
┌───────────────────────┐   ┌───────────────────────┐
│ MediaMTX / Restreamer │   │      TAK Server       │
└───────────────────────┘   └───────────────────────┘
            │                           │
            └──────────────┬────────────┘
                           ▼
             [ Remote ATAK-Clients / GefStd ]
         (Streams mit projizierter Kartenfläche)
```

---

## 🚀 Downloads (Aktuelle Version: v1.1.9)

Vorkompilierte APKs und Einreichungspakete stehen unter [GitHub Releases](https://github.com/carlosgt91/icu-ng/releases/tag/v1.1.9) bereit:

| Paket | Datei | Beschreibung |
| :--- | :--- | :--- |
| **ATAK-Plugin** | [ATAK-Plugin-IcuNg-1.1.9-813b18c6-5.8.0-civ-release.apk](https://github.com/carlosgt91/icu-ng/releases/download/v1.1.9/ATAK-Plugin-IcuNg-1.1.9-813b18c6-5.8.0-civ-release.apk) | Natives Plugin-Paket für ATAK-CIV 5.8+ |
| **Begleit-App** | [ICU-NG-Capture-1.1.9-release.apk](https://github.com/carlosgt91/icu-ng/releases/download/v1.1.9/ICU-NG-Capture-1.1.9-release.apk) | Eigenständiger Hintergrunddienst für Streaming bei ausgeschaltetem Bildschirm |

### SHA-256 Prüfsummen

```text
540a5438499391b80c161145b147224888935620cce2320d7ffa525dca632ef3  ATAK-Plugin-IcuNg-1.1.9-813b18c6-5.8.0-civ-release.apk
214f14cf95a484510dad0727bd7a3c6e602ffb329b4899334cb07e1748a770e8  ICU-NG-Capture-1.1.9-release.apk
b2172a25a484193233ac8d91c9f09fa1f430a94dfb0e9ff7138a687f97cb4808  IcuNg-1.1.9-5.8.0.zip
```

---

## 🎯 Hauptfunktionen

### 1. Nahtlose taktische Benutzeroberfläche
* **REC-Taste auf der Karte:** Antippen zum Starten/Stoppen mit gespeicherten Voreinstellungen. Langes Drücken öffnet das Schnelleinstellungsfeld; Ziehen verschiebt es am Bildschirmrand.
* **Radialmenü-Integration:** Starten und Überwachen von Live-Streams direkt aus dem Radialmenü des eigenen Kartenmarkers.
* **QR-Code-Konfiguration:** Sofortiges Einlesen von Server-URLs, Zugangsdaten und SRT-Passphrasen über die Kamera.

### 2. NATO / STANAG 4609 & MISB KLV-Konformität
* **MISB ST 0601 & ST 0902:** Erzeugt standardkonforme KLV-Metadatenpakete mit **2 Hz** (entspricht ST 1402):
  * Plattformposition und ellipsoidische Höhe (Tags 13, 14, 15).
  * Kamera-Azimut, -Elevation und relative Rollung (Tags 5, 6, 7, 20).
  * Horizontales Sichtfeld (Tag 16) und Zielbreite (Tag 22).
  * Geländeprojektierter Bildmittelpunkt und Bildeckkoordinaten (Tags 23–26).
  * Sicherheitsmetadaten (MISB ST 0102, Tag 48) und Standard-Missions-ID (Tag 3).
  * MIIS Core Identifier (MISB ST 1204, Tag 94) mit stabiler UUID v5 für Sensor-/Plattform-Tracking.
* **MISB ST 0604 Präzisionszeitstempel:** Als SEI-Header in jedem H.264/H.265-Frame eingebettet.
* **Geländeprojektion:** Empfangende Systeme können das Live-Videobild direkt auf ATAK-Höhenkarten einblenden.

### 3. Dynamischer Sensor-Sichtfeldkegel (FOV)
* Im Gegensatz zu herkömmlichen Plugins, die statische Kreiskegel basierend auf dem GPS-Kurs zeichnen, berechnet ICU NG den **tatsächlichen Kamera-Ausrichtungsvektor**.
* Überträgt die Echtzeit-`<sensor>`-Geometrie in Cursor on Target (`REPORT_LOCATION`), sodass Teammitglieder das exakte Sichtfeld und die Bodenabdeckung sehen.

### 4. Hintergrundübertragung bei ausgeschaltetem Bildschirm
* Unter Android wird der Kamerazugriff im Hintergrund entzogen, sobald das Display sperrt.
* ICU NG verbindet sich nahtlos mit **ICU NG Capture**, einem Begleitdienst (`capture/`) mit deklariertem `camera|microphone`-Vordergrunddienst.
* Ermöglicht kontinuierliches Senden bei gesperrtem Bildschirm zur Schonung des Akkus und zur Wahrung der Tarnung.

### 5. Multiprotokoll & Hochleistungscodierung
* Unterstützte Protokolle: **RTSP** (RFC 2326), **RTSPS** (TLS-verschlüsselt) und **SRT** (Secure Reliable Transport mit AES-Verschlüsselung und Passphrase).
* Codecs: **H.264 (AVC) Main Profile** und **H.265 (HEVC)**.
* Konstante Framerate (CFR) und konfigurierbare adaptive Bitrate.
* Volle Unterstützung interner Kameras und externer **USB UVC / OTG**-Optiken oder Körperkameras.

---

## 🔒 Sicherheit und ATO-Vorgaben (Authority to Operate)

Entwickelt für den Einsatz in anspruchsvollen militärischen und behördlichen Netzwerken:

| Protokoll | Standardport | Richtung | Verschlüsselung / Transport | Zweck |
| :--- | :--- | :--- | :--- | :--- |
| **RTSP** | `8554` | Nur ausgehend | TCP unverschlüsselt | Herkömmlicher Video-Ingest |
| **RTSPS** | `8555` | Nur ausgehend | TLS über TCP | Sicheres Behörden-Streaming |
| **SRT** | `8890` | Nur ausgehend | UDP / AES-128/256 | Robuste Übertragung bei geringer Latenz |

* **Keine eingehenden Ports:** Keine offenen Listening-Sockets oder Angriffsflächen auf dem mobilen Endgerät (EUD).
* **Vollständig Air-Gapped fähig:** Keine Cloud-Abhängigkeiten, keine externe Telemetrie, keine unautorisierten Netzwerkverbindungen.
* **Lokale IPC-Sicherheit:** Die Kommunikation zwischen Plugin und Begleit-App ist über AIDL strikt an ATAK-Paketsignaturen gebunden.

---

## 🛠️ Build & Entwicklung

### Voraussetzungen
* Java 17 (OpenJDK / Temurin)
* Android SDK (API 34–36, Build Tools 34.0.0+)
* ATAK-CIV SDK (5.8.0.7+)

### Kompilierung
1. SDK-Pfad in `local.properties` festlegen:
   ```properties
   sdk.path=/pfad/zu/ATAK-CIV-5.8.0.7-SDK
   ```
2. Debug- oder Release-APKs erstellen:
   ```bash
   ./gradlew assembleCivDebug
   ./gradlew assembleCivRelease
   ```
   *(Kompilierung der Begleit-App: `./gradlew :capture:assembleRelease`)*.

---

## 📄 Lizenz & Danksagungen

* Veröffentlicht unter der **[Apache-Lizenz 2.0](LICENSE)**.
* Streaming-Pipeline basiert auf modifiziertem [RootEncoder](https://github.com/pedroSG94/RootEncoder) (Apache-2.0) mit RFC 6597 KLV-Spurinjektion (`rootencoder-rtsp/`).
* USB-Kameratreiber via [UVCAndroid](https://github.com/herohan/UVCAndroid) (Apache-2.0).
* QR-Dekodierung via [ZXing](https://github.com/zxing/zxing) (Apache-2.0).

---

<div align="center">
  <sub>Betreut von <b><a href="https://github.com/carlosgt91">carlosgt91</a></b> • Verifiziert für das ATAK-CIV Ökosystem</sub>
</div>
