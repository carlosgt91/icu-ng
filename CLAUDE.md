# icu-replacement

Plugin ATAK sustituto de TAKICU/OpenICU, integrado en el propio menú nativo de ATAK (no como plugin flotante aparte).

## Contexto / referencias

- **TAK ICU** — plugin ATAK ya existente, pero limitado: no soporta streaming de audio y autenticación/cifrado (RTSPS) a la vez.
- [**OpenTAK_ICU**](https://github.com/brian7704/OpenTAK_ICU) (brian7704) — alternativa que arregla esas limitaciones: RTSP/RTSPS/RTMP/RTMPS/SRT/multicast UDP, vídeo H264/H265/AV1, audio AAC/G711/OPUS, autenticación y cifrado simultáneos, streaming en segundo plano, captura de foto durante grabación, cambio de cámara, bitrate adaptativo. Construido sobre [RootEncoder](https://github.com/pedroSG94/RootEncoder) (pedroSG94).
  - Pero es una **app Android independiente**, no un plugin: hay que cambiar de app y configurar servidor/credenciales a mano en la herramienta de vídeo de ATAK cada vez.
  - **Sin licencia publicada** (ni LICENSE ni mención en README) — no partir de un fork de su código. Construir directamente sobre RootEncoder (licencia abierta clara), usando OpenTAK_ICU como referencia de features/UX, no como base de código.

## Objetivo

Igualar el conjunto de funcionalidad de OpenTAK_ICU (protocolos, codecs, auth+cifrado simultáneos) pero como plugin ATAK real, integrado en el menú nativo — sin cambiar de app ni reconfigurar servidor a mano cada vez.

## Qué recibe el vídeo (fuera de alcance de este subproyecto)

El plugin solo captura y *publica* (RootEncoder → RTSP/RTSPS/SRT). No es responsabilidad de este subproyecto desplegar ni gestionar el servidor receptor — eso es aparte. Como referencia de qué tipo de servidor puede recibirlo: [raytheonbbn/tak-video-restreamer](https://github.com/raytheonbbn/tak-video-restreamer) (Flask + MediaMTX + FFmpeg) es un ejemplo de receptor compatible con RTSP/RTSPS/SRT/RTMP que redistribuye a ATAK/WinTAK/CloudTAK — sirve para saber contra qué protocolo/formato desarrollar el publicador, nada más.

## Pendiente de definir

- Qué funcionalidad concreta se replica primero (probablemente: RTSP/RTSPS básico con audio, antes que SRT/multicast).
- Punto de integración exacto en el menú nativo de ATAK (extension point) — ver la skill `atak-plugin` para referencia de extension points.

Ver `../CLAUDE.md` para el entorno de build y las convenciones generales del monorepo.

## App compañera ICU NG Capture (`capture/`, rama `icu-ng/app-companera`)

App Android normal (no plugin, no necesita firma de TAK) que emite desde un servicio en primer plano de tipo `camera|microphone`, para que la emisión siga con la pantalla apagada (ATAK solo tiene servicio de tipo `location` y Android le quita la cámara en segundo plano). El plugin hace de interfaz: `CaptureLink` (plugin) ↔ `CaptureContract` + `ICaptureService.aidl` (app). Los `.aidl` están duplicados en `app/src/main/aidl` y `capture/src/main/aidl`: cambiar ambos a la vez.

- Arranque/parada: `startActivityForResult` a `ControlActivity` (transparente; solo así la app puede arrancar un servicio de cámara). La app solo obedece si `getCallingPackage()` es ATAK.
- Todo lo demás por el servicio enlazado (`ICaptureService`, cada llamada comprueba que viene del uid de ATAK): estado, vista previa del panel (el plugin pasa el `Surface`; `setPreview` es síncrono para que la app suelte la superficie antes de que se destruya), cambio de cámara sin reconexión y paquetes KLV.
- El KLV se sigue calculando en el plugin (`KlvTelemetry` con `PacketSink`: posición de ATAK, elevación del terreno, sensores) y se pasa a la app 1 vez/s. Verificado que sigue llegando con la pantalla apagada.
- Cámaras USB también desde la app (`UsbCameraSource` copiada). En reposo la vista previa del panel la sigue dibujando el plugin; al emitir se le pasa a la app y vuelve al parar.
- ATAK 5.8 (targetSdk 35) solo ve apps que casen con sus `<queries>`; el filtro `VIEW` con mimeType propio en `ControlActivity` es lo que hace visible la app.
- Sin la app instalada el plugin emite él mismo como antes; el panel dice quién emite. Se detecta también si se instala con ATAK abierto.
- La versión anterior (solo plugin) está en la etiqueta `icu-ng-plugin-solo-v0.1`.
- Build: `./gradlew :capture:assembleDebug`.
