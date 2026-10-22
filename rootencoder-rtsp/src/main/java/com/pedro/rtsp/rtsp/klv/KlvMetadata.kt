/*
 * ICU NG addition to RootEncoder's rtsp module (Apache-2.0, see NOTICE.md).
 */

package com.pedro.rtsp.rtsp.klv

/**
 * Hook through which the app feeds KLV (MISB ST 0601) packets to the RTSP
 * client. Process-wide on purpose: RootEncoder's RtspStream keeps its
 * RtspClient private, and ICU NG only ever runs one stream.
 *
 * When [supplier] is set at connect time, the client announces an
 * `application/smpte336m` media (RFC 6597) and sets it up. The sender then
 * asks [KlvSupplier.nextPacket] once per video frame and sends whatever it
 * returns with that frame's RTP timestamp, so metadata and picture share a
 * clock without any extra timing logic. The supplier decides the rate:
 * return null to send nothing for this frame.
 */
object KlvMetadata {

  fun interface KlvSupplier {
    /** Called on the sender thread. Return a complete KLV packet, or null. */
    fun nextPacket(): ByteArray?
  }

  @JvmStatic
  @Volatile
  var supplier: KlvSupplier? = null

  /** RTP payload type for the KLV media (dynamic range, after video 96 / audio 97). */
  const val PAYLOAD_TYPE = 98
  const val CLOCK = 90000
}
