package com.language.repeater.pcm

import android.R.attr.path
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Date: 2025-08-19
 * Time: 19:38
 * Description:
 */
object PcmDataUtil {
  private const val TAG = "PcmDataUtil"

  /**
   * 获取 WAV 文件中音频真实数据的起始位置 (Data Offset)
   * 它可以完美解决 FFmpeg 生成 metadata 导致文件头变长产生 "爆音/锯齿" 的问题
   */
  fun getDataOffset(file: File): Long {
    if (!file.exists()) return 44 // 文件不存在的兜底

    try {
      RandomAccessFile(file, "r").use { raf ->
        // 1. 简单校验是否是 RIFF WAVE
        raf.seek(0)
        val riffHeader = ByteArray(4)
        raf.read(riffHeader)
        if (String(riffHeader) != "RIFF") return 44 // 不是标准WAV，按默认处理

        // 2. 跳过 RIFF size (4字节) + WAVE (4字节) = 目前在 12 字节处
        raf.seek(12)

        // 3. 循环遍历 Chunk，直到找到 "data"
        val chunkNameBuf = ByteArray(4)
        val sizeBuf = ByteArray(4)

        while (raf.filePointer < raf.length()) {
          // 读取 Chunk ID (例如 "fmt ", "LIST", "data")
          if (raf.read(chunkNameBuf) != 4) break
          val chunkName = String(chunkNameBuf, Charset.forName("ASCII"))

          // 读取 Chunk Size (小端序)
          if (raf.read(sizeBuf) != 4) break
          val chunkSize = (sizeBuf[0].toInt() and 0xFF) or
              ((sizeBuf[1].toInt() and 0xFF) shl 8) or
              ((sizeBuf[2].toInt() and 0xFF) shl 16) or
              ((sizeBuf[3].toInt() and 0xFF) shl 24)

          if (chunkName == "data") {
            // 找到了！当前的指针位置就是数据的开始
            // 注意：RandomAccessFile 读完 size 后，指针正好指向 data 的内容
            return raf.filePointer
          } else {
            // 不是 data 块 (比如是 metadata 所在的 LIST 块)，直接跳过它
            // 防止越界，用 skipBytes
            raf.skipBytes(chunkSize)
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }

    return 44 // 如果解析失败，回退到标准 44
  }

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
        result.add(WaveformPoint(time, v, v))
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
      result.add(WaveformPoint(time, min, max))
    }

    return result
  }

  fun readAllPcmToWavePoint(file: File, targetSize: Int): List<WaveformPoint> {
    val total = (file.length() - 44) / PcmConfig.BYTES_PER_SAMPLE
    val step = (total / targetSize).coerceAtLeast(1)

    val samples = ShortArray(step.toInt())
    val buffer = ByteArray(step.toInt() * 2)
    val result = mutableListOf<WaveformPoint>()

    FileInputStream(file).use { input ->
      input.skip(44)
      var bytesRead: Int
      while (input.read(buffer).also { bytesRead = it } != -1) {
        //把读出来的byte转成short数组, 再计算这个数组的最大最小值或者平均值, 转成WaveformPoint
        var max = Short.MIN_VALUE
        var min = Short.MAX_VALUE

        // s16le -> 2字节小端
        val count = bytesRead / 2
        for (i in 0 until count) {
          val byteIndex = i * 2
          val value= ((buffer[byteIndex + 1].toInt() shl 8) or (buffer[byteIndex].toInt() and 0xFF)).toShort()
          samples[i] = value
          if (value > max) max = value
          if (value < min) min = value
        }
        result.add(WaveformPoint(0f, min, max))
      }
    }
    return result
  }

  //从指定pcm文件中读取字节数组, 转成short数组
  suspend fun readPcmFile(file: File): ShortArray = withContext(Dispatchers.IO) {
    val totalSamples = ((file.length()-44) / PcmConfig.BYTES_PER_SAMPLE).toInt()
    val arr = ShortArray(totalSamples)
    var index = 0
    val buffer = ByteArray(2)
    FileInputStream(file).buffered().use { input ->
      input.skip(44)
      while (input.read(buffer) != -1) {
        // s16le -> 2字节小端
        val byteIndex = 0
        arr[index] = ((buffer[1].toInt() shl 8) or (buffer[0].toInt() and 0xFF)).toShort()
        index++
      }
      arr
    }
  }
}