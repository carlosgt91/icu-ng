# icu-replacement

Plugin ATAK sustituto de TAKICU/OpenICU, integrado en el propio menú nativo de ATAK (no como plugin flotante aparte).

## Contexto / referencias

- **TAK ICU** — plugin ATAK ya existente, pero limitado: no soporta streaming de audio y autenticación/cifrado (RTSPS) a la vez.
- [**OpenTAK_ICU**](https://github.com/brian7704/OpenTAK_ICU) (brian7704) — alternativa que arregla esas limitaciones: RTSP/RTSPS/RTMP/RTMPS/SRT/multicast UDP, vídeo H264/H265/AV1, audio AAC/G711/OPUS, autenticación y cifrado simultáneos, streaming en segundo plano, captura de foto durante grabación, cambio de cámara, bitrate adaptativo. Construido sobre [RootEncoder](https://github.com/pedroSG94/RootEncoder) (pedroSG94).
  - Pero es una **app Android independiente**, no un plugin: hay que cambiar de app y configurar servidor/credenciales a mano en la herramienta de vídeo de ATAK cada vez.
  - **Sin licencia publicada** (ni LICENSE ni mención en README) — no partir de un fork de su código. Construir directamente sobre RootEncoder (licencia abierta clara), usando OpenTAK_ICU como referencia de features/UX, no como base de código.

## Objetivo

Igualar el conjunto de funcionalidad de OpenTAK_ICU (protocolos, codecs, auth+cifrado simultáneos) pero como plugin ATAK real, integrado en el menú nativo — sin cambiar de app ni reconfigurar servidor a mano cada vez.

## Arquitectura propuesta (servidor de recepción)

- **Servidor**: [raytheonbbn/tak-video-restreamer](https://github.com/raytheonbbn/tak-video-restreamer) — Flask + MediaMTX + FFmpeg, dockerizado. Recibe RTSP/RTSPS/SRT/RTMP y redistribuye a ATAK/WinTAK/CloudTAK/navegador/VLC, con grabación y API de gestión de streams.
- **División de trabajo**: el plugin solo captura y *publica* (RootEncoder → RTSP/RTSPS/SRT hacia el servidor); toda la redistribución/transcodificación la hace el servidor, no el plugin.
- **Despliegue**: instancia propia en Gontra, **separada** de la instalación de MediaMTX que ya usa SPATAK (profesional) — mismo criterio de compartimento estanco que en el resto del vault.
- **Seguridad**: cambiar las credenciales por defecto (`admin:changeme`) antes de exponerlo, aunque sea solo en el tailnet.

## Pendiente de definir

- Qué funcionalidad concreta se replica primero (probablemente: RTSP/RTSPS básico con audio, antes que SRT/multicast).
- Punto de integración exacto en el menú nativo de ATAK (extension point) — ver la skill `atak-plugin` para referencia de extension points.
- Desplegar `tak-video-restreamer` en Gontra (Docker Compose) y decidir el nombre del path/stream por dispositivo.

Ver `../CLAUDE.md` para el entorno de build y las convenciones generales del monorepo.
