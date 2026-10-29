/*
 * ICU NG addition to RootEncoder's rtsp module (Apache-2.0, see NOTICE.md).
 */

package com.pedro.rtsp.rtsp.klv

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Per-frame timing of the video, set by the app before the stream starts
 * (the H.264/H.265 packetizers read it when they are created, on connect).
 *
 * - [fps]: the grid the packetizers snap RTP timestamps to, so a receiver
 *   sees constant frame intervals (WinTAK fails on variable ones).
 * - [precisionTimeStamps]: put a MISB ST 0604 Precision Time Stamp (the
 *   frame's UTC time in microseconds) in an SEI message in every frame,
 *   which STANAG 4609 asks for so the picture and its KLV can be matched.
 */
object FrameTiming {

  @JvmStatic
  @Volatile
  var fps = 30

  @JvmStatic
  @Volatile
  var precisionTimeStamps = false

  // ST 0604 user_data_unregistered UUID: "MISPmicrosectime"
  private val MISP_TIME_UUID = "MISPmicrosectime".toByteArray(Charsets.US_ASCII)
  // ST 0603 time status: lock to a time reference unknown (a phone's
  // clock), no discontinuity, reserved bits 1.
  private const val TIME_STATUS = 0x9F
  private const val SEI_USER_DATA_UNREGISTERED = 5

  /** H.264 SEI NAL unit (header 0x06, no start code) for this UTC time. */
  @JvmStatic
  fun h264Sei(utcMicros: Long): ByteBuffer = sei(byteArrayOf(0x06), utcMicros)

  /** H.265 prefix SEI NAL unit (type 39, no start code) for this UTC time. */
  @JvmStatic
  fun h265Sei(utcMicros: Long): ByteBuffer = sei(byteArrayOf(0x4E, 0x01), utcMicros)

  private fun sei(header: ByteArray, utcMicros: Long): ByteBuffer {
    val payload = ByteArrayOutputStream()
    payload.write(MISP_TIME_UUID)
    payload.write(TIME_STATUS)
    // 8-byte time in four 2-byte groups, each but the last followed by
    // 0xFF so the timestamp alone can never form a start code.
    for (group in 0 until 4) {
      payload.write((utcMicros shr (56 - 16 * group)).toInt() and 0xFF)
      payload.write((utcMicros shr (48 - 16 * group)).toInt() and 0xFF)
      if (group < 3) payload.write(0xFF)
    }
    val rbsp = ByteArrayOutputStream()
    rbsp.write(SEI_USER_DATA_UNREGISTERED)
    rbsp.write(payload.size())
    rbsp.write(payload.toByteArray())
    rbsp.write(0x80) // rbsp_trailing_bits
    val nal = ByteArrayOutputStream()
    nal.write(header)
    nal.write(emulationPrevention(rbsp.toByteArray()))
    return ByteBuffer.wrap(nal.toByteArray())
  }

  // Inserts 0x03 after any 00 00 followed by 00..03 (H.264 7.4.1).
  @JvmStatic
  fun emulationPrevention(rbsp: ByteArray): ByteArray {
    val out = ByteArrayOutputStream(rbsp.size + 4)
    var zeros = 0
    for (b in rbsp) {
      val v = b.toInt() and 0xFF
      if (zeros >= 2 && v <= 3) {
        out.write(3)
        zeros = 0
      }
      out.write(v)
      zeros = if (v == 0) zeros + 1 else 0
    }
    return out.toByteArray()
  }

  /**
   * Maps the encoder's frame timestamps onto UTC. The offset is taken from
   * the first frame and kept, so frame-to-frame spacing is the encoder's
   * own; it is taken again if the two clocks drift a second apart (encoder
   * restart, clock change).
   */
  class Clock {
    private var offsetMicros = Long.MIN_VALUE

    fun utcMicros(frameMicros: Long): Long {
      val now = System.currentTimeMillis() * 1000L
      if (offsetMicros == Long.MIN_VALUE
          || Math.abs(frameMicros + offsetMicros - now) > 1_000_000L) {
        offsetMicros = now - frameMicros
      }
      return frameMicros + offsetMicros
    }
  }
}
