package com.pedro.rtsp.rtp.packets

import com.pedro.common.frame.MediaFrame
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.RtpConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BasePacketCfrTest {

  private class TestPacket : BasePacket(RtpConstants.clockVideoFrequency, 96) {
    override suspend fun createAndSendPacket(
      mediaFrame: MediaFrame,
      callback: suspend (List<RtpFrame>) -> Unit
    ) {}

    fun testUpdateTs(nanos: Long): Long {
      val buf = ByteArray(12)
      return updateTimeStamp(buf, nanos)
    }
  }

  @Test
  fun testCfrQuantizationAbsorbsJitter() {
    val packet = TestPacket()
    packet.setConstantFrameRate(30) // 30 FPS = 3000 ticks per frame at 90 kHz

    // Frame 0 at 0 ms
    val ts0 = packet.testUpdateTs(0L)

    // Intra-frame packet (e.g. FU-A fragment of Frame 0)
    val ts0_frag = packet.testUpdateTs(0L)
    assertEquals("Intra-frame packets must have identical timestamps", ts0, ts0_frag)

    // Frame 1 with camera jitter: arrives at 33.82 ms (33,820,000 ns)
    val ts1 = packet.testUpdateTs(33_820_000L)
    assertEquals("Frame 1 interval must be exactly 3000 ticks", 3000L, ts1 - ts0)

    // Frame 2 with camera jitter: arrives at 77.64 ms (interval was 43.82 ms!)
    val ts2 = packet.testUpdateTs(77_640_000L)
    assertEquals("Frame 2 interval must be exactly 3000 ticks", 3000L, ts2 - ts1)

    // Frame 3 arrives at 111.46 ms (interval was 33.82 ms)
    val ts3 = packet.testUpdateTs(111_460_000L)
    assertEquals("Frame 3 interval must be exactly 3000 ticks", 3000L, ts3 - ts2)

    // Frame 4 skipped/dropped: Frame 5 arrives at 178.10 ms (~66.6 ms gap)
    val ts5 = packet.testUpdateTs(178_100_000L)
    assertEquals("Dropped frame must advance by exactly 2 slots (6000 ticks) preserving grid", 6000L, ts5 - ts3)
  }

  @Test
  fun testDisabledCfrPassesThroughRaw() {
    val packet = TestPacket()
    // cfrFps is 0 (disabled)
    val ts0 = packet.testUpdateTs(0L)
    val ts1 = packet.testUpdateTs(33_820_000L)
    // 33_820_000 ns * 90_000 / 1_000_000_000 = 3043.8 -> 3043 ticks
    assertEquals(3043L, ts1 - ts0)
  }
}
