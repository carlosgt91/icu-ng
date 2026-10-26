#import "@preview/polylux:0.4.0": *
#import "formatting.typ": *

#show: userguide.with(
   plugin-name: "ICU NG",
   plugin-version: "1.1.4",
   platform: "ATAK",
   platform-version: "5.8.0",
)

#tak-slide[
= What is ICU NG
#toolbox.side-by-side(columns: (1fr, 8fr))[
#image("plugin_icon.png", width: 90%)
][
ICU NG streams the device camera (video and audio) to a video server via
RTSP, RTSPS, or SRT, with KLV telemetry (MISB ST 0601), and announces it on the map
so other ATAK users can view it from their Video list under your callsign.

It consists of two components installed together:
- *ICU NG Plugin* (inside ATAK): the on-map REC button, the settings panel, and
  the video announcement.
- *ICU NG Capture app*: handles the streaming. It keeps streaming with the screen off
  or with another app in front, something a plugin cannot do on its own.

Without ICU NG Capture, the plugin streams on its own, but video stops when the screen
is turned off.
]
]

#tak-slide[
= Installation
+ Install *ICU NG Capture* and then the *ICU NG* plugin.
+ Open ICU NG Capture once from the app drawer and grant permissions for
  camera, microphone, and notifications. (Otherwise, it prompts the first time you stream.)
+ Open ATAK and load the plugin in Plugins if prompted.
+ Open the ICU NG panel: under the status it should say
  _"Streaming via ICU NG Capture · continues with screen off"_. If it says
  _"Streaming via plugin"_, ATAK has not found the app: reinstall it and restart ATAK.

#toolbox.side-by-side(columns: (1fr, 1fr))[
  #image("2.png", width: 95%)
][
  The panel is opened from the ATAK tools menu (_ICU NG_) or by long-pressing
  the on-map REC button.
]
]

#tak-slide[
= Streaming from the map: the REC button
#toolbox.side-by-side(columns: (1fr, 1fr, 5fr))[
  #image("1.png", width: 95%)
][
  #image("4b.png", width: 80%)
][
- *Tap*: starts or stops streaming with the saved settings, without opening the panel.
- *Press and release*: opens the panel.
- *Press and drag*: moves the button up or down along the right
  edge. The position is remembered.

States:
- White ring: stopped.
- Amber ring: connecting (or reconnecting).
- Red ring with blinking dot: streaming.
- If it fails (misspelled URL, server down, credentials), a notice appears with
  the reason.
]
]

#tak-slide[
= The panel
#toolbox.side-by-side(columns: (5fr, 4fr))[
  #image("3.png", width: 100%)
][
- *Camera preview* (also while streaming).
- *Status* and active streamer (ICU NG Capture or the plugin).
- *Camera*: back, front, or USB. Can be changed while streaming, without interruption.
- Server *protocol and URL*: `rtsp://`, `rtsps://`, or `srt://`.
- *QR*: reads the server URL from a QR code.
- *Stream / Stop*.
- *KLV*: sends telemetry with the video.

Everything is saved immediately: the REC button streams with whatever the panel shows.
]
]

#tak-slide[
= Quality
#toolbox.side-by-side(columns: (5fr, 4fr))[
  #image("5.png", width: 100%)
][
The *Quality* button opens:
- *Resolution*: 480p, 720p, or 1080p.
- *FPS*: 15, 24, or 30.
- *Bitrate*: _Auto_ (based on resolution) or fixed, from 0.5 to 6 Mbps.
- *Adaptive bitrate*: if the network is constrained (VPN, mobile data), it lowers
  quality instead of cutting out, and raises it when conditions improve.
- *Microphone*: disabled sends silence.

Resolution and fps apply when streaming starts. Bitrate, adaptive bitrate, and microphone
change immediately, even while streaming.
]
]

#tak-slide[
= With the screen off
#toolbox.side-by-side(columns: (1fr, 1fr))[
  #image("6.png", width: 100%)
][
While streaming, ICU NG Capture displays this persistent notification. Streaming continues:
- with the screen off or the device in your pocket,
- with another app in front,
- even if ATAK is closed (reopening it allows the plugin to resume control).

*Stop* in the notification halts streaming from anywhere.

If the server drops or network connectivity is lost, the app retries every few seconds and
automatically resumes streaming once connected.
]
]

#tak-slide[
= Video on the map and KLV telemetry
- While streaming (RTSP or SRT), other ATAK users see your video in their
  *Video* list, with your *callsign* as the name. RTSPS is not announced: ATAK
  does not support opening that type of link.
- A *cone* appears on your marker showing where the camera is pointing. With the front
  camera, it points backwards.
- *KLV (MISB ST 0601)*, once per second: your position and altitude, camera
  pointing direction (heading and pitch, from device sensors), field of view,
  and the ground target point. Compatible viewers (including ATAK itself)
  display this over the video and on the map.
- KLV is transmitted over RTSP/RTSPS. It is not sent over SRT.
- With a *USB camera*, KLV is not sent: camera orientation is unknown.
]

#tak-slide[
= USB camera and QR configuration
#toolbox.side-by-side(columns: (1fr, 1fr))[
*USB camera (UVC)*

Connect the camera, select _USB_ in the panel, and stream. The first time, Android requests
permission for ICU NG Capture to use that camera. If connected after starting,
the image appears automatically upon plugging it in.
][
*Server QR code*

The *QR* button reads the server URL from a QR code (for example, generated
by the video server) and fills in protocol, URL, and, for SRT, the passphrase:

`rtsp://[user:password@]host:port/path`

`rtsps://[user:password@]host:port/path`

`srt://host:port?streamid=publish:path`

A "view" QR code (`read:`) works too: ICU NG always publishes.
]
]

#tak-slide[
= Common issues
- *Panel shows "Streaming via plugin"*: ATAK cannot find ICU NG Capture. Install (or
  reinstall) it and restart ATAK.
- *"Could not open ICU NG Capture"*: the app has been uninstalled or is not responding.
  Open it once from the app drawer.
- *Stays amber*: server is not responding. Check the URL, network, or VPN.
- *"Authentication error"*: incorrect username or password in the URL.
- *"ICU NG Capture does not have camera permission"*: Android Settings → Apps → ICU NG Capture →
  Permissions.
- *No KLV on receiver*: verify that the KLV checkbox is checked, protocol is
  RTSP/RTSPS, and it is not a USB camera. It is not sent without GPS fix.

#toolbox.side-by-side(columns: (3fr, 2fr))[
  #image("7.png", width: 100%)
][
  This manual: Settings → Tool Preferences → Specific Tool Preferences → ICU NG.
]
]
