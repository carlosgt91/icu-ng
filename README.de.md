[English](README.md) | [Español](README.es.md) | [Deutsch](README.de.md) | [Українська](README.uk.md)

ATAK-Plugin — ICU NG

Kamera-Streaming für ATAK: Video und Audio von den Kameras des Geräts (Rückseite,
Vorderseite oder USB/UVC) an einen RTSP-, RTSPS- oder SRT-Videoserver, mit
MISB ST 0601 KLV-Telemetrie, auf der Karte angekündigt, sodass andere
ATAK-Benutzer den Stream aus ihrer Videoliste öffnen können. Ein Ersatz für
TAK ICU direkt in ATAK.

Benutzerhandbuch: in ATAK unter Einstellungen → Werkzeugeinstellungen →
Spezifische Werkzeugeinstellungen → ICU NG (Quelle: docs/user_manual/).

_________________________________________________________________
ZWECK UND FUNKTIONSUMFANG

ICU NG ermöglicht es einem Operator, das Bild der Gerätekamera zu streamen, ohne
ATAK zu verlassen und ohne den Stream jedes Mal manuell in einer anderen App
einzurichten. Der Stream wird mit dem Rufzeichen des Operators benannt und über
den Video-Verbindungsmanager von ATAK angekündigt, sodass das restliche Team ihn
direkt in ATAK ansehen kann.

- REC-Taste auf der Karte: Antippen zum Starten/Stoppen mit den gespeicherten
  Einstellungen, langes Drücken für das Einstellungsfeld, langes Drücken und
  Ziehen zum Verschieben entlang des rechten Bildschirmrands.
- Einstellungsfeld mit Live-Vorschau: Kamera, Protokoll, Server-URL (eingegeben
  oder per QR-Code eingescannt), SRT-Passphrase und Verschlüsselung, Qualität
  (Auflösung, fps, Bitrate, adaptive Bitrate, Mikrofon).
- MISB ST 0601 KLV über RTSP/RTSPS, einmal pro Sekunde: Sensorposition und
  -höhe, Kamera-Azimut und -Elevation (Rotationsvektorsensor), Sichtfeld,
  Bildmittelpunkt auf dem Gelände (ATAK-Höhendaten), Rufzeichen.
- Sichtfeldkegel des Sensors auf dem eigenen Marker während des Streamings.
- Stream-Taste im Radialmenü des eigenen Markers.

Hintergrund-Streaming. Ein Plugin läuft innerhalb des ATAK-Prozesses, und ATAKs
einziger Vordergrunddienst ist vom Typ "location", weshalb Android den
Kamerazugriff des Plugins stoppt, sobald der Bildschirm ausgeschaltet wird. Um
bei ausgeschaltetem Bildschirm weiterzustreamen, kann die Kameraerfassung von
einer optionalen Begleit-App, ICU NG Capture (capture/ im Quell-Repository; eine
reguläre Android-App, nicht Teil dieser Einreichung), übernommen werden, die
einen Kamera-/Mikrofon-Vordergrunddienst bereitstellt. Das Plugin bleibt die
vollständige Benutzeroberfläche und steuert die App; ohne installierte
Begleit-App streamt das Plugin eigenständig, jedoch nur im Vordergrund.

_________________________________________________________________
STATUS

Version 1.0. Verifiziert auf ATAK-CIV 5.8.0.7 (Entwickler-Build): Emulator
(Android 14) für alle Funktionen, einschließlich des civRelease-Builds;
praxiserprobt auf einem Samsung-Tablet (Android 16) beim Streaming über RTSP zu
einem MediaMTX-basierten Restreamer über ein VPN. Bisher noch nicht auf Hardware
getestet: eine USB-Kamera (UVC).

Vorbereitet für die Einreichung durch Dritte auf tak.gov.

_________________________________________________________________
ANSPRECHPARTNER

Carlos (carlosgt91)
https://github.com/carlosgt91/atak-plugins/issues

_________________________________________________________________
ERFORDERLICHE PORTS

(Wichtig für ATO, Netzwerkkonfiguration und Sicherheitsüberprüfungen)

  Ausschließlich ausgehend und nur während der Operator streamt, zu dem vom
  Operator konfigurierten Videoserver (URL im Bedienfeld eingegeben oder aus
  einem QR-Code gelesen):

  - RTSP:  TCP zum RTSP-Port des Servers (MediaMTX-Standard: 8554).
  - RTSPS: TCP + TLS zum RTSPS-Port des Servers (MediaMTX-Standard: 8555).
  - SRT:   UDP zum SRT-Port des Servers (MediaMTX-Standard: 8890).

  Keine eingehenden Ports und keine Listening-Sockets. Kein eigener
  Datenverkehr zum oder vom TAK-Server: Der Stream wird über die ATAK-eigenen
  APIs als Videoverbindung angekündigt und von ATAK im CoT des eigenen Markers
  übertragen. Außerhalb aktiver Streams werden keine Daten übertragen; ohne
  Netzwerkverbindung kann das Plugin schlichtweg keine Verbindung aufbauen.

  Lokale IPC mit der Begleit-App ICU NG Capture, sofern installiert:
  Aktivitätsstarts (start/stop) und ein gebundener AIDL-Dienst (Status,
  Vorschauoberfläche, KLV-Pakete). Die App akzeptiert ausschließlich Aufrufe aus
  dem ATAK-Paket.

_________________________________________________________________
ERFORDERLICHE HARDWARE

  Android-Gerät mit Unterstützung für ATAK-CIV 5.8, mit Kamera. Berechtigungen
  für Kamera und Mikrofon (von ATAK bzw. von ICU NG Capture, falls installiert).
  Keine Speichernutzung über die Standardeinstellungen von ATAK hinaus.

_________________________________________________________________
UNTERSTÜTZTE HARDWARE

  Integrierte Kameras des Geräts. USB-Video-Class-Kameras (UVC) über USB-OTG. Ein
  Videoserver mit Unterstützung für RTSP/RTSPS/SRT-Publishing, z. B. MediaMTX
  oder raytheonbbn/tak-video-restreamer.

_________________________________________________________________
KOMPILIERUNG

  Standard-Build eines ATAK-Plugins. sdk.path in local.properties auf ein
  entpacktes ATAK CIV SDK setzen, dann:

      ./gradlew assembleCivDebug
      ./gradlew assembleCivRelease

  ext.ATAK_VERSION in app/build.gradle legt die Zielversion von ATAK fest. Das
  Projekt umfasst zwei Module: app (das Plugin) und rootencoder-rtsp (siehe
  unten). Im Quell-Repository ist ein drittes Modul, capture (die Begleit-App),
  enthalten, sofern vorhanden; es wird zum Erstellen des Plugins nicht benötigt.

_________________________________________________________________
ENTWICKLERHINWEISE

  - Streaming basiert auf RootEncoder 2.8.1 (pedroSG94, Apache-2.0),
    StreamBase-API. rootencoder-rtsp/ ist eine Kopie des rtsp-Moduls von
    RootEncoder, modifiziert zur Ankündigung und Übertragung einer KLV-Spur
    (RFC 6597, application/smpte336m) parallel zu Video und Audio; siehe
    rootencoder-rtsp/NOTICE.md. Die App ersetzt das veröffentlichte
    rtsp-Artefakt hierdurch.
  - USB-Kameras: UVCAndroid 1.0.13 (herohan). Dessen nativer Code ruft
    Java-Methoden namentlich auf (daher die entsprechenden Proguard-Keep-Regeln).
  - QR-Dekodierung: ZXing-Core.
  - Einstellungen werden in den Standardeinstellungen von ATAK gespeichert
    (Schlüssel icung.*), sodass die REC-Taste streamen kann, ohne dass das
    Einstellungsfeld jemals geöffnet werden musste.
  - versionCode wird aus PLUGIN_VERSION und ATAK_VERSION abgeleitet (nicht aus
    git), sodass Builds aus diesem Archiv eine aussagekräftige Versionsnummer
    tragen.
  - Schnittstelle zur Begleit-App: app/src/main/aidl und CaptureLink.java auf
    Plugin-Seite; identische AIDL-Dateien in der App.
