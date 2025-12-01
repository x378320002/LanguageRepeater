package com.language.repeater.pcm

import android.R.attr.path
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Date: 2025-08-19
 * Time: 19:38
 * Description:
 */
object PcmDataUtil {
  private const val TAG = "PcmDataUtil"

  fun downsampleToWaveform(
    samples: ShortArray,
    targetPoints: Int,
    startTime: Float,
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

  fun readAllPcmToWavePoint(file: File, targetSize: Int): List<WaveformPoint> {
    val total = file.length() / PcmConfig.BYTES_PER_SAMPLE
    val step = (total / targetSize).coerceAtLeast(1)

    val samples = ShortArray(step.toInt())
    val buffer = ByteArray(step.toInt() * 2)
    val result = mutableListOf<WaveformPoint>()

    FileInputStream(file).use { input ->
      var bytesRead: Int
      while (input.read(buffer).also { bytesRead = it } != -1) {
        // s16le -> 2字节小端
        val count = bytesRead / 2
        for (i in 0 until count) {
          val byteIndex = i * 2
          samples[i] = ((buffer[byteIndex + 1].toInt() shl 8) or (buffer[byteIndex].toInt() and 0xFF)).toShort()
        }
        result.addAll(downsampleToWaveform(samples, 1, 0f, 0f))
      }
    }
    return result
  }

  //从指定pcm文件中读取字节数组, 转成short数组
  suspend fun readPcmFile(file: File): ShortArray = withContext(Dispatchers.IO) {
    val totalSamples = (file.length() / PcmConfig.BYTES_PER_SAMPLE).toInt()
    val arr = ShortArray(totalSamples)
    var index = 0
    val buffer = ByteArray(2)
    FileInputStream(file).buffered().use { input ->
      while (input.read(buffer) != -1) {
        // s16le -> 2字节小端
        val byteIndex = 0
        arr[index] = ((buffer[1].toInt() shl 8) or (buffer[0].toInt() and 0xFF)).toShort()
        index++
      }
      arr
    }
  }

  /**
   * 二次处理, downsample, 供ui渲染, 原始数据太大了, 必须经过二次处理
   * 处理方式: 均方根
   */
  suspend fun downSample(data: List<Short>, width: Int): List<Int> = withContext(Dispatchers.IO) {
    if (data.size <= width) {
      return@withContext data.map { it.toInt() }
    }

    // Downsample：减少绘制点数
    val step = data.size.toFloat() / width.toFloat()
    Log.i(TAG, "step:$step")
    val result = mutableListOf<Int>()
    for (i in 0 until width) {
      val start = (i * step).toInt()
      val end = minOf((start + step).toInt(), data.size)

      var sum = 0.0
      var max = Short.MIN_VALUE.toInt()
      var min = Short.MAX_VALUE.toInt()
      for (j in start until end) {
        val v = data[j].toInt()
        sum += v * v
        if (v > max) max = v
        if (v < min) min = v
      }
      val rms = sqrt(sum / (end - start)).toDouble()
      val mixed = (rms * 0.9 + (max - min) * 1 * 0.1)
      result.add(mixed.toInt())
//      result.add(rms.toInt())
    }
    return@withContext result
  }
}