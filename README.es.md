[English](README.md) | [Español](README.es.md) | [Deutsch](README.de.md) | [Українська](README.uk.md)

Plugin ATAK — ICU NG

Streaming de cámara para ATAK: vídeo y audio desde las cámaras del dispositivo
(trasera, frontal o USB/UVC) a un servidor de vídeo RTSP, RTSPS o SRT, con
telemetría KLV MISB ST 0601, anunciado en el mapa para que otros usuarios de
ATAK puedan abrirlo desde su lista de Vídeo. Un sustituto de TAK ICU integrado
dentro de ATAK.

Manual de usuario: dentro de ATAK, Ajustes → Preferencias de herramientas →
Preferencias de herramientas específicas → ICU NG (fuente: docs/user_manual/).

_________________________________________________________________
PROPÓSITO Y CAPACIDADES

ICU NG permite a un operador emitir lo que ve la cámara del dispositivo sin salir
de ATAK y sin tener que configurar la emisión a mano en otra aplicación cada vez.
La emisión lleva como nombre el indicativo del operador y se anuncia a través del
gestor de conexiones de vídeo de ATAK, de modo que el resto del equipo puede
verla desde ATAK.

- Botón REC en el mapa: toque para iniciar/detener con los ajustes guardados,
  pulsación larga para el panel de ajustes, pulsación larga y arrastrar para
  moverlo a lo largo del borde derecho.
- Panel de ajustes con vista previa en directo: cámara, protocolo, URL del
  servidor (escrita o leída de un código QR), passphrase y cifrado SRT, calidad
  (resolución, fps, bitrate, bitrate adaptativo, micrófono).
- MISB ST 0601 KLV sobre RTSP/RTSPS, una vez por segundo: posición y altitud
  del sensor, acimut y elevación de la cámara (sensor de vector de rotación),
  campo de visión, centro del encuadre en el terreno (datos de elevación de ATAK),
  indicativo.
- Cono de campo de visión del sensor sobre el marcador propio durante la emisión.
- Botón de emisión en el menú radial del marcador propio.

Emisión en segundo plano. Un plugin se ejecuta dentro del proceso de ATAK, y el
único servicio en primer plano de ATAK es de tipo "location", por lo que Android
detiene el acceso a la cámara del plugin cuando la pantalla se apaga. Para
continuar emitiendo con la pantalla apagada, la captura de cámara puede realizarse
mediante una app complementaria opcional, ICU NG Capture (capture/ en el
repositorio de código fuente; una app Android estándar, que no forma parte de
esta entrega), que mantiene un servicio en primer plano de cámara/micrófono. El
plugin sigue siendo la interfaz de usuario completa y controla la app; sin la
app instalada el plugin emite por sí mismo, únicamente en primer plano.

_________________________________________________________________
ESTADO

Versión 1.0. Verificado en ATAK-CIV 5.8.0.7 (compilación de desarrollador):
emulador (Android 14) para todas las funciones, incluida la compilación civRelease;
probado en campo en una tablet Samsung (Android 16) emitiendo por RTSP hacia un
retransmisor basado en MediaMTX a través de una VPN. Todavía no ejecutado en
hardware: una cámara USB (UVC).

Preparado para la entrega a terceros en tak.gov.

_________________________________________________________________
PUNTOS DE CONTACTO

Carlos (carlosgt91)
https://github.com/carlosgt91/icu-ng/issues

_________________________________________________________________
PUERTOS REQUERIDOS

(Esto es importante para ATO, redes y otras cuestiones de seguridad)

  Solo salientes, y únicamente mientras el operador esté emitiendo, hacia el
  único servidor de vídeo que haya configurado el operador (URL escrita en el
  panel o leída de un QR):

  - RTSP:  TCP hacia el puerto RTSP del servidor (MediaMTX por defecto 8554).
  - RTSPS: TCP + TLS hacia el puerto RTSPS del servidor (MediaMTX por defecto 8555).
  - SRT:   UDP hacia el puerto SRT del servidor (MediaMTX por defecto 8890).

  Sin puertos entrantes ni sockets a la escucha. Sin tráfico propio hacia o
  desde el servidor TAK: la emisión se anuncia como una conexión de vídeo a
  través de las propias API de ATAK, transmitida en el CoT del marcador propio
  por ATAK. No se envía nada cuando no se está emitiendo; sin red el plugin
  sencillamente no puede conectarse.

  IPC local con la app complementaria ICU NG Capture, cuando está instalada:
  inicio de actividades (start/stop) y un servicio vinculado AIDL (estado,
  superficie de vista previa, paquetes KLV). La app solo acepta llamadas
  procedentes del paquete de ATAK.

_________________________________________________________________
EQUIPO REQUERIDO

  Dispositivo Android compatible con ATAK-CIV 5.8, con cámara. Permisos de
  cámara y micrófono (de ATAK, o de ICU NG Capture cuando esté instalada). Sin
  uso de almacenamiento más allá de las preferencias de ATAK.

_________________________________________________________________
EQUIPO SOPORTADO

  Las propias cámaras del dispositivo. Cámaras USB Video Class (UVC) mediante
  USB OTG. Un servidor de vídeo que admita publicación por RTSP/RTSPS/SRT, p. ej.
  MediaMTX o raytheonbbn/tak-video-restreamer.

_________________________________________________________________
COMPILACIÓN

  Compilación estándar de plugin de ATAK. Defina sdk.path en local.properties
  apuntando a un SDK de ATAK CIV descomprimido, y luego:

      ./gradlew assembleCivDebug
      ./gradlew assembleCivRelease

  ext.ATAK_VERSION en app/build.gradle selecciona la versión de ATAK a la que
  dirigirse. El proyecto tiene dos módulos: app (el plugin) y rootencoder-rtsp
  (véase más abajo). En el repositorio de código fuente se incluye un tercero,
  capture (la app complementaria), cuando está presente, el cual no es necesario
  para compilar el plugin.

_________________________________________________________________
NOTAS PARA DESARROLLADORES

  - El streaming utiliza RootEncoder 2.8.1 (pedroSG94, Apache-2.0), API StreamBase.
    rootencoder-rtsp/ es una copia del módulo rtsp de RootEncoder, modificada para
    anunciar y enviar una pista KLV (RFC 6597, application/smpte336m) junto al
    vídeo y audio; véase rootencoder-rtsp/NOTICE.md. La app lo sustituye por el
    artefacto rtsp publicado.
  - Cámaras USB: UVCAndroid 1.0.13 (herohan). Su código nativo realiza
    retrollamadas a Java por nombre, de ahí sus reglas keep de proguard.
  - Decodificación QR: ZXing core.
  - Los ajustes residen en las preferencias por defecto de ATAK (claves icung.*),
    por lo que el botón REC puede emitir sin que el panel se haya abierto nunca.
  - versionCode se calcula a partir de PLUGIN_VERSION y ATAK_VERSION (no de
    git), de modo que las compilaciones realizadas desde este zip llevan un
    versionCode coherente.
  - La interfaz de la app complementaria: app/src/main/aidl y CaptureLink.java en
    el lado del plugin; los mismos archivos AIDL en la app.
