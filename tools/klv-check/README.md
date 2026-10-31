# klv-check

Checks the KLV that actually reaches the video server, not just the encoder's
output in a unit test.

```bash
# live: record 30 s from a MediaMTX-based server (tak-video-restreamer) over SRT
tools/klv-check/validate-stream.sh "srt://<server>:8890?streamid=read:<path>" 30

# a capture you already have (.ts, or a raw KLV dump)
tools/klv-check/validate-stream.sh --file capture.ts
```

ICU NG publishes KLV over RTSP as an RFC 6597 (`smpte336m`) track. MediaMTX
remuxes it into the MPEG-TS it serves over SRT, which is what this records.

Two independent checks:

1. **jMISB 1.12** (`KlvCheck.java`): every MISB ST 0601 packet parsed strictly,
   checksum included; Precision Time Stamps (tag 2) present and increasing;
   packet rate; which tags were seen, and the first packet decoded.
2. **stanag4609 0.4.0** verifier, ST 0902 MISMMS profile, on the whole
   capture: TS structure and continuity, ST 1402 KLV carriage and PTS/PCR
   cadence, ST 0604 time stamps on every video frame, MISP codec
   profile/level, and the ST 0902 minimum metadata set (ST 0601 items, the
   ST 0102 security items and the ST 1204 MIIS Core Identifier). Passing
   checks are hidden; `--format html` gives the full report.

Exit code 0 = both clean, 1 = errors, 2 = no KLV found.

The KLV is taken from the TS by its own demuxer (the PMT's KLV PID, PES
payloads), not by `ffmpeg -map 0:d -c copy -f data`: on the public
`Day Flight.mpg` sample that cut the first 5 bytes of the 16-byte key off
every packet, so nothing parsed. (That sample also fails jMISB on its own:
its tag 22 uses an older encoding.)

Needs ffmpeg, Java 17+ and Python 3.10+ (venv). jMISB (MIT, Maven Central)
and stanag4609 (MIT, PyPI) are downloaded into `~/.cache/icung-klv-check` on
first use, pinned to exact versions and SHA-256 sums. Credentials in the URL are hidden in the output.
