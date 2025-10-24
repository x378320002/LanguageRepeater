package com.language.repeater.pcm

import android.annotation.SuppressLint
import android.util.Log
import com.language.repeater.GlobalConfig
import com.language.repeater.MyApp
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
  private var voiceSegmentsV1 = listOf<Pair<Float, Float>>()
  private var voiceSegmentsV2 = listOf<Pair<Float, Float>>()
  fun getVoiceSegments(): List<Pair<Float, Float>> {
    return voiceSegmentsV2
  }

  //预处理全部的PCM数据, 根据波形做一些基本的分割
  fun prepareAllData() {
//    allData = loadWaveformData(0f, durationSeconds, ScreenUtil.getScreenSize().width)

    //V1版本
//    // 1. 准备PCM数据
//    val startSample = 0
//    val sampleCount = (durationSeconds * sampleRate).toInt()
//    val pcmData = loadSegmentBySample(startSample, sampleCount)
//    //用于绘制波形的数据
//    var time = System.currentTimeMillis()
//    allData = downsampleToWaveform(pcmData,
//      ScreenUtil.getScreenSize().width,
//      0f,
//      1f / sampleRate)
//    // 2. 创建VAD分离器
//    val segmentation = VoiceSentenceDetectorV1()
//    // 3. 使用默认配置分离
//    voiceSegmentsV1 = segmentation.segment(pcmData)
//    Log.i("wangzixu", "耗时 ${(System.currentTimeMillis()-time).toFloat()/1000}")
//    Log.i("wangzixu", "检测到 ${voiceSegmentsV1.size} 句话:")
////    voiceSegmentsV1.forEachIndexed { index, (start, end) ->
////      Log.i("wangzixu", "句子 ${index + 1}: [$start, $end]")
////    }
//    // 5. 查看时间格式
//    val timeStrings = segmentation.segmentsToTimeString(voiceSegmentsV1)
//    timeStrings.forEachIndexed { index, timeStr ->
//      Log.i("wangzixu", "句子 ${index + 1}: $timeStr")
//    }

    //V2版本
    val time = System.currentTimeMillis()
    val detectorV2 = VoiceSentenceDetectorV2(MyApp.instance)
    voiceSegmentsV2 = detectorV2.detectSentences(pcmFile)
    Log.i("wangzixu", "V2耗时 ${(System.currentTimeMillis()-time).toFloat()/1000}")
    Log.i("wangzixu", "V2检测到 ${voiceSegmentsV2.size} 句话:")
    val timeStringsV2 = segmentsToTimeString(voiceSegmentsV2)
    timeStringsV2.forEachIndexed { index, timeStr ->
      Log.i("wangzixu", "句子 ${index + 1}: $timeStr")
    }
  }

  /**
   * 转换为时间格式（用于调试）
   */
  @SuppressLint("DefaultLocale")
  fun segmentsToTimeString(segments: List<Pair<Float, Float>>): List<String> {
    return segments.map { (start, end) ->
      String.format("%.2fs - %.2fs (%.2fs)", start, end, end - start)
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