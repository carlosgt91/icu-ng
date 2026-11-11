# Provisioning ICU NG with a `.pref` file

Every ICU NG stream setting lives in ATAK's own preferences under a key that
starts with `icung.`. An ATAK `.pref` file can therefore set them for a whole
fleet: import it through the Import Manager, send it inside a data package, or
push it with your MDM. ICU NG picks the new values up immediately, with no ATAK
restart:

- the pane shows them as soon as the import finishes;
- bitrate, adaptive bitrate and microphone also change a running stream;
- everything else applies to the next stream (a live stream keeps its
  connection settings until it is stopped).

A value that cannot be read (a word where a number is expected, an index out of
range) only resets that one setting to its default; the plugin keeps working.
Numbers and booleans are also accepted when written as strings.

Start from [`icu-ng-settings-template.pref`](icu-ng-settings-template.pref) and
delete the entries you do not want to set. In XML, write `&` in a URL as `&amp;`.

## Keys

| Key | Class | Values | Default |
|---|---|---|---|
| `icung.protocol` | Integer | 0 RTSP, 1 RTSPS, 2 SRT | 0 |
| `icung.url` | String | `rtsp://`, `rtsps://` or `srt://` URL, matching the protocol. RTSP credentials go in the URL (`rtsp://user:password@host/path`) | empty |
| `icung.trust_all_certs` | Boolean | RTSPS: accept any server certificate | false |
| `icung.srt_passphrase` | String | SRT: 10–79 characters, empty for no encryption | empty |
| `icung.srt_encryption` | Integer | 0 AES-128, 1 AES-192, 2 AES-256 | 0 |
| `icung.camera` | Integer | 0 back, 1 front, 2 USB | 0 |
| `icung.codec` | Integer | 0 H.264, 1 H.265 | 0 |
| `icung.resolution` | Integer | 0 480p, 1 720p, 2 1080p | 0 |
| `icung.fps` | Integer | 0 15, 1 24, 2 30 fps | 2 |
| `icung.bitrate` | Integer | 0 auto, 1 250 kbps, 2 0.5, 3 1, 4 2, 5 4, 6 6 Mbps | 0 |
| `icung.adaptive_bitrate` | Boolean | lower the bitrate while the link is congested | true |
| `icung.microphone` | Boolean | false sends silence | true |
| `icung.record` | Boolean | also keep an MP4 of every stream on the phone (Movies/ICU NG); needs ICU NG Capture, ignored without it | false |
| `icung.server_feed` | Boolean | list the stream in the Video Feed Manager of every connected TAK Server (TLS connections) | true |
| `icung.klv` | Boolean | MISB ST 0601 KLV track (RTSP/RTSPS only) | true |
| `icung.klv_classification` | Integer | 0 UNCLASSIFIED, 1 RESTRICTED, 2 CONFIDENTIAL, 3 SECRET, 4 TOP SECRET | 0 |
| `icung.klv_country` | String | GENC three-letter code of the classifying country, e.g. `ESP` | the phone's region |
| `icung.klv_mission` | String | KLV mission ID; empty = callsign and UTC date | empty |

A `.pref` file that carries passwords or SRT passphrases is a secret: deliver it
only over channels you trust (MDM, an encrypted data package).
