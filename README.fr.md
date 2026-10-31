<div align="center">

# Plugin ATAK — ICU NG (Next Generation)

[![ATAK-CIV](https://img.shields.io/badge/ATAK--CIV-5.8%2B-blue.svg)](https://tak.gov)
[![Standards](https://img.shields.io/badge/Standards-MISB%20ST%200601%20%7C%20ST%200902%20%7C%20STANAG%204609-success.svg)](https://gwg.nga.mil/)
[![Protocoles](https://img.shields.io/badge/Protocoles-RTSP%20%7C%20RTSPS%20%7C%20SRT-orange.svg)](#securite-et-conformite-ato)
[![Version](https://img.shields.io/badge/Version-v1.1.9-brightgreen.svg)](https://github.com/carlosgt91/icu-ng/releases/tag/v1.1.9)
[![Licence](https://img.shields.io/badge/Licence-Apache%202.0-lightgrey.svg)](LICENSE)

**Plugin de streaming vidéo tactique professionnel à faible latence pour ATAK-CIV.**  
*Diffusion native de caméra avec métadonnées STANAG 4609 / MISB KLV, cône de capteur (FOV) dynamique sur carte et capture en arrière-plan.*

---

[English](README.md) • [Español](README.es.md) • [Deutsch](README.de.md) • [Українська](README.uk.md) • [Français](README.fr.md) • [Português](README.pt.md)

</div>

---

## 📌 Présentation Générale

**ICU NG** est un remplaçant moderne et direct de l'outil hérité *TAK ICU*, intégré nativement dans ATAK sous forme de plugin de premier plan. Il permet à un opérateur sur le terrain de diffuser de la vidéo et de l'audio en direct depuis les caméras de son appareil (arrière/avant) ou des flux USB externes (UVC) vers des serveurs vidéo **RTSP, RTSPS (TLS) ou SRT**.

Les flux sont identifiés par l'indicatif d'appel (*callsign*) de l'opérateur et annoncés automatiquement via le gestionnaire de connexions vidéo natif d'ATAK (`<__video>` CoT). L'empreinte active de la caméra est projetée dynamiquement sur la carte des coéquipiers, avec cône de champ de vision en temps réel et projection des coins de l'image sur le terrain.

```
       [ Caméra de l'appareil / USB UVC ]
                       │
                       ▼
           ┌───────────────────────┐
           │     Plugin ICU NG     │ ◄─── Télémétrie (Vecteur de rotation, GPS, terrain)
           │ (ou App Compagne)     │ ───► Injection KLV MISB ST 0601 / ST 0902 (2 Hz)
           └───────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        ▼ (RTSP / RTSPS / SRT)        ▼ (Annonce CoT Vidéo)
    ┌───────────────────────┐   ┌───────────────────────┐
    │ MediaMTX / Restreamer │   │      TAK Server       │
    └───────────────────────┘   └───────────────────────┘
                │                           │
                └──────────────┬────────────┘
                               ▼
                 [ Clients ATAK Distants / PC ]
               (Flux avec projection cartographique)
```

---

## 🚀 Téléchargements (Dernière Version : v1.1.9)

Les fichiers APK précompilés et les archives de soumission sont disponibles sur [GitHub Releases](https://github.com/carlosgt91/icu-ng/releases/tag/v1.1.9) :

| Paquet | Fichier | Description |
| :--- | :--- | :--- |
| **Plugin ATAK** | [ATAK-Plugin-IcuNg-1.1.9-813b18c6-5.8.0-civ-release.apk](https://github.com/carlosgt91/icu-ng/releases/download/v1.1.9/ATAK-Plugin-IcuNg-1.1.9-813b18c6-5.8.0-civ-release.apk) | Paquet de plugin natif pour ATAK-CIV 5.8+ |
| **App Compagne** | [ICU-NG-Capture-1.1.9-release.apk](https://github.com/carlosgt91/icu-ng/releases/download/v1.1.9/ICU-NG-Capture-1.1.9-release.apk) | Service indépendant pour diffusion avec écran éteint |

### Sommes de Contrôle SHA-256

```text
540a5438499391b80c161145b147224888935620cce2320d7ffa525dca632ef3  ATAK-Plugin-IcuNg-1.1.9-813b18c6-5.8.0-civ-release.apk
214f14cf95a484510dad0727bd7a3c6e602ffb329b4899334cb07e1748a770e8  ICU-NG-Capture-1.1.9-release.apk
b2172a25a484193233ac8d91c9f09fa1f430a94dfb0e9ff7138a687f97cb4808  IcuNg-1.1.9-5.8.0.zip
```

---

## 🎯 Fonctionnalités Clés

### 1. Interface Tactique Épurée
* **Bouton REC sur la carte :** Appui simple pour démarrer/arrêter avec les préréglages. Appui long pour ouvrir le panneau de réglages rapides ; glisser pour repositionner le long du bord de l'écran.
* **Intégration au Menu Radial :** Démarrez et contrôlez les flux directement depuis le menu radial de votre propre marqueur.
* **Configuration par Code QR :** Lecture instantanée des URL de serveur, identifiants et clés de chiffrement SRT via la caméra.

### 2. Conformité OTAN / STANAG 4609 & KLV MISB
* **MISB ST 0601 & ST 0902 :** Génère des paquets de métadonnées KLV conformes à **2 Hz** (conforme ST 1402) :
  * Position de la plateforme et altitude ellipsoïdale (Tags 13, 14, 15).
  * Azimut, élévation et roulis relatif de la caméra (Tags 5, 6, 7, 20).
  * Champ de vision horizontal (Tag 16) et largeur de cible (Tag 22).
  * Centre du cadre projeté et coordonnées des coins au sol (Tags 23–26).
  * Données de sécurité (MISB ST 0102, Tag 48) et identifiant de mission par défaut (Tag 3).
  * Identifiant MIIS Core (MISB ST 1204, Tag 94) avec UUID v5 stable pour le suivi de capteur/plateforme.
* **Horodatage de précision MISB ST 0604 :** Intégré dans les en-têtes SEI sur chaque trame H.264/H.265.
* **Projection cartographique du flux :** Les récepteurs peuvent draper la perspective vidéo directement sur les cartes topographiques d'ATAK.

### 3. Cône de Champ de Vision (FOV) Dynamique
* Contrairement aux anciens plugins traçant des cônes statiques basés sur le cap GPS, ICU NG calcule le **vecteur d'orientation réel de la caméra**.
* Injecte la géométrie `<sensor>` en temps réel dans Cursor on Target (`REPORT_LOCATION`), affichant la zone exacte couverte au sol.

### 4. Diffusion en Arrière-plan & Écran Verrouillé
* Sous Android, l'accès à la caméra en arrière-plan est coupé dès que l'écran s'éteint.
* ICU NG s'interface avec **ICU NG Capture**, une application compagne (`capture/`) disposant d'un service de premier plan `camera|microphone`.
* Diffusez écran éteint pour préserver la batterie et maintenir la discrétion opérationnelle.

### 5. Multiprotocole et Encodage Haute Performance
* Protocoles pris en charge : **RTSP** (RFC 2326), **RTSPS** (chiffrement TLS) et **SRT** (Secure Reliable Transport avec chiffrement AES et mot de passe).
* Codecs : **H.264 (AVC) Main Profile** et **H.265 (HEVC)**.
* Fréquence d'images constante (CFR) et débit binaire adaptatif configurable.
* Prise en charge intégrale des optiques internes et des caméras externes **USB UVC / OTG**.

---

## 🔒 Sécurité et Conformité ATO (Authority to Operate)

Conçu pour les exigences strictes des réseaux militaires et gouvernementaux :

| Protocole | Port par Défaut | Direction | Chiffrement / Transport | Usage |
| :--- | :--- | :--- | :--- | :--- |
| **RTSP** | `8554` | Sortant uniquement | TCP en clair | Ingestion vidéo classique |
| **RTSPS** | `8555` | Sortant uniquement | TLS sur TCP | Diffusion sécurisée d'entreprise |
| **SRT** | `8890` | Sortant uniquement | UDP / AES-128/256 | Transport résilient à faible latence |

* **Zéro port entrant :** Aucun socket d'écoute ni surface d'attaque réseau sur le terminal mobile (EUD).
* **Isolation complète (Air-Gapped) :** Aucune dépendance cloud, aucune télémétrie externe, aucune communication non sollicitée.
* **Sécurité IPC locale :** Communication entre le plugin et l'application compagne verrouillée par signature de paquet via AIDL.

---

## 🛠️ Compilation & Développement

### Prérequis
* Java 17 (OpenJDK / Temurin)
* Android SDK (API 34–36, Build Tools 34.0.0+)
* SDK ATAK-CIV (5.8.0.7+)

### Compilation
1. Définir le chemin du SDK dans `local.properties` :
   ```properties
   sdk.path=/chemin/vers/ATAK-CIV-5.8.0.7-SDK
   ```
2. Compiler les APKs debug ou release :
   ```bash
   ./gradlew assembleCivDebug
   ./gradlew assembleCivRelease
   ```
   *(Pour compiler l'application compagne : `./gradlew :capture:assembleRelease`)*.

---

## 📄 Licence & Mentions

* Publié sous licence **[Apache 2.0](LICENSE)**.
* Moteur de streaming basé sur [RootEncoder](https://github.com/pedroSG94/RootEncoder) (Apache-2.0) avec injection de piste KLV RFC 6597 (`rootencoder-rtsp/`).
* Support caméra USB via [UVCAndroid](https://github.com/herohan/UVCAndroid) (Apache-2.0).
* Décodage QR via [ZXing](https://github.com/zxing/zxing) (Apache-2.0).

---

<div align="center">
  <sub>Maintenu par <b><a href="https://github.com/carlosgt91">carlosgt91</a></b> • Validé pour l'écosystème ATAK-CIV</sub>
</div>
