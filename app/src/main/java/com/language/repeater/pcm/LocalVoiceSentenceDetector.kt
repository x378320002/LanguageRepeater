package com.language.repeater.pcm

import android.util.Log
import kotlin.collections.forEachIndexed
import kotlin.collections.lastIndex
import kotlin.collections.map
import kotlin.math.sqrt

private typealias SentenceByTime = Sentence

class LocalVoiceSentenceDetector(
  private val sampleRate: Int = PcmConfig.PCM_SAMPLE_RATE,  // 采样率
) {
  /**
   * 配置参数
   */
  data class SentenceDetectorConfig(
    /** 能量检测窗口大小(毫秒) */
    val windowSizeMs: Int = 30,

    /** 窗口重叠率(0.0-1.0) */
    val overlapRatio: Float = 0.5f,

    //手动设置的threshold, 优先使用
    var manualThreshold: Float = 0f,

    /** 语音能量阈值(0.0-1.0)，低于此值视为静音 */
    var energyThreshold: Float = 0.02f,

    /** 过零率阈值(0.0-1.0)，用于辅助判断 */
    var zcrThreshold: Float = 0.12f,

    /** 最小静音持续时间(毫秒)，低于此值不算句子间隔 */
    val minSilenceDurationMs: Int = 700,

    /** 最小语音持续时间(毫秒)，低于此值不算一句话 */
    val minSpeechDurationMs: Int = 50,

    /** 句子前后的额外补偿扩展 */
    var paddingMs: Int = 150,

    /** 句子前后追加清辅音和低音量的检查计算区间,多少帧 */
    var expandEdgeStartCount: Int = 15, //20帧=300ms
    var expandEdgeEndCount: Int = 30, //20帧=300ms

    var expandEdgeCheckEnergyFactor: Float = 0.2f, //20帧=300ms
  )

  /**
   * 音频的一帧数据
   */
  private class AudioFrame(
    val sampleIndex: Int,      // 对应的采样点索引
    val energy: Float         // 短时能量(0.0-1.0)
  ) {
    var isSpeech: Boolean = false      // 是否为语音
  }

  /**
   * 多帧数据组成的一个声音片段, 记录了帧的开始和结束为止
   */
  private data class Segment(
    var frameStart: Int,
    var frameEnd: Int,
  )

  /**
   * 标记每个句子, 开头和结尾, 采样点坐标
   */
  private data class SentenceBySample(
    var sampleStart: Int,
    var sampleEnd: Int,
  )

  private var config: SentenceDetectorConfig = SentenceDetectorConfig()

  /**
   * 主函数：分离语音片段
   * @param pcmData 原始PCM数据
   * @param config 配置参数
   * @return 每句话的起始和结束采样点位置 List<Pair<起始索引, 结束索引>>
   */
  fun detectSentences(
    pcmData: ShortArray,
    con: SentenceDetectorConfig? = null,
  ): List<SentenceByTime> {
    if (pcmData.isEmpty()) return emptyList()

    if (con != null) {
      config = con
    }

    // 提取音频特征
    val features = extractFeatures(pcmData)

    // 启用自适应阈值，计算阈值
    calculateThreshold(features)

    // 标记语音/静音片段
    markSpeechRegions(features)

    // 中值滤波平滑（去除孤立的噪点）, 复读机的环境没必要开启
    //medianFilter(features)

    // 分离出语音片段
    val segments = extractSegments(features)

    //扩展语音片段的边缘
    expandSegmentEdge(features, segments)

    //片段转成句子
    val sentences = convertToSentence(features, segments, pcmData.lastIndex)

    return sentences.map {
      SentenceByTime(
        it.sampleStart.toFloat() / sampleRate,
        it.sampleEnd.toFloat() / sampleRate
      )
    }
  }

  /**
   * 提取音频特征（能量和过零率）
   */
  private fun extractFeatures(
    pcmData: ShortArray,
  ): List<AudioFrame> {
    val windowSizeSamples = (config.windowSizeMs * sampleRate / 1000)
    val hopSize = (windowSizeSamples * (1 - config.overlapRatio)).toInt()

    val features = mutableListOf<AudioFrame>()
    var position = 0

    while (position + windowSizeSamples <= pcmData.size) {
      val window = pcmData.sliceArray(position until position + windowSizeSamples)

      val energy = calculateEnergy(window)
      //暂时不需要过零率
      //val zcr = calculateZCR(window)
      features.add(
        AudioFrame(
          sampleIndex = position + windowSizeSamples / 2,
          energy = energy
        )
      )

      position += hopSize
    }

    return features
  }

  /**
   * 计算短时能量（归一化到0-1）
   */
  private fun calculateEnergy(samples: ShortArray): Float {
    if (samples.isEmpty()) return 0f

    var sumSquares = 0.0
    samples.forEach { sample ->
      val normalized = sample
      sumSquares += normalized * normalized
    }

    val rms = sqrt(sumSquares / samples.size) / 32768.0
    return rms.toFloat()
  }

  /**
   * 计算过零率（Zero Crossing Rate）
   */
  private fun calculateZCR(samples: ShortArray): Float {
    if (samples.size < 2) return 0f

    var zeroCrossings = 0
    for (i in 1 until samples.size) {
      if ((samples[i] >= 0 && samples[i - 1] < 0) ||
        (samples[i] < 0 && samples[i - 1] >= 0)
      ) {
        zeroCrossings++
      }
    }

    return zeroCrossings.toFloat() / samples.size
  }

  /**
   * 标记语音区域
   */
  private fun markSpeechRegions(features: List<AudioFrame>) {
    // 初步标记
    features.forEachIndexed { index, feature ->
      feature.isSpeech = feature.energy >= config.energyThreshold
//      if (isSpeech) {
//        Log.i(
//          "wangzixu", "==========markSpeechRegions, energy: ${feature.energy}, zcr:${feature.zcr}"
//        )
//      } else {
//        Log.i(
//          "wangzixu", "markSpeechRegions, energy: ${feature.energy}, zcr:${feature.zcr}"
//        )
//      }
    }
  }

  /**
   * 中值滤波平滑
   */
  private fun medianFilter(
    features: List<AudioFrame>,
    windowSize: Int = 6,
  ) {
    val halfWindow = windowSize / 2
    features.forEachIndexed { index, feature ->
      val start = (index - halfWindow).coerceAtLeast(0)
      val end = (index + halfWindow).coerceAtMost(features.size - 1)
      val window = features.subList(start, end + 1)
      val speechCount = window.count { it.isSpeech }
      val isSpeech = speechCount > halfWindow
      feature.isSpeech = isSpeech
    }
  }

  private fun extractSegments(features: List<AudioFrame>): List<Segment> {
    //此处的AudioPoint存的是AudioFeature在features中的坐标
    val segments = mutableListOf<Segment>()

    var begin: Int = -1
    var end: Int = -1
    features.forEachIndexed { index, cur ->
      if (cur.isSpeech) {
        // 检测到语音
        if (begin == -1) {
          begin = index
        }
        end = index
      } else {
        // 检测到静音
        if (begin != -1 && end != -1) {
          // 检查静音持续时间
          val beginSampleIndex = features[begin].sampleIndex
          val endSampleIndex = features[end].sampleIndex
          val silenceDuration = (cur.sampleIndex - endSampleIndex) * 1000 / sampleRate
          if (silenceDuration >= config.minSilenceDurationMs) {
            // 静音足够长，结束当前语音片段, 并检测句子的长度
            val duration = (endSampleIndex - beginSampleIndex) * 1000 / sampleRate
            if (duration >= config.minSpeechDurationMs) {
              segments.add(Segment(begin, end))
            }
            begin = -1
            end = -1
          }
        }
      }
    }

    // 处理最后一个片段
    if (begin != -1 && end != -1) {
      val beginSampleIndex = features[begin].sampleIndex
      val endSampleIndex = features[end].sampleIndex
      val duration = (endSampleIndex - beginSampleIndex) * 1000 / sampleRate
      if (duration >= config.minSpeechDurationMs) {
        segments.add(Segment(begin, end))
      }
    }
    return segments
  }

  /**
   * 计算自适应阈值,
   */
  private fun calculateThreshold(
    features: List<AudioFrame>,
  ) {
    if (features.isEmpty()) {
      return
    }
    if (config.manualThreshold > 0.001f) {
      config.energyThreshold = config.manualThreshold
      Log.i("wangzixu", "calculateThreshold, use manualThreshold: ${config.energyThreshold}")
      return
    }

    val energies = features.map { it.energy }.sorted()
    val averageEnergy = energies
      .subList(0, (energies.size * 0.99f).toInt())
      .average()
    config.energyThreshold = averageEnergy.toFloat()
    Log.i("wangzixu", "calculateThreshold, averageEnergy: $averageEnergy")
  }

  private fun expandSegmentEdge(
    features: List<AudioFrame>,
    segments: List<Segment>,
  ) {
    fun isSpeech(feature: AudioFrame): Boolean {
      return  (feature.energy >= config.energyThreshold * config.expandEdgeCheckEnergyFactor)
    }

    fun expandBegin(seg: Segment, index: Int) {
      val begin = (seg.frameStart - config.expandEdgeStartCount).coerceAtLeast(0)
      val end = seg.frameStart - 1
      for (i in begin..end) {
        val feature = features[i]
        val isSpeech = isSpeech(feature)
        if (isSpeech) {
          seg.frameStart = i
          break
        }
      }
      //seg.first = (seg.first - config.expandEdgeExtraCount).coerceAtLeast(0)
    }

    fun expandEnd(seg: Segment, index: Int) {
      val begin = seg.frameEnd + 1
      val end = (seg.frameEnd + config.expandEdgeEndCount).coerceAtMost(features.lastIndex)
      for (i in end downTo begin) {
        val feature = features[i]
        val isSpeech = isSpeech(feature)
        if (isSpeech) {
          seg.frameEnd = i
          break
        }
      }
      //seg.second = (seg.second + config.expandEdgeExtraCount).coerceAtMost(features.lastIndex)
    }

    for (i in segments.indices) {
      val seg = segments[i]
      expandBegin(seg, i)
      expandEnd(seg, i)
    }
  }

  private fun convertToSentence(
    features: List<AudioFrame>,
    segments: List<Segment>,
    maxSampleIndex: Int
  ): List<SentenceBySample> {
    var sentences =  segments.map {
      val start = features[it.frameStart].sampleIndex
      val end = features[it.frameEnd].sampleIndex
      SentenceBySample(start, end)
    }

    val paddingSample = config.paddingMs * sampleRate / 1000
    sentences.forEach {
      it.sampleStart = (it.sampleStart - paddingSample / 2).coerceAtLeast(0)
      it.sampleEnd = (it.sampleEnd + paddingSample).coerceAtMost(maxSampleIndex)
    }

    sentences = mergeSentence(sentences)
    return sentences
  }

  /**
   * 合并过近的句子
   */
  private fun mergeSentence(tempSentences: List<SentenceBySample>): List<SentenceBySample> {
    if (tempSentences.isEmpty()) {
      return tempSentences
    }

    //再次合并间隔过短的区间
    val sentences = mutableListOf<SentenceBySample>()
    var last = tempSentences[0]
    //val minGapSample = config.minSilenceDurationMs * sampleRate / 1000
    for (i in 1..tempSentences.lastIndex) {
      val cur = tempSentences[i]
      if (cur.sampleStart <= last.sampleEnd) {
        last.sampleEnd = maxOf(cur.sampleEnd, last.sampleEnd)
      } else {
        sentences.add(last)
        last = cur
      }
    }
    sentences.add(last)
    return sentences
  }

}