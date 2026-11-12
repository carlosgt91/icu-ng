<div align="center">

# Plugin ATAK — ICU NG (Next Generation)

[![ATAK-CIV](https://img.shields.io/badge/ATAK--CIV-5.8%2B-blue.svg)](https://tak.gov)
[![Estándares](https://img.shields.io/badge/Estándares-MISB%20ST%200601%20%7C%20ST%200902%20%7C%20STANAG%204609-success.svg)](https://gwg.nga.mil/)
[![Protocolos](https://img.shields.io/badge/Protocolos-RTSP%20%7C%20RTSPS%20%7C%20SRT-orange.svg)](#seguridad-y-normativa-ato)
[![Versión](https://img.shields.io/badge/Versión-v1.2.1-brightgreen.svg)](https://github.com/carlosgt91/icu-ng/releases/tag/v1.2.1)
[![Licencia](https://img.shields.io/badge/Licencia-Apache%202.0-lightgrey.svg)](LICENSE)

**Plugin profesional de streaming de vídeo táctico de baja latencia para ATAK-CIV.**  
*Emisión nativa de cámara con metadatos STANAG 4609 / KLV MISB, huella dinámica de cono de sensor (FOV) en mapa y soporte de captura en segundo plano.*

---

[English](README.md) • [Español](README.es.md) • [Deutsch](README.de.md) • [Українська](README.uk.md) • [Français](README.fr.md) • [Português](README.pt.md)

</div>

---

## 📌 Descripción General

**ICU NG** es un sustituto moderno y directo para la herramienta heredada *TAK ICU*, integrado de forma nativa dentro de ATAK como plugin de primer nivel. Permite al combatiente/operador en el terreno emitir vídeo y audio en tiempo real desde las cámaras integradas (trasera/frontal) o fuentes externas USB (UVC) directamente hacia servidores de vídeo **RTSP, RTSPS (TLS) o SRT**.

Las emisiones se etiquetan con el indicativo (*callsign*) del operador y se anuncian de forma automática a través del gestor de conexiones de vídeo de ATAK (`<__video>` CoT). La huella activa de la cámara se dibuja dinámicamente sobre el mapa de los miembros del equipo, incluyendo el cono de visión del sensor y la proyección de esquinas de vídeo sobre el terreno.

```
       [ Cámara del Terminal / USB UVC ]
                       │
                       ▼
           ┌───────────────────────┐
           │     Plugin ICU NG     │ ◄─── Telemetría (Vector de rotación, GPS, elevación)
           │ (o App Captura Acom.) │ ───► Inyección KLV MISB ST 0601 / ST 0902 (2 Hz)
           └───────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        ▼ (RTSP / RTSPS / SRT)        ▼ (Anuncio CoT Video)
    ┌───────────────────────┐   ┌───────────────────────┐
    │ MediaMTX / Restreamer │   │      TAK Server       │
    └───────────────────────┘   └───────────────────────┘
                │                           │
                └──────────────┬────────────┘
                               ▼
                [ Clientes ATAK Remotos / TOC ]
             (Emisión con proyección sobre mapa)
```

---

## 🚀 Descargas (Última Versión: v1.2.1)

Los APKs precompilados están disponibles en [GitHub Releases](https://github.com/carlosgt91/icu-ng/releases/tag/v1.2.1):

| Paquete | Archivo | Descripción |
| :--- | :--- | :--- |
| **Plugin ATAK** | [ATAK-Plugin-IcuNg-1.2.1--5.8.0-civ-release.apk](https://github.com/carlosgt91/icu-ng/releases/download/v1.2.1/ATAK-Plugin-IcuNg-1.2.1--5.8.0-civ-release.apk) | Paquete de plugin nativo para ATAK-CIV 5.8+ |
| **App Compañera** | [ICU-NG-Capture-1.2.1-release.apk](https://github.com/carlosgt91/icu-ng/releases/download/v1.2.1/ICU-NG-Capture-1.2.1-release.apk) | Servicio independiente para emisión con pantalla apagada |

### Sumas de Verificación SHA-256

```text
623b66ebedbd7a2279a8442f525fc28c1948e2ca3fba329ffe305dff346ab2d5  ATAK-Plugin-IcuNg-1.2.1--5.8.0-civ-release.apk
57a4ebf16592103d059d1191ce956f2914ffe1ab5523bedeb1882914602933e5  ICU-NG-Capture-1.2.1-release.apk
```

---

## 🎯 Capacidades Principales

### 1. Interfaz Táctica sin Fricción
* **Botón REC sobre el mapa:** Toque para iniciar/detener la emisión con los ajustes guardados. Pulsación larga para desplegar el panel de configuración rápida; arrastrar para moverlo por el borde de la pantalla.
* **Integración en Menú Radial:** Inicie y supervise emisiones directamente desde el menú radial del marcador propio.
* **Configuración por Código QR:** Configure al instante URLs de servidor, credenciales y passphrases SRT mediante escaneo QR con la cámara.

### 2. Cumplimiento OTAN / STANAG 4609 y KLV MISB
* **MISB ST 0601 y ST 0902:** Genera paquetes de metadatos KLV conformes a **2 Hz** (cumpliendo ST 1402):
  * Posición de la plataforma y altitud elipsoidal (Tags 13, 14, 15).
  * Acimut, elevación y balanceo relativo del sensor (Tags 5, 6, 7, 20).
  * Campo de visión horizontal (Tag 16) y ancho de objetivo (Tag 22).
  * Centro del encuadre y coordenadas de las esquinas sobre el terreno (Tags 23–26).
  * Conjunto Local de Seguridad (MISB ST 0102, Tag 48) e ID de Misión por defecto (Tag 3).
  * Identificador MIIS Core (MISB ST 1204, Tag 94) con UUID v5 persistente para seguimiento de sensor/plataforma.
* **Marcas de tiempo de precisión MISB ST 0604:** Inyectadas en cabeceras SEI en cada fotograma H.264/H.265.
* **Proyección de vídeo sobre el terreno:** Los receptores pueden proyectar la perspectiva visual de la cámara directamente sobre la cartografía de ATAK.

### 3. Cono de Campo de Visión (FOV) Dinámico
* A diferencia de plugins antiguos que dibujan conos estáticos según el rumbo GPS, ICU NG calcula el **vector real de orientación de la cámara**.
* Inyecta la geometría `<sensor>` en tiempo real en Cursor on Target (`REPORT_LOCATION`), mostrando a los compañeros el frustum visual exacto y su intersección en el terreno.

### 4. Emisión en Segundo Plano y Pantalla Apagada
* En Android, el acceso a la cámara en segundo plano se revoca al apagar la pantalla.
* ICU NG se enlaza de forma transparente con **ICU NG Capture**, un servicio complementario (`capture/`) con servicio en primer plano de tipo `camera|microphone`.
* Mantenga la transmisión activa con la pantalla bloqueada, ahorrando batería y manteniendo la disciplina operativa en misiones encubiertas.

### 5. Multiprotocolo y Codificación de Alto Rendimiento
* Protocolos: **RTSP** (RFC 2326), **RTSPS** (cifrado TLS) y **SRT** (Secure Reliable Transport con cifrado AES y passphrase).
* Códecs: **H.264 (AVC) Main Profile** y **H.265 (HEVC)**.
* Reloj de tasa de fotogramas constante (CFR) y tasa de bits adaptativa configurable.
* Compatibilidad total con lentes integradas y cámaras externas **USB UVC / OTG** (ópticas, visores o cámaras corporales).


---

## 🔒 Seguridad y Normativa ATO (Authority to Operate)

Diseñado desde el inicio para despliegues militares y redes corporativas de alta seguridad:

| Protocolo | Puerto por Defecto | Dirección | Cifrado / Transporte | Propósito |
| :--- | :--- | :--- | :--- | :--- |
| **RTSP** | `8554` | Saliente únicamente | TCP en claro | Ingesta clásica de vídeo |
| **RTSPS** | `8555` | Saliente únicamente | TLS sobre TCP | Emisión cifrada corporativa |
| **SRT** | `8890` | Saliente únicamente | UDP / AES-128/256 | Transporte resiliente de baja latencia |

* **Cero puertos entrantes:** Sin sockets a la escucha ni vectores de ataque de red abiertos en el terminal EUD móvil.
* **Cero telemetría / Air-Gapped:** Sin dependencias en la nube, sin rastreo externo ni llamadas de red no autorizadas.
* **Seguridad IPC Local:** La comunicación entre el plugin y la app complementaria está restringida exclusivamente a la firma y permisos del paquete ATAK vía AIDL.

---

## 🗺️ Hoja de ruta

Próximos cambios previstos (el orden puede cambiar según el feedback):

- **Pantalla principal más limpia:** protocolo, URL, QR y marcado KLV pasan a un botón de *Configuración*, para dejar sitio a la vista previa.
- **KLV en la grabación local:** los MP4 conservan la telemetría MISB ST 0601, para analizarlos con herramientas STANAG 4609 tras la misión.
- **Foto georreferenciada:** un toque captura un fotograma y lo deja en el mapa como marcador con la imagen, para compartirlo con el equipo.
- **Perfil de bajo ancho de banda:** ajuste de un toque (baja resolución y fps, sin audio) para enlaces de radio o satélite débiles.

### 💬 Se agradecen comentarios y sugerencias

Los informes de fallos, la experiencia en campo y las sugerencias son muy bienvenidos. Abre un [issue](https://github.com/carlosgt91/icu-ng/issues) indicando modelo de dispositivo, versión de ATAK y versión de ICU NG.

---

## 🛠️ Compilación y Desarrollo

### Requisitos
* Java 17 (OpenJDK / Temurin)
* Android SDK (API 34–36, Build Tools 34.0.0+)
* SDK de ATAK-CIV (5.8.0.7+)

### Compilación
1. Configure la ruta del SDK en `local.properties`:
   ```properties
   sdk.path=/ruta/a/ATAK-CIV-5.8.0.7-SDK
   ```
2. Genere los APKs de depuración o producción:
   ```bash
   ./gradlew assembleCivDebug
   ./gradlew assembleCivRelease
   ```
   *(Para compilar la app compañera: `./gradlew :capture:assembleRelease`)*.

---

## 📄 Licencia y Reconocimientos

* Publicado bajo la **[Licencia Apache 2.0](LICENSE)**.
* Motor de streaming basado en [RootEncoder](https://github.com/pedroSG94/RootEncoder) (Apache-2.0) modificado con inyección de pista KLV RFC 6597 (`rootencoder-rtsp/`).
* Soporte de cámaras USB mediante [UVCAndroid](https://github.com/herohan/UVCAndroid) (Apache-2.0).
* Decodificación de códigos QR mediante [ZXing](https://github.com/zxing/zxing) (Apache-2.0).

---

<div align="center">
  <sub>Mantenido por <b><a href="https://github.com/carlosgt91">carlosgt91</a></b> • Verificado para el ecosistema ATAK-CIV</sub>
</div>
