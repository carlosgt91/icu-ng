/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.rtsp.rtp.packets

import com.pedro.common.frame.MediaFrame
import com.pedro.rtsp.rtsp.RtpFrame
import com.pedro.rtsp.utils.CryptoProperties
import com.pedro.rtsp.utils.CryptoUtils
import com.pedro.rtsp.utils.RtpConstants
import com.pedro.rtsp.utils.setLong
import kotlin.experimental.and
import kotlin.experimental.or

/**
 * Created by pedro on 27/11/18.
 */
abstract class BasePacket(private var clock: Long, private val payloadType: Int) {

  protected var channelIdentifier: Int = 0
  private var seq = 0L
  private var ssrc = 0L
  protected val maxPacketSize = RtpConstants.MTU - 28
  protected var cryptoUtils: CryptoUtils? = null
  private var roc = 0
  protected val TAG = "BasePacket"

  // Synthetic CFR Clock Quantizer state
  private var cfrFps: Int = 0
  private var baseRawNanos = -1L
  private var baseRtpTicks = -1L
  private var lastRawNanos = -1L
  private var lastRtpTs = -1L
  private var lastFrameSlot = -1L

  fun setConstantFrameRate(fps: Int) {
    this.cfrFps = fps
  }

  fun setCryptoProperties(cryptoProperties: CryptoProperties) {
    cryptoUtils = CryptoUtils(cryptoProperties)
  }

  abstract suspend fun createAndSendPacket(
    mediaFrame: MediaFrame,
    callback: suspend (List<RtpFrame>) -> Unit
  )

  open fun reset() {
    seq = 0
    ssrc = 0
    baseRawNanos = -1L
    baseRtpTicks = -1L
    lastRawNanos = -1L
    lastRtpTs = -1L
    lastFrameSlot = -1L
  }

  fun setSSRC(ssrc: Long) {
    this.ssrc = ssrc
  }

  protected fun setClock(clock: Long) {
    this.clock = clock
  }

  protected fun getBuffer(size: Int): ByteArray {
    val buffer = ByteArray(size)
    buffer[0] = 0x80.toByte()
    buffer[1] = payloadType.toByte()
    setLongSSRC(buffer, ssrc)
    requestBuffer(buffer)
    return buffer
  }

  protected fun updateTimeStamp(buffer: ByteArray, timestamp: Long): Long {
    val ts = if (cfrFps > 0 && clock == RtpConstants.clockVideoFrequency) {
      quantizeCfrTimestamp(timestamp)
    } else {
      (timestamp / 1000000000L) * clock + ((timestamp % 1000000000L) * clock) / 1000000000L
    }
    buffer.setLong(ts, 4, 8)
    return ts
  }

  private fun quantizeCfrTimestamp(timestampNanos: Long): Long {
    // If multiple packets (e.g. SPS, PPS, IDR slices or FU-A fragments) belong to the same frame:
    if (timestampNanos == lastRawNanos && lastRtpTs != -1L) {
      return lastRtpTs
    }

    val ticksPerFrame = clock / cfrFps
    val rawTicks = (timestampNanos / 1000000000L) * clock + ((timestampNanos % 1000000000L) * clock) / 1000000000L

    if (baseRawNanos == -1L || timestampNanos < baseRawNanos || (timestampNanos - lastRawNanos) > 2_000_000_000L) {
      baseRawNanos = timestampNanos
      baseRtpTicks = rawTicks
      lastFrameSlot = 0L
      lastRtpTs = baseRtpTicks
    } else {
      val deltaNanos = timestampNanos - baseRawNanos
      val targetSlot = (deltaNanos * cfrFps + 500_000_000L) / 1_000_000_000L
      val nextSlot = if (targetSlot <= lastFrameSlot) lastFrameSlot + 1 else targetSlot
      lastFrameSlot = nextSlot
      lastRtpTs = baseRtpTicks + (nextSlot * ticksPerFrame)
    }
    lastRawNanos = timestampNanos
    return lastRtpTs
  }

  protected fun updateSeq(buffer: ByteArray) {
    val currentSeq = ++seq
    buffer.setLong(currentSeq, 2, 4)
    roc = (currentSeq / (RtpConstants.MAX_SEQ_NUMBER + 1)).toInt()
  }

  protected fun markPacket(buffer: ByteArray) {
    buffer[1] = buffer[1] or 0x80.toByte()
  }

  protected fun encryptSize() = if (cryptoUtils != null) RtpConstants.HMAC_SIZE else 0

  protected fun encryptPacket(buffer: ByteArray) {
    cryptoUtils?.let {
      val payloadEndOffset = buffer.size - encryptSize()
      val payload = buffer.copyOfRange(RtpConstants.RTP_HEADER_LENGTH, payloadEndOffset)
      it.encrypt(payload, getIvData(it)).copyInto(buffer, RtpConstants.RTP_HEADER_LENGTH)
      val hmac = it.calculateHmac(buffer.copyOfRange(0, payloadEndOffset), roc)
      hmac.copyInto(buffer, payloadEndOffset)
    }
  }

  private fun setLongSSRC(buffer: ByteArray, ssrc: Long) {
    buffer.setLong(ssrc, 8, 12)
  }

  private fun requestBuffer(buffer: ByteArray) {
    buffer[1] = buffer[1] and 0x7F
  }

  private fun getIvData(cryptoUtils: CryptoUtils): ByteArray {
    val index = ((roc shl 16) or (seq.toInt() and 0xFFFF))
    return cryptoUtils.generateIv(ssrc, index.toLong())
  }
}