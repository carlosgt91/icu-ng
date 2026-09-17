# ICU NG — diseño

Fecha: 2026-09-17
Subproyecto: `icu-replacement/`
Nombre del plugin: **ICU NG** (paquete `com.carlosgt.atak.icung`, nombre visible "ICU NG" en el menú de ATAK)

## Objetivo

Plugin ATAK nativo que iguala progresivamente la funcionalidad de
[OpenTAK_ICU](https://github.com/brian7704/OpenTAK_ICU) (streaming
RTSP/RTSPS/RTMP/SRT con audio, autenticación/cifrado simultáneos) pero
integrado en el propio menú nativo de ATAK — sin cambiar de app ni
reconfigurar servidor a mano cada vez, como exige hoy TAK ICU/OpenTAK_ICU.

v1 (esta spec) cubre: captura de cámara+audio, push RTSP a un servidor
externo, y anuncio del stream en el mapa de los demás vía CoT — el mínimo
que ya supera a TAK ICU oficial (que no soporta audio) y que sienta la base
para añadir RTSPS/SRT/multicast/cambio de cámara/etc. después.

## Contexto y decisiones previas

- **No forkear** OpenTAK_ICU ni TAK-PluginSuite-ATAK_ICU (jpat-12): ninguno
  tiene licencia publicada. Se usan solo como referencia de features/UX y de
  patrón de integración (jpat-12 documenta bien el `ARCHITECTURE.md` del
  patrón `IVideoConnectionManager`/`CotDetailManager`), nunca como código
  base.
- **RootEncoder** (pedroSG94, Apache-2.0, vía JitPack) como motor de
  captura/codificación/streaming — evita reimplementar RTSP/RTMP/SRT a mano,
  que es donde jpat-12 ha perdido más tiempo.
- El servidor receptor (tipo [tak-video-restreamer](https://github.com/raytheonbbn/tak-video-restreamer),
  MediaMTX+FFmpeg+Flask) está **fuera de alcance**: el plugin solo publica
  (rol cliente RTSP), no aloja ni gestiona el receptor. Por defecto, la URL
  que se anuncia por CoT se deriva del mismo host/puerto/stream-key de
  publicación (asumiendo un servidor tipo MediaMTX donde push y pull
  comparten host); la config permite sobrescribir la URL de lectura por
  separado para el caso de un host de lectura distinto (p. ej. detrás de
  NAT o con DNS público distinto al de publicación).
- Entorno de build: `tools/atak-plugin-dev` (submódulo, dockerizado). SDK de
  ATAK con licencia tak.gov, montado como volumen, nunca committeado.
  `ATAK_SDK_DIR` en `.env` de esta máquina apunta a
  `ATAK-CIV-5.8.0.5-SDK`.
- Objetivo de test: emulador Android `google_apis` (no `aosp_atd`, que
  renderiza framebuffer negro con `uiautomator` reportando éxito igualmente),
  con ubicación mock para simular el fix GPS.

## Arquitectura

Plugin nuevo estilo `IPlugin` (`gov.tak.api.plugin`), patrón de plantilla
ATAK 5.7+, con `assets/plugin.xml`.

- **`IcuNgPlugin`** (`IPlugin`) — entry point. En `onStart` registra un
  `ToolbarItem` (identificador = `pluginContext.getPackageName()`) que abre
  un `Pane` con el preview de cámara y los controles start/stop.
- **`StreamController`** — envuelve `RtspCamera2`/`RtspCamera1` de
  RootEncoder: vincula el preview (`OpenGlView`/`SurfaceView`, inflado con
  `PluginLayoutInflater`, nunca el inflater de ATAK), gestiona
  start/stop/retry de la conexión hacia el servidor RTSP configurado
  (host/puerto/stream-key en las prefs del plugin).
- **`SelfMarkerVideoAnnouncer`** — un `CotDetailHandler` registrado en
  `CotDetailManager.getInstance().registerHandler(...)`, enganchado al ciclo
  de emisión de la SA propia (self-marker) para añadir el detalle
  `<__video>` (URL de lectura) y `<sensor>` (cono FOV/rumbo) **solo cuando
  hay fix GPS y el stream está activo**. No crea un timer/emisor CoT
  independiente — se apoya en el pipeline de SA propia que ATAK ya corre.
- **Registro en `IVideoConnectionManager`/`ConnectionEntry`** — para que la
  herramienta de Vídeo nativa de ATAK vea el stream aunque el CoT no llegue
  o el usuario lo abra manualmente desde ahí, igual que hace la herramienta
  UAS de drones.

## Flujo de datos

Cámara+mic → RootEncoder codifica (H264/AAC) → push RTSP al servidor externo
configurado → si hay fix GPS, se añade el detalle CoT en el siguiente ciclo
de SA propia → los demás ATAK ven el cono de vídeo sobre el marcador propio
y pueden abrir el stream desde la herramienta de Vídeo nativa (por CoT o por
`IVideoConnectionManager` directamente).

## Gating por GPS

Decisión: **el vídeo sin metadatos de posición aporta poco en una app de
mando y control**, así que el comportamiento por defecto es exigente, no
permisivo:

- El preview de cámara funciona sin fix GPS (para poder encuadrar/probar).
- El botón "emitir" **no publica el detalle CoT ni el registro en
  `IVideoConnectionManager` hasta que hay fix GPS** — igual que TAK ICU.
- Si el fix se pierde a media emisión, se retira el detalle CoT y el
  registro en `IVideoConnectionManager` (el stream RTSP en sí puede seguir
  vivo hacia el servidor, pero deja de anunciarse en el mapa) hasta
  recuperar fix.

## Fases de implementación

1. **Esqueleto** — plantilla renombrada (`com.carlosgt.atak.icung`), botón
   en el menú, `Pane` vacío. *Hecho* = carga en ATAK vía `deploy`/sync/Load,
   aparece en el drawer de Tools.
2. **Cámara + RootEncoder + preview local** — captura y muestra preview en
   el Pane, sin emitir aún. *Hecho* = se ve la cámara del emulador en el
   panel del plugin.
3. **Servidor/push RTSP** — configuración de servidor destino (host, puerto,
   stream-key), start/stop real hacia un receptor real. *Hecho* = un
   cliente RTSP externo (VLC, ffplay) reproduce el stream publicado.
4. **Emisión CoT** — `SelfMarkerVideoAnnouncer` + registro en
   `IVideoConnectionManager`, con el gating por GPS. *Hecho* = un segundo
   ATAK en la misma red ve el cono de vídeo sobre el marcador propio y abre
   el stream desde la herramienta de Vídeo nativa.

## Fuera de alcance de v1

RTSPS/RTMP/SRT/multicast UDP, autenticación/cifrado simultáneos, cambio de
cámara, foto durante grabación, bitrate adaptativo, streaming en segundo
plano. Quedan para iteraciones posteriores, con el objetivo final de igualar
el conjunto de funcionalidad completo de OpenTAK_ICU.

## Testing

- **JVM unit tests** (`testCivDebugUnitTest`) para lógica sin Android:
  parsing/validación de la config de servidor, construcción del detalle CoT
  (`<__video>`/`<sensor>`) a partir de un estado dado.
- **Tests instrumentados** (`instrument`, clases derivadas de
  `ATAKTestClass`, con `ClassLoaderReplacer.fixClassLoaderForClass`) para el
  ciclo de vida del handler de `CotDetailManager` y el registro/baja en
  `IVideoConnectionManager` al ganar/perder fix GPS.
- **Verificación manual end-to-end** en el emulador `google_apis` (ubicación
  mock para simular el fix GPS) contra un receptor RTSP real (MediaMTX o
  `tak-video-restreamer`), y contra un segundo ATAK para confirmar que el
  cono de vídeo aparece sobre el marcador propio.

## Licencias / cumplimiento

- RootEncoder: Apache-2.0 — sin restricción para uso/distribución.
- SDK de ATAK: nunca committeado; `scan` (de `atak-plugin-dev`) antes de cada
  commit para detectar material del SDK colado en el árbol.
- No se copia código de OpenTAK_ICU ni de TAK-PluginSuite-ATAK_ICU (sin
  licencia publicada) — solo se reimplementa el patrón de integración
  descrito en su documentación, con nombres y estructura propios.
