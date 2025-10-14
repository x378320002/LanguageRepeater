package com.language.repeater.pcm

import android.util.Log
import androidx.core.content.ContentProviderCompat.requireContext
import com.konovalov.vad.silero.Vad
import com.konovalov.vad.silero.VadSilero
import com.language.repeater.GlobalConfig
import com.language.repeater.utils.ScreenUtil
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min


class PCMSegmentLoader(
  private val pcmFile: File,
  private val sampleRate: Int = GlobalConfig.PCM_SAMPLE_RATE,
  private val channels: Int = GlobalConfig.PCM_CHANNEL,
  private val bitDepth: Int = GlobalConfig.PCM_BIT_DEPTH,
) {
  private val bytesPerSample = (bitDepth / 8) * channels
  private val fileSize = pcmFile.length()
  val totalSamples = (fileSize / bytesPerSample).toInt()
  val durationSeconds = totalSamples.toFloat() / sampleRate

  var allData: List<WaveformPoint> = listOf()
  //预处理全部的PCM数据, 根据波形做一些基本的分割
  fun prepareAllData() {
//    allData = loadWaveformData(0f, durationSeconds, ScreenUtil.getScreenSize().width)

    // 1. 准备PCM数据
    val startSample = 0
    val sampleCount = (durationSeconds * sampleRate).toInt()
    val pcmData = loadSegmentBySample(startSample, sampleCount)

    //用于绘制波形的数据
    allData = downsampleToWaveform(pcmData,
      ScreenUtil.getScreenSize().width,
      0f,
      1f / sampleRate)

    // 2. 创建VAD分离器
    val segmentation = VoiceSegmentation()

    // 3. 使用默认配置分离
    val segments = segmentation.segment(pcmData)

    Log.i("wangzixu", "检测到 ${segments.size} 句话:")
    segments.forEachIndexed { index, (start, end) ->
      Log.i("wangzixu", "句子 ${index + 1}: [$start, $end]")
    }
  }

  fun loadWaveformData(
    startTimeSeconds: Float,
    durationSeconds: Float,
    targetPoints: Int,
  ): List<WaveformPoint> {
    val startSample = (startTimeSeconds * sampleRate).toInt()
    val sampleCount = (durationSeconds * sampleRate).toInt()
    val samples = loadSegmentBySample(startSample, sampleCount)
    return downsampleToWaveform(samples, targetPoints, startTimeSeconds, durationSeconds/targetPoints)
  }

  private fun loadSegmentBySample(startSample: Int, sampleCount: Int): ShortArray {
    val actualStart = startSample.coerceIn(0, totalSamples)
    val actualCount = sampleCount.coerceAtMost(totalSamples - actualStart)

    if (actualCount <= 0) return shortArrayOf()

    val startByte = actualStart.toLong() * bytesPerSample
    val byteCount = actualCount * bytesPerSample

    val buffer = ByteArray(byteCount)
    val samples = ShortArray(actualCount * channels)

    RandomAccessFile(pcmFile, "r").use { raf ->
      raf.seek(startByte)
      raf.readFully(buffer)
    }

    for (i in samples.indices) {
      val byteIndex = i * 2
      samples[i] = ((buffer[byteIndex + 1].toInt() shl 8) or
          (buffer[byteIndex].toInt() and 0xFF)).toShort()
    }

    return samples
  }

  private fun downsampleToWaveform(
    samples: ShortArray, targetPoints: Int, startTime: Float,
    perPointTime: Float,
  ): List<WaveformPoint> {
    if (samples.isEmpty()) return emptyList()

    val result = mutableListOf<WaveformPoint>()
    val samplesPerPoint = samples.size / targetPoints

    if (samplesPerPoint <= 0) {
      samples.forEachIndexed {i, v ->
        val time = startTime + i * perPointTime
        result.add(WaveformPoint(time, v.toInt(), v.toInt()))
      }
      return result
    }

    for (i in 0 until targetPoints) {
      val startIdx = i * samplesPerPoint
      val endIdx = min((i + 1) * samplesPerPoint, samples.size)

      if (startIdx >= samples.size) break

      var max = Short.MIN_VALUE
      var min = Short.MAX_VALUE

      for (j in startIdx until endIdx) {
        val value = samples[j]
        if (value > max) max = value
        if (value < min) min = value
      }

      val time = startTime + i * perPointTime
      result.add(WaveformPoint(time, min.toInt(), max.toInt()))
    }

    return result
  }
}