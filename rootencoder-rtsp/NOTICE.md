# rootencoder-rtsp

Source copied from [RootEncoder](https://github.com/pedroSG94/RootEncoder)
2.8.1, module `rtsp`, Copyright (C) pedroSG94, licensed under the Apache
License 2.0 (`LICENSE.txt`).

Modified for ICU NG to publish a MISB ST 0601 KLV metadata track
(RFC 6597, `application/smpte336m`) next to video and audio:

- `rtsp/klv/KlvMetadata.kt` (new): hook the app uses to feed KLV packets.
- `utils/RtpTracks.kt`: KLV track id.
- `rtsp/commands/CommandsManager.kt`: KLV media in the SDP.
- `rtsp/RtspClient.kt`: SETUP of the KLV track.
- `rtsp/RtspSender.kt`: sends KLV as RTP, timestamped with the video frame
  it rides on.
