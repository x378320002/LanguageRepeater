package com.language.repeater.pcm

import android.util.Log
import java.io.File
import java.io.RandomAccessFile


class PCMSegmentLoader(
  private val pcmFile: File,
  private val sampleRate: Int = PcmConfig.PCM_SAMPLE_RATE
) {
  //val offset = PcmDataUtil.getDataOffset(pcmFile).also {
  //  Log.i("wangzixu_PCMSegmentLoader", "PCMSegmentLoader getDataOffset : $it")
  //}
  val totalSamples = ((pcmFile.length() - PcmConfig.WAV_HEAD_SIZE) / PcmConfig.BYTES_PER_SAMPLE).toInt()
  val totalDuration = totalSamples.toFloat() / sampleRate

  /** 波形数据缓存：时间窗口 -> 波形数据 */
  val waveformCache = mutableMapOf<Int, List<WaveformPoint>>()

  fun loadWaveformData(
    startTimeSeconds: Float,
    durationSeconds: Float,
    targetPoints: Int,
  ): List<WaveformPoint> {
    val startSample = (startTimeSeconds * sampleRate).toInt()
    val sampleCount = (durationSeconds * sampleRate).toInt()
    val samples = loadSegmentBySample(startSample, sampleCount)
    return PcmDataUtil.downsampleToWaveform(samples, targetPoints, startTimeSeconds, durationSeconds/targetPoints)
  }

  private fun loadSegmentBySample(startSample: Int, sampleCount: Int): ShortArray {
    val actualStart = startSample.coerceIn(0, totalSamples)
    val actualCount = sampleCount.coerceAtMost(totalSamples - actualStart)

    if (actualCount <= 0) return shortArrayOf()

    val startByte = actualStart.toLong() * PcmConfig.BYTES_PER_SAMPLE
    val byteCount = actualCount * PcmConfig.BYTES_PER_SAMPLE

    val buffer = ByteArray(byteCount)

    RandomAccessFile(pcmFile, "r").use { raf ->
      raf.seek(startByte + PcmConfig.WAV_HEAD_SIZE)
      raf.readFully(buffer)
    }

    val samples = ShortArray(actualCount)
    for (i in samples.indices) {
      val byteIndex = i * 2
      samples[i] = ((buffer[byteIndex + 1].toInt() shl 8) or
          (buffer[byteIndex].toInt() and 0xFF)).toShort()
    }

    return samples
  }
}