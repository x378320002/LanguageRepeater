package com.language.repeater.pcm

import java.io.File
import java.io.RandomAccessFile


class PCMSegmentLoader(
  private val pcmFile: File,
  private val sampleRate: Int = PcmConfig.PCM_SAMPLE_RATE
) {
  val totalSamples = (pcmFile.length() / PcmConfig.BYTES_PER_SAMPLE).toInt()
  val totalDuration = totalSamples.toFloat() / sampleRate

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
    val samples = ShortArray(actualCount)

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
}