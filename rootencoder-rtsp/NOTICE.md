# rootencoder-rtsp

Source copied from [RootEncoder](https://github.com/pedroSG94/RootEncoder)
2.8.1, module `rtsp`, Copyright (C) pedroSG94, licensed under the Apache
License 2.0 (`LICENSE.txt`).

Modified for ICU NG to publish a MISB ST 0601 KLV metadata track
(RFC 6597, `application/smpte336m`) next to video and audio:

- `rtsp/klv/KlvMetadata.kt` (new): hook the app uses to feed KLV packets.
- `utils/RtpTracks.kt`: KLV track id.
- `rtsp/commands/CommandsManager.kt`: KLV media in the SDP.
- `rtsp/RtspClient.kt`: SETUP of the KLV track; SPS/PPS wait raised from
  5 s to 30 s (USB cameras are slow to deliver their first frame); two
  field assignments in the connect coroutine moved into private setters
  (no behaviour change).
- `rtsp/RtspSender.kt`: sends KLV as RTP, timestamped with the video frame
  it rides on.

## Static analysis (tak.gov Fortify)

Fortify reports `utils/CryptoUtils.kt` as Weak Cryptographic Hash (HMAC-SHA1)
and Insecure Mode of Operation (AES/ECB). Both are left unchanged on purpose:

- They are what SRTP requires (RFC 3711: HMAC-SHA1-80 authentication, and
  AES Counter Mode, which the class builds block by block on top of a raw AES
  ECB primitive — ECB is never applied to the payload itself).
- ICU NG never turns SRTP on, so this code does not run. RTSPS (TLS) and SRT
  (AES) carry ICU NG's encrypted streams.

Reopen if SRTP is ever enabled, or if a RootEncoder update changes this class.
