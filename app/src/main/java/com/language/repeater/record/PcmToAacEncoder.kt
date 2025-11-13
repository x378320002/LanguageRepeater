package com.language.repeater.record

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * PCM 编码器 (消费者)
 * 职责：消费一个 Channel 的 PCM 数据, 编码为 .m4a (AAC) 文件。
 * 这是一个阻塞式消费者, 它会在自己的协程上运行。
 */
class PcmToAacEncoder(
  val mediaCacheSize: Int = 1024 * 8
) {

  companion object {
    private const val TAG = "SearchVoiceV3ViewModel"
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_COUNT_OUT = 1
    private const val AAC_MIME_TYPE = "audio/mp4a-latm"
    private const val AAC_BIT_RATE = 64000
    private const val CODEC_TIMEOUT_US = 10000L
  }

  private var mediaCodec: MediaCodec? = null
  private var mediaMuxer: MediaMuxer? = null
  private var muxerTrackIndex: Int = -1
  private var pcmPresentationTimeUs: Long = 0L
  private var isMuxerStarted = false

  private val codecBufferInfo = MediaCodec.BufferInfo()

  private enum class DrainResult { CONTINUE, EOS_REACHED }

  @Throws(IOException::class)
  private fun setupMediaCodecAndMuxer(aacFile: File) {
    //muxer
    mediaMuxer = MediaMuxer(aacFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    isMuxerStarted = false

    val audioFormat =
      MediaFormat.createAudioFormat(AAC_MIME_TYPE, SAMPLE_RATE, CHANNEL_COUNT_OUT).apply {
        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        setInteger(MediaFormat.KEY_BIT_RATE, AAC_BIT_RATE)
        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mediaCacheSize)
      }
    mediaCodec = MediaCodec.createEncoderByType(AAC_MIME_TYPE)
    mediaCodec!!.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

    muxerTrackIndex = -1
    pcmPresentationTimeUs = 0L
  }

  /**
   * 核心 API: 消费一个 Channel, 编码成一个文件
   * @param pcmChannel 生产者提供的 PCM 数据通道
   * @param aacFile 目标输出的 .m4a 文件
   */
  suspend fun encode(pcmChannel: ReceiveChannel<ByteArray>, aacFile: File) {
    try {
      setupMediaCodecAndMuxer(aacFile)
      mediaCodec?.start()
      // 1. 消费来自 Channel 的数据
      //    当 Channel 关闭时, 循环会正常结束
      //    当 Channel 异常关闭时, 循环会抛出异常
      for (pcmData in pcmChannel) {
        // 这是一个阻塞式循环, 直到数据被喂进去
        feedPcmToCodec(pcmData)
      }

      // 2. Channel 已经正常关闭 (生产者停止) - 发送 EOS 信号
      feedEosToCodec()

      Log.i(TAG, "Encoding job complete.")
    } catch (e: Exception) {
      Log.i(TAG, "Encoding job failed: ${e.message}")
      throw e // 向上抛出, 让 coroutineScope 捕获
    } finally {
      cleanupResources()
    }
  }

  private fun cleanupResources() {
    Log.i(TAG, "Cleaning up encoder resources...")
    try {
      mediaCodec?.stop()
      mediaCodec?.release()
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping MediaCodec", e)
    }
    mediaCodec = null

    try {
      if (isMuxerStarted) {
        mediaMuxer?.stop()
      }
      mediaMuxer?.release()
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping MediaMuxer", e)
    }
    mediaMuxer = null
    isMuxerStarted = false
  }

  /**
   * 持续尝试将一块 pcmData 喂给编码器 (阻塞式)
   */
  private suspend fun feedPcmToCodec(pcmData: ByteArray) {
    val mediaCodec = mediaCodec ?: return
    var inputDone = false
    while (!inputDone && coroutineContext.isActive) {
      val inputIndex = mediaCodec.dequeueInputBuffer(CODEC_TIMEOUT_US)
      if (inputIndex >= 0) {
        // 成功获取输入缓冲区
        queueInputBuffer(inputIndex, pcmData, pcmData.size)
        inputDone = true
      }

      // 持续排空输出, 以便为输入腾出空间
      drainCodecOutput(codecBufferInfo)
    }
  }

  /**
   * 发送流结束 (EOS) 信号给编码器 (阻塞式)
   */
  private suspend fun feedEosToCodec() {
    val mediaCodec = mediaCodec ?: return
    var eosSent = false
    while (!eosSent && coroutineContext.isActive) {
      val inputIndex = mediaCodec.dequeueInputBuffer(CODEC_TIMEOUT_US)
      if (inputIndex >= 0) {
        queueInputBuffer(inputIndex, null, 0, isEos = true)
        eosSent = true
      }

      //持续排空输出, 以便为输入腾出空间
      var result  = drainCodecOutput(codecBufferInfo)
      // 排干编码器中剩余的最后几帧, 已经发送了eos, 最后必然会收到EOS_REACHED
      while (eosSent && result != DrainResult.EOS_REACHED && coroutineContext.isActive) {
        result = drainCodecOutput(codecBufferInfo)
      }
    }
  }

  /**
   * 将数据块排入输入队列
   */
  private fun queueInputBuffer(index: Int, data: ByteArray?, size: Int, isEos: Boolean = false) {
    val mediaCodec = mediaCodec ?: return
    val inputData = mediaCodec.getInputBuffer(index)
    inputData?.clear()

    if (isEos) {
      mediaCodec.queueInputBuffer(
        index,
        0,
        0,
        pcmPresentationTimeUs,
        MediaCodec.BUFFER_FLAG_END_OF_STREAM
      )
    } else if (data != null && size > 0) {
      inputData?.put(data, 0, size)
      val pts = (size.toDouble() / (SAMPLE_RATE * CHANNEL_COUNT_OUT * 2) * 1_000_000).toLong()
      pcmPresentationTimeUs += pts
      mediaCodec.queueInputBuffer(index, 0, size, pcmPresentationTimeUs, 0)
    }
  }

  /**
   * 排出一个输出缓冲区
   */
  private fun drainCodecOutput(bufferInfo: MediaCodec.BufferInfo): DrainResult {
    val mediaCodec = mediaCodec ?: return DrainResult.EOS_REACHED
    val mediaMuxer = mediaMuxer ?: return DrainResult.EOS_REACHED
    val outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
    return when (outputIndex) {
      MediaCodec.INFO_TRY_AGAIN_LATER -> DrainResult.CONTINUE
      MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
        val newFormat = mediaCodec.outputFormat
        muxerTrackIndex = mediaMuxer.addTrack(newFormat)
        mediaMuxer.start()
        isMuxerStarted = true
        DrainResult.CONTINUE
      }

      else -> {
        if (outputIndex < 0) return DrainResult.CONTINUE

        val outputData = mediaCodec.getOutputBuffer(outputIndex)
        if (outputData != null
          && isMuxerStarted
          && bufferInfo.size > 0
          && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0)
        ) {
          // 写入 .m4a 文件
          outputData.position(bufferInfo.offset)
          outputData.limit(bufferInfo.offset + bufferInfo.size)
          mediaMuxer.writeSampleData(muxerTrackIndex, outputData, bufferInfo)
        }

        mediaCodec.releaseOutputBuffer(outputIndex, false)

        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
          return DrainResult.EOS_REACHED
        }

        DrainResult.CONTINUE
      }
    }
  }
}