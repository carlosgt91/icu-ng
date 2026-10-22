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

package com.pedro.rtsp.rtsp

import com.pedro.rtsp.rtsp.klv.KlvMetadata

import android.util.Log
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.common.base.BaseSender
import com.pedro.common.frame.MediaFrame
import com.pedro.common.onMainThread
import com.pedro.common.socket.base.SocketType
import com.pedro.common.socket.base.TcpStreamSocket
import com.pedro.common.validMessage
import com.pedro.rtsp.rtcp.BaseSenderReport
import com.pedro.rtsp.rtp.packets.AacPacket
import com.pedro.rtsp.rtp.packets.Av1Packet
import com.pedro.rtsp.rtp.packets.BasePacket
import com.pedro.rtsp.rtp.packets.G711Packet
import com.pedro.rtsp.rtp.packets.H264Packet
import com.pedro.rtsp.rtp.packets.H265Packet
import com.pedro.rtsp.rtp.packets.OpusPacket
import com.pedro.rtsp.rtp.packets.Vp8Packet
import com.pedro.rtsp.rtp.packets.Vp9Packet
import com.pedro.rtsp.rtp.sockets.BaseRtpSocket
import com.pedro.rtsp.rtp.sockets.RtpSocketTcp
import com.pedro.rtsp.rtsp.commands.CommandsManager
import com.pedro.rtsp.utils.RtpConstants
import kotlinx.coroutines.isActive
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Random

/**
 * Created by pedro on 7/11/18.
 */
class RtspSender(
  connectChecker: ConnectChecker,
  private val commandsManager: CommandsManager
): BaseSender(connectChecker, "RtspSender") {

  private var videoPacket: BasePacket = H264Packet(commandsManager.rtpTracks.trackVideo)
  private var audioPacket: BasePacket = AacPacket(commandsManager.rtpTracks.trackAudio)
  private var rtpSocket: BaseRtpSocket? = null
  private var baseSenderReport: BaseSenderReport? = null

  // ICU NG: KLV metadata track (see KlvMetadata)
  @Volatile
  var klvEnabled = false
  private var klvSeq = 0
  private var klvSsrc = 0L

  @Throws(IOException::class)
  fun setSocketsInfo(
    socketType: SocketType,
    protocol: Protocol, host: String, timeout: Long,
    videoSourcePorts: Array<Int?>, audioSourcePorts: Array<Int?>,
    videoServerPorts: Array<Int?>, audioServerPorts: Array<Int?>,
  ) {
    rtpSocket = BaseRtpSocket.getInstance(commandsManager.rtpTracks, socketType, protocol, host, timeout, videoSourcePorts[0], audioSourcePorts[0], videoServerPorts[0], audioServerPorts[0])
    baseSenderReport = BaseSenderReport.getInstance(commandsManager.rtpTracks, socketType, protocol, host, timeout, videoSourcePorts[1], audioSourcePorts[1], videoServerPorts[1], audioServerPorts[1])
  }

  @Throws(IOException::class)
  suspend fun setSocket(socket: TcpStreamSocket) {
    rtpSocket?.setSocket(socket)
    baseSenderReport?.setSocket(socket)
  }

  override fun setVideoInfo(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
    videoPacket = when (commandsManager.videoCodec) {
      VideoCodec.H264 -> {
        if (pps == null) throw IllegalArgumentException("pps can't be null with h264")
        H264Packet(commandsManager.rtpTracks.trackVideo).apply { sendVideoInfo(sps, pps) }
      }
      VideoCodec.H265 -> {
        if (vps == null || pps == null) throw IllegalArgumentException("pps or vps can't be null with h265")
        H265Packet(commandsManager.rtpTracks.trackVideo).apply { sendVideoInfo(sps, pps, vps) }
      }
      VideoCodec.AV1 -> Av1Packet(commandsManager.rtpTracks.trackVideo)
      VideoCodec.VP8 -> Vp8Packet(commandsManager.rtpTracks.trackVideo)
      VideoCodec.VP9 -> Vp9Packet(commandsManager.rtpTracks.trackVideo)
    }
  }

  override fun setAudioInfo(sampleRate: Int, isStereo: Boolean) {
    audioPacket = when (commandsManager.audioCodec) {
      AudioCodec.G711 -> G711Packet(commandsManager.rtpTracks.trackAudio)
      AudioCodec.AAC, AudioCodec.HE_AAC -> AacPacket(commandsManager.rtpTracks.trackAudio).apply { setAudioInfo(sampleRate) }
      AudioCodec.OPUS -> OpusPacket(commandsManager.rtpTracks.trackAudio)
    }
  }

  override suspend fun onRun() {
    val ssrcVideo = Random().nextInt().toLong()
    val ssrcAudio = Random().nextInt().toLong()
    baseSenderReport?.setSSRC(ssrcVideo, ssrcAudio)
    videoPacket.setSSRC(ssrcVideo)
    audioPacket.setSSRC(ssrcAudio)
    klvSsrc = Random().nextInt().toLong() and 0xFFFFFFFFL
    klvSeq = 0
    val isTcp = rtpSocket is RtpSocketTcp
    while (scope.isActive && running) {
      val error = runCatching {
        consumeFrame { mediaFrame ->
          getRtpPackets(mediaFrame) { rtpFrames ->
            var size = 0L
            var isVideo = false
            rtpFrames.forEach { rtpFrame ->
              rtpSocket?.sendFrame(rtpFrame)
              //4 is tcp header length
              val packetSize = (if (isTcp) rtpFrame.length + 4 else rtpFrame.length).toLong()
              bytesSend.addAndGet(packetSize)
              bytesSendPerSecond.addAndGet(packetSize)
              size += packetSize
              isVideo = rtpFrame.isVideoFrame(commandsManager.rtpTracks.trackVideo)
              if (isVideo) videoFramesSent.incrementAndGet()
              else audioFramesSent.incrementAndGet()
              if (baseSenderReport?.update(rtpFrame) == true) {
                //4 is tcp header length
                val reportSize = (if (isTcp) RtpConstants.REPORT_PACKET_LENGTH + 4 else RtpConstants.REPORT_PACKET_LENGTH).toLong()
                bytesSend.addAndGet(reportSize)
                bytesSendPerSecond.addAndGet(reportSize)
                if (isEnableLogs) Log.i(TAG, "wrote report")
              }
            }
            if (isVideo && klvEnabled && rtpFrames.isNotEmpty()) {
              size += sendKlv(rtpFrames[0].timeStamp, isTcp)
            }
            rtpSocket?.flush()
            if (isEnableLogs) {
              val type = if (isVideo) "Video" else "Audio"
              Log.i(TAG, "wrote $type packet, size $size")
            }
          }
        }
      }.exceptionOrNull()
      if (error != null) {
        onMainThread {
          connectChecker.onConnectionFailed("Error send packet, ${error.validMessage()}")
        }
        Log.e(TAG, "send error: ", error)
        running = false
        return
      }
    }
  }

  // ICU NG: one KLV packet as one RTP packet (RFC 6597: a KLV unit that
  // fits is sent whole, marker bit set), stamped with the video frame's RTP
  // timestamp. Bypasses baseSenderReport on purpose: it only knows video
  // and audio and would count KLV as audio.
  private suspend fun sendKlv(videoTimestamp: Long, isTcp: Boolean): Long {
    val klv = KlvMetadata.supplier?.nextPacket() ?: return 0
    val maxPayload = RtpConstants.MTU - 28 - RtpConstants.RTP_HEADER_LENGTH
    if (klv.isEmpty() || klv.size > maxPayload) return 0
    val buffer = ByteArray(RtpConstants.RTP_HEADER_LENGTH + klv.size)
    buffer[0] = 0x80.toByte()
    buffer[1] = (0x80 or KlvMetadata.PAYLOAD_TYPE).toByte()
    val seq = (++klvSeq) and 0xFFFF
    buffer[2] = (seq shr 8).toByte()
    buffer[3] = seq.toByte()
    for (i in 0 until 4) {
      buffer[4 + i] = (videoTimestamp shr (24 - 8 * i)).toByte()
      buffer[8 + i] = (klvSsrc shr (24 - 8 * i)).toByte()
    }
    klv.copyInto(buffer, RtpConstants.RTP_HEADER_LENGTH)
    rtpSocket?.sendFrame(RtpFrame(buffer, videoTimestamp, buffer.size, commandsManager.rtpTracks.trackKlv))
    val packetSize = (if (isTcp) buffer.size + 4 else buffer.size).toLong()
    bytesSend.addAndGet(packetSize)
    bytesSendPerSecond.addAndGet(packetSize)
    return packetSize
  }

  override suspend fun stopImp(clear: Boolean) {
    baseSenderReport?.reset()
    baseSenderReport?.close()
    rtpSocket?.close()
    audioPacket.reset()
    videoPacket.reset()
  }

  private suspend fun getRtpPackets(mediaFrame: MediaFrame?, callback: suspend (List<RtpFrame>) -> Unit) {
    if (mediaFrame == null) return
    when (mediaFrame.type) {
      MediaFrame.Type.VIDEO -> videoPacket.createAndSendPacket(mediaFrame) { callback(it) }
      MediaFrame.Type.AUDIO -> audioPacket.createAndSendPacket(mediaFrame) { callback(it) }
    }
  }
}