package com.language.repeater.pcm

import android.util.Log
import kotlin.collections.forEachIndexed
import kotlin.collections.lastIndex
import kotlin.collections.map
import kotlin.math.sqrt

typealias SentenceByTime = Sentence

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

    /** 语音能量阈值(0.0-1.0)，低于此值视为静音 */
    var energyThreshold: Float = 0.02f,

    /** 过零率阈值(0.0-1.0)，用于辅助判断 */
    var zcrThreshold: Float = 0.18f,

    /** 最小静音持续时间(毫秒)，低于此值不算句子间隔 */
    val minSilenceDurationMs: Int = 600,

    /** 最小语音持续时间(毫秒)，低于此值不算一句话 */
    val minSpeechDurationMs: Int = 50,

    /** 句子前后的额外补偿扩展 */
    var paddingMs: Int = 60,

    /** 句子前后追加清辅音和低音量的检查计算区间,多少帧 */
    var expandEdgeCheckCount: Int = 25, //20帧=300ms
  )

  /**
   * 能量和过零率数据
   */
  private class AudioFeature(
    val sampleIndex: Int,      // 对应的采样点索引
    val energy: Float,         // 短时能量(0.0-1.0)
    val zcr: Float,            // 过零率(0.0-1.0)
  ) {
    var isSpeech: Boolean = false      // 是否为语音
  }

  private data class Segment(
    var first: Int,
    var second: Int,
  )

  /**
   * 标记每个句子, 开头和结尾, 采样点坐标
   */
  private data class Sentence(
    var start: Int,
    var end: Int,
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

    // 中值滤波平滑（去除孤立的噪点）, 复读机的环境没必要开启, ASR看需求
    //medianFilter(features)

    // 分离出语音片段
    val segments = extractSegments(features)

    //扩展语音片段的边缘
    expandSegmentEdge(features, segments)

    //片段转成句子
    val sentences = convertToSentence(features, segments, pcmData.lastIndex)

    return sentences.map {
      SentenceByTime(
        it.start.toFloat() / sampleRate,
        it.end.toFloat() / sampleRate
      )
    }
  }

  /**
   * 提取音频特征（能量和过零率）
   */
  private fun extractFeatures(
    pcmData: ShortArray,
  ): List<AudioFeature> {
    val windowSizeSamples = (config.windowSizeMs * sampleRate / 1000)
    val hopSize = (windowSizeSamples * (1 - config.overlapRatio)).toInt()

    val features = mutableListOf<AudioFeature>()
    var position = 0

    while (position + windowSizeSamples <= pcmData.size) {
      val window = pcmData.sliceArray(position until position + windowSizeSamples)

      val energy = calculateEnergy(window)
      val zcr = calculateZCR(window)
      features.add(
        AudioFeature(
          sampleIndex = position + windowSizeSamples / 2,
          energy = energy,
          zcr = zcr
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
  private fun markSpeechRegions(features: List<AudioFeature>) {
    // 初步标记
    features.forEachIndexed { index, feature ->
      val isSpeech =
        (feature.energy > config.energyThreshold) && (feature.zcr < config.zcrThreshold)
//          || (feature.zcr > config.zcrThreshold && feature.energy < config.energyThreshold / 2)
      feature.isSpeech = isSpeech
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
    features: List<AudioFeature>,
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

  private fun extractSegments(features: List<AudioFeature>): List<Segment> {
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
    features: List<AudioFeature>,
  ) {
    if (features.isEmpty()) {
      return
    }

    val sortedList = features.map { it.energy }.sorted()
    val averageEnergy = sortedList.subList(0, (sortedList.size * 0.99f).toInt()).average()
//    val averageEnergy = features.map { it.energy }.average()
    config.energyThreshold = averageEnergy.toFloat()
    Log.i("wangzixu",
      "⭐⭐⭐⭐⭐⭐ detectSentences calculateThreshold, averageEnergy: $averageEnergy"
    )
  }

  private fun expandSegmentEdge(
    features: List<AudioFeature>,
    segments: List<Segment>,
  ) {
    //对句子进行前后扩展, 把被过零率过滤掉的再追加加回来, 并附带一些更低音量的片段
    val energyMin = config.energyThreshold / 4f
    val zcrMax = 0.6f

    fun isSpeech(feature: AudioFeature): Boolean {
      return (feature.energy > energyMin && feature.zcr <= zcrMax)
    }

    fun expandBegin(seg: Segment, index: Int) {
      val begin = (seg.first - config.expandEdgeCheckCount).coerceAtLeast(0)
      val end = seg.first - 1
      for (i in begin..end) {
        val feature = features[i]
        val isSpeech = isSpeech(feature)
        if (isSpeech) {
          seg.first = i
          break
        }
      }
      //seg.first = (seg.first - config.expandEdgeExtraCount).coerceAtLeast(0)
    }

    fun expandEnd(seg: Segment, index: Int) {
      val begin = seg.second + 1
      val end = (seg.second + config.expandEdgeCheckCount).coerceAtMost(features.lastIndex)
      for (i in end downTo begin) {
        val feature = features[i]
        val isSpeech = isSpeech(feature)
        Log.i(
          "wangzixu",
          "⭐⭐⭐⭐⭐⭐ expandEnd, index: ${index}, averageEnergy: $${feature.energy}, feature.zcr: ${feature.zcr}"
        )
        if (isSpeech) {
          seg.second = i
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
    features: List<AudioFeature>,
    segments: List<Segment>,
    maxSampleIndex: Int
  ): List<Sentence> {
    var sentences =  segments.map {
      val start = features[it.first].sampleIndex
      val end = features[it.second].sampleIndex
      Sentence(start, end)
    }

    sentences = mergeSentence(sentences)

    val paddingSample = config.paddingMs * sampleRate / 1000
    sentences.forEach {
      it.start = (it.start - paddingSample).coerceAtLeast(0)
      it.end = (it.end + paddingSample).coerceAtMost(maxSampleIndex)
    }
    return sentences
  }

  /**
   * 合并过近的句子
   */
  private fun mergeSentence(tempSentences: List<Sentence>): List<Sentence> {
    if (tempSentences.isEmpty()) {
      return tempSentences
    }

    //再次合并间隔过短的区间
    val sentences = mutableListOf<Sentence>()
    var last = tempSentences[0]
    val minGapSample = config.minSilenceDurationMs * sampleRate / 1000
    for (i in 1..tempSentences.lastIndex) {
      val cur = tempSentences[i]
      if (cur.start <= (last.end + minGapSample)) {
        last.end = maxOf(cur.end, last.end)
      } else {
        sentences.add(last)
        last = cur
      }
    }
    sentences.add(last)
    return sentences
  }

}