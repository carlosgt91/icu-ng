package com.pedro.rtsp.rtsp.klv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameTimingTest {

  private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

  @Test
  fun h264SeiCarriesMispTimeStamp() {
    val t = 0x0005_F3A1_2B3C_4D5EL
    val sei = FrameTiming.h264Sei(t).array()
    val expected = bytes(0x06, 5, 28) +
        "MISPmicrosectime".toByteArray(Charsets.US_ASCII) +
        bytes(0x9F, 0x00, 0x05, 0xFF, 0xF3, 0xA1, 0xFF, 0x2B, 0x3C, 0xFF, 0x4D, 0x5E, 0x80)
    assertArrayEquals(expected, sei)
  }

  @Test
  fun h265SeiHasPrefixSeiHeader() {
    val sei = FrameTiming.h265Sei(1L).array()
    assertEquals(0x4E.toByte(), sei[0])
    assertEquals(0x01.toByte(), sei[1])
    assertEquals(5.toByte(), sei[2])
  }

  @Test
  fun emulationPreventionBreaksStartCodes() {
    assertArrayEquals(bytes(0, 0, 3, 1, 0, 0, 3, 0, 5),
        FrameTiming.emulationPrevention(bytes(0, 0, 1, 0, 0, 0, 5)))
    assertArrayEquals(bytes(0, 0, 4), FrameTiming.emulationPrevention(bytes(0, 0, 4)))
  }

  @Test
  fun clockKeepsEncoderSpacing() {
    val clock = FrameTiming.Clock()
    val first = clock.utcMicros(1_000_000L)
    assertEquals(33_333L, clock.utcMicros(1_033_333L) - first)
  }
}
