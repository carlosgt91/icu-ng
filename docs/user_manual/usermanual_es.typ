#import "@preview/polylux:0.4.0": *
#import "formatting.typ": *

#show: userguide.with(
   plugin-name: "ICU NG",
   plugin-version: "1.1",
   platform: "ATAK",
   platform-version: "5.8.0",
)

#tak-slide[
= Qué es ICU NG
#toolbox.side-by-side(columns: (1fr, 8fr))[
#image("plugin_icon.png", width: 90%)
][
ICU NG emite la cámara del dispositivo (vídeo y audio) a un servidor de vídeo por
RTSP, RTSPS o SRT, con telemetría KLV (MISB ST 0601), y la anuncia en el mapa para
que el resto de ATAK la vea desde su lista de Vídeo con tu callsign.

Son dos piezas que se instalan juntas:
- *Plugin ICU NG* (dentro de ATAK): el botón REC del mapa, el panel de ajustes y el
  anuncio del vídeo.
- *App ICU NG Capture*: hace la emisión. Sigue emitiendo con la pantalla apagada o
  con otra app delante, algo que un plugin no puede hacer por sí solo.

Sin ICU NG Capture el plugin emite él mismo, pero el vídeo se para al apagar la
pantalla.
]
]

#tak-slide[
= Instalación
+ Instala *ICU NG Capture* y después el plugin *ICU NG*.
+ Abre ICU NG Capture una vez desde el cajón de apps y acepta los permisos de
  cámara, micrófono y notificaciones. (Si no, los pide la primera vez que emitas.)
+ Abre ATAK y carga el plugin en el gestor de plugins si lo pide.
+ Abre el panel de ICU NG: bajo el estado debe decir
  _"Emite ICU NG Capture · sigue con la pantalla apagada"_. Si dice
  _"Emite el plugin"_, ATAK no ha encontrado la app: reinstálala y reinicia ATAK.

#toolbox.side-by-side(columns: (1fr, 1fr))[
  #image("2.png", width: 95%)
][
  El panel se abre desde el menú de herramientas de ATAK (_ICU NG_) o manteniendo
  pulsado el botón REC del mapa.
]
]

#tak-slide[
= Emitir desde el mapa: el botón REC
#toolbox.side-by-side(columns: (1fr, 1fr, 5fr))[
  #image("1.png", width: 95%)
][
  #image("4b.png", width: 80%)
][
- *Toque*: empieza o para la emisión con los ajustes guardados, sin abrir el panel.
- *Mantener pulsado y soltar*: abre el panel.
- *Mantener pulsado y arrastrar*: mueve el botón arriba o abajo por el borde
  derecho. La posición se recuerda.

Estados:
- Anillo blanco: detenido.
- Anillo ámbar: conectando (o reconectando).
- Anillo rojo con el punto parpadeando: emitiendo.
- Si falla (URL mal escrita, servidor caído, credenciales), aparece un aviso con
  el motivo.
]
]

#tak-slide[
= El panel
#toolbox.side-by-side(columns: (5fr, 4fr))[
  #image("3.png", width: 100%)
][
- *Vista previa* de la cámara (también mientras emite).
- *Estado* y quién emite (ICU NG Capture o el plugin).
- *Cámara*: trasera, frontal o USB. Se puede cambiar emitiendo, sin cortar.
- *Protocolo y URL* del servidor: `rtsp://`, `rtsps://` o `srt://`.
- *QR*: lee la URL del servidor de un código QR.
- *Emitir / Detener*.
- *KLV*: envía la telemetría con el vídeo.

Todo se guarda al momento: el botón REC emite con lo que muestra el panel.
]
]

#tak-slide[
= Calidad
#toolbox.side-by-side(columns: (5fr, 4fr))[
  #image("5.png", width: 100%)
][
El botón *Calidad* abre:
- *Resolución*: 480p, 720p o 1080p.
- *FPS*: 15, 24 o 30.
- *Bitrate*: _Auto_ (según resolución) o fijo, de 0,5 a 6 Mbps.
- *Bitrate adaptativo*: si la red no da para más (VPN, datos móviles), baja la
  calidad en vez de cortarse, y la sube cuando mejora.
- *Micrófono*: desactivado envía silencio.

Resolución y fps se aplican al empezar a emitir. Bitrate, adaptativo y micrófono
cambian al momento, también en plena emisión.
]
]

#tak-slide[
= Con la pantalla apagada
#toolbox.side-by-side(columns: (1fr, 1fr))[
  #image("6.png", width: 100%)
][
Mientras emite, ICU NG Capture muestra esta notificación fija. La emisión sigue:
- con la pantalla apagada o el dispositivo en el bolsillo,
- con otra app delante,
- incluso si ATAK se cierra (al volver a abrirlo, el plugin la retoma).

*Detener* en la notificación para la emisión desde cualquier sitio.

Si el servidor se cae o se pierde la red, la app reintenta cada pocos segundos y
vuelve a emitir sola cuando hay conexión.
]
]

#tak-slide[
= El vídeo en el mapa y la telemetría KLV
- Mientras emites (RTSP o SRT), el resto de usuarios de ATAK ven tu vídeo en su
  lista de *Vídeo*, con tu *callsign* como nombre. RTSPS no se anuncia: ATAK no
  sabe abrir ese tipo de enlace.
- En tu marcador aparece un *cono* con hacia dónde mira la cámara. Con la frontal
  apunta hacia atrás.
- *KLV (MISB ST 0601)*, una vez por segundo: tu posición y altitud, hacia dónde
  apunta la cámara (rumbo e inclinación, de los sensores del dispositivo), su campo
  de visión y el punto del terreno al que mira. Los visores compatibles (el propio
  ATAK, entre otros) lo muestran sobre el vídeo y en el mapa.
- El KLV va por RTSP/RTSPS. Por SRT no se envía.
- Con *cámara USB* no se envía KLV: no se sabe hacia dónde apunta.
]

#tak-slide[
= Cámara USB y configuración por QR
#toolbox.side-by-side(columns: (1fr, 1fr))[
*Cámara USB (UVC)*

Conecta la cámara, elige _USB_ en el panel y emite. La primera vez Android pide
permiso para que ICU NG Capture use esa cámara. Si se conecta después de empezar,
la imagen aparece sola al enchufarla.
][
*QR del servidor*

El botón *QR* lee la URL del servidor de un código QR (por ejemplo, el que genera
el servidor de vídeo) y rellena protocolo, URL y, en SRT, el passphrase:

`rtsp://[usuario:clave@]host:puerto/ruta`

`rtsps://[usuario:clave@]host:puerto/ruta`

`srt://host:puerto?streamid=publish:ruta`

Un QR de _ver_ (`read:`) vale también: ICU NG siempre publica.
]
]

#tak-slide[
= Problemas frecuentes
- *El panel dice "Emite el plugin"*: ATAK no ve ICU NG Capture. Instálala (o
  reinstálala) y reinicia ATAK.
- *"No se pudo abrir ICU NG Capture"*: la app se ha desinstalado o no responde.
  Ábrela una vez desde el cajón de apps.
- *Se queda en ámbar*: el servidor no contesta. Revisa la URL, la red o la VPN.
- *"Error de autenticación"*: usuario o clave de la URL incorrectos.
- *"ICU NG Capture no tiene permiso de cámara"*: Ajustes de Android → Apps → ICU NG Capture →
  Permisos.
- *Sin KLV en el receptor*: comprueba que la casilla KLV esté marcada, que el
  protocolo sea RTSP/RTSPS y que no sea cámara USB. Sin posición GPS no se envía.

#toolbox.side-by-side(columns: (3fr, 2fr))[
  #image("7.png", width: 100%)
][
  Este manual: Ajustes → Tool Preferences → Specific Tool Preferences → ICU NG.
]
]
