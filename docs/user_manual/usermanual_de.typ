#import "@preview/polylux:0.4.0": *
#import "formatting.typ": *

#show: userguide.with(
   plugin-name: "ICU NG",
   plugin-version: "1.1.5",
   platform: "ATAK",
   platform-version: "5.8.0",
)

#tak-slide[
= Was ist ICU NG
#toolbox.side-by-side(columns: (1fr, 8fr))[
#image("plugin_icon.png", width: 90%)
][
ICU NG streamt die Gerätekamera (Video und Audio) über RTSP, RTSPS oder SRT an einen
Videoserver, mit KLV-Telemetrie (MISB ST 0601), und kündigt sie auf der Karte an,
damit andere ATAK-Benutzer sie in ihrer Video-Liste unter Ihrem Callsign sehen können.

Es besteht aus zwei Komponenten, die zusammen installiert werden:
- *Plugin ICU NG* (in ATAK): die REC-Schaltfläche auf der Karte, das Einstellungsfenster und
  die Videoankündigung.
- *App ICU NG Capture*: übernimmt das Streaming. Sie streamt auch bei ausgeschaltetem Bildschirm
  oder mit einer anderen App im Vordergrund weiter – was ein Plugin allein nicht kann.

Ohne ICU NG Capture streamt das Plugin selbst, aber das Video stoppt beim Ausschalten
des Bildschirms.
]
]

#tak-slide[
= Installation
+ Installieren Sie *ICU NG Capture* und danach das Plugin *ICU NG*.
+ Öffnen Sie ICU NG Capture einmalig aus der App-Übersicht und akzeptieren Sie die Berechtigungen für
  Kamera, Mikrofon und Benachrichtigungen. (Andernfalls werden sie beim ersten Streamen abgefragt.)
+ Öffnen Sie ATAK und laden Sie das Plugin unter Plugins, falls dazu aufgefordert.
+ Öffnen Sie das ICU NG-Bedienfeld: Unter dem Status sollte stehen
  _"Stream über ICU NG Capture · läuft bei ausgeschaltetem Bildschirm weiter"_. Steht dort
  _"Stream über Plugin"_, hat ATAK die App nicht gefunden: Installieren Sie sie erneut und starten Sie ATAK neu.

#toolbox.side-by-side(columns: (1fr, 1fr))[
  #image("2.png", width: 95%)
][
  Das Bedienfeld wird über das Werkzeugmenü von ATAK (_ICU NG_) oder durch langes
  Drücken der REC-Schaltfläche auf der Karte geöffnet.
]
]

#tak-slide[
= Streamen von der Karte: die REC-Schaltfläche
#toolbox.side-by-side(columns: (1fr, 1fr, 5fr))[
  #image("1.png", width: 95%)
][
  #image("4b.png", width: 80%)
][
- *Tippen*: Startet oder stoppt den Stream mit den gespeicherten Einstellungen, ohne das Bedienfeld zu öffnen.
- *Gedrückt halten und loslassen*: Öffnet das Bedienfeld.
- *Gedrückt halten und ziehen*: Verschiebt die Schaltfläche entlang des rechten Randes
  nach oben oder unten. Die Position wird gespeichert.

Status:
- Weißer Ring: Gestoppt.
- Bernsteinfarbener Ring: Verbindung wird hergestellt (oder wiederhergestellt).
- Roter Ring mit blinkendem Punkt: Streaming.
- Bei einem Fehler (falsche URL, Server nicht erreichbar, Zugangsdaten) erscheint ein Hinweis mit
  dem Grund.
]
]

#tak-slide[
= Das Bedienfeld
#toolbox.side-by-side(columns: (5fr, 4fr))[
  #image("3.png", width: 100%)
][
- *Kameravorschau* (auch während des Streamings).
- *Status* und wer streamt (ICU NG Capture oder das Plugin).
- *Kamera*: Rückkamera, Frontkamera oder USB. Kann während des Streamings ohne Unterbrechung gewechselt werden.
- *Protokoll und URL* des Servers: `rtsp://`, `rtsps://` oder `srt://`.
- *QR*: Liest die Server-URL aus einem QR-Code.
- *Streamen / Stoppen*.
- *KLV*: Sendet die Telemetrie mit dem Video.

Alles wird sofort gespeichert: Die REC-Schaltfläche streamt mit den Einstellungen des Bedienfelds.
]
]

#tak-slide[
= Qualität
#toolbox.side-by-side(columns: (5fr, 4fr))[
  #image("5.png", width: 100%)
][
Die Schaltfläche *Qualität* öffnet:
- *Auflösung*: 480p, 720p oder 1080p.
- *FPS*: 15, 24 oder 30.
- *Bitrate*: _Auto_ (je nach Auflösung) oder fest, von 0,5 bis 6 Mbps.
- *Adaptive Bitrate*: Reicht die Netzwerkbandbreite nicht aus (VPN, Mobilfunk), wird die
  Qualität verringert statt abzubrechen, und bei besserer Verbindung wieder angehoben.
- *Mikrofon*: Deaktiviert sendet Stille.

Auflösung und fps werden beim Starten des Streams angewendet. Bitrate, adaptive Bitrate und Mikrofon
ändern sich sofort, auch mitten im Stream.
]
]

#tak-slide[
= Bei ausgeschaltetem Bildschirm
#toolbox.side-by-side(columns: (1fr, 1fr))[
  #image("6.png", width: 100%)
][
Während des Streamings zeigt ICU NG Capture diese dauerhafte Benachrichtigung an. Der Stream läuft weiter:
- bei ausgeschaltetem Bildschirm oder mit dem Gerät in der Tasche,
- mit einer anderen App im Vordergrund,
- selbst wenn ATAK geschlossen wird (beim erneuten Öffnen übernimmt das Plugin wieder die Steuerung).

*Stoppen* in der Benachrichtigung beendet das Streaming von überall aus.

Fällt der Server aus oder geht die Netzwerkverbindung verloren, versucht es die App alle paar Sekunden
erneut und streamt automatisch weiter, sobald eine Verbindung besteht.
]
]

#tak-slide[
= Video auf der Karte und KLV-Telemetrie
- Während des Streamings (RTSP oder SRT) sehen andere ATAK-Benutzer Ihr Video in ihrer
  *Video*-Liste mit Ihrem *callsign* als Namen. RTSPS wird nicht angekündigt: ATAK
  kann diese Art von Verknüpfung nicht öffnen.
- Auf Ihrer Markierung erscheint ein *Kegel*, der die Blickrichtung der Kamera anzeigt. Bei der
  Frontkamera zeigt er nach hinten.
- *KLV (MISB ST 0601)*, einmal pro Sekunde: Ihre Position und Höhe, Ausrichtung
  der Kamera (Richtung und Neigung über die Gerätesensoren), Sichtfeld und
  der anvisierte Geländepunkt. Kompatible Betrachter (unter anderem ATAK selbst)
  blenden dies über dem Video und auf der Karte ein.
- KLV wird über RTSP/RTSPS übertragen. Über SRT wird kein KLV gesendet.
- Bei einer *USB-Kamera* wird kein KLV gesendet: Die Ausrichtung der Kamera ist unbekannt.
]

#tak-slide[
= USB-Kamera und QR-Konfiguration
#toolbox.side-by-side(columns: (1fr, 1fr))[
*USB-Kamera (UVC)*

Schließen Sie die Kamera an, wählen Sie _USB_ im Bedienfeld und starten Sie das Streaming. Beim ersten Mal bittet Android um
die Erlaubnis, dass ICU NG Capture diese Kamera verwenden darf. Wird sie erst nach dem Start angeschlossen,
erscheint das Bild automatisch beim Einstecken.
][
*Server-QR-Code*

Die Schaltfläche *QR* liest die Server-URL aus einem QR-Code (z. B. vom
Videoserver generiert) und füllt Protokoll, URL sowie bei SRT die Passphrase aus:

`rtsp://[benutzer:passwort@]host:port/pfad`

`rtsps://[benutzer:passwort@]host:port/pfad`

`srt://host:port?streamid=publish:pfad`

Ein QR-Code zum Anzeigen (`read:`) funktioniert ebenfalls: ICU NG veröffentlicht immer.
]
]

#tak-slide[
= Häufige Probleme
- *Das Bedienfeld zeigt "Stream über Plugin"*: ATAK erkennt ICU NG Capture nicht. Installieren (oder
  reinstallieren) Sie die App und starten Sie ATAK neu.
- *"ICU NG Capture konnte nicht geöffnet werden"*: Die App wurde deinstalliert oder antwortet nicht.
  Öffnen Sie sie einmalig aus der App-Übersicht.
- *Bleibt bernsteinfarben*: Der Server antwortet nicht. Prüfen Sie URL, Netzwerk oder VPN.
- *"Authentifizierungsfehler"*: Benutzername oder Passwort in der URL falsch.
- *"ICU NG Capture hat keine Kameraberechtigung"*: Android-Einstellungen → Apps → ICU NG Capture →
  Berechtigungen.
- *Kein KLV beim Empfänger*: Prüfen Sie, ob das KLV-Kontrollkästchen aktiviert ist, das
  Protokoll RTSP/RTSPS verwendet wird und keine USB-Kamera angeschlossen ist. Ohne GPS-Position wird kein KLV gesendet.

#toolbox.side-by-side(columns: (3fr, 2fr))[
  #image("7.png", width: 100%)
][
  Dieses Handbuch: Einstellungen → Werkzeugeinstellungen → Spezifische Werkzeugeinstellungen → ICU NG.
]
]
