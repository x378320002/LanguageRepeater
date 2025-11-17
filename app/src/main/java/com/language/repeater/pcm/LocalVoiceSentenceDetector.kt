package com.language.repeater.pcm

import android.util.Log
import kotlin.math.sqrt
/**
 * 语音断句分离器
 * 从PCM音频数据中识别出每句话的起始和结束位置
 * 好问题！语音断句（Voice Activity Detection, VAD）是个经典问题。我来帮你实现一个实用的算法：我实现了一个完整的语音断句算法！核心特点：
 *
 * ## 🎯 算法原理
 *
 * **采用多特征融合的VAD方法：**
 *
 * 1. **短时能量（RMS）** - 主要指标
 *    - 语音段能量高
 *    - 静音段能量低
 *
 * 2. **过零率（ZCR）** - 辅助判断
 *    - 清音和浊音的区分
 *    - 过滤高频噪声
 *
 * 3. **自适应阈值** - 智能调节
 *    - 根据音频整体响度自动调整
 *    - 适应不同录音环境
 *
 * ## 处理流程
 *
 * ```
 * PCM数据 → 特征提取 → 语音/静音标记 → 片段提取 → 细化处理 → 结果
 *   ↓           ↓             ↓              ↓           ↓
 * 原始数据    能量+过零率    二值化         初步分句    后处理
 *                                                    (合并/分割/扩展)
 * ```
 *
 * ## ⚙️ 关键参数说明
 *
 * | 参数 | 默认值 | 作用 |
 * |------|--------|------|
 * | `energyThreshold` | 0.02 | 能量阈值，**最重要** |
 * | `minSilenceDurationMs` | 300ms | 静音多长算句子间隔 |
 * | `minSpeechDurationMs` | 500ms | 语音多长算一句话 |
 * | `maxSpeechDurationMs` | 15s | 超过此时长强制分句 |
 * | `paddingMs` | 100ms | 前后扩展避免截断 |
 *
 * ## 🔧 快速调优
 *
 * **问题1：分得太碎（一句话被切成多段）**
 * ```kotlin
 * config.copy(
 *     energyThreshold = 0.015f,      // 降低阈值
 *     minSilenceDurationMs = 500     // 增加静音判定时长
 * )
 * ```
 *
 * **问题2：多句合并（几句话粘在一起）**
 * ```kotlin
 * config.copy(
 *     energyThreshold = 0.025f,      // 提高阈值
 *     minSilenceDurationMs = 200     // 减少静音判定时长
 * )
 * ```
 *
 * **问题3：句首句尾被切掉**
 * ```kotlin
 * config.copy(
 *     paddingMs = 200                // 增加扩展边距
 * )
 * ```
 */
class LocalVoiceSentenceDetector(
  private val sampleRate: Int = PcmConfig.PCM_SAMPLE_RATE  // 采样率
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
    var defaultThreshold: Float = 0.025f,

    /** 过零率阈值(0.0-1.0)，用于辅助判断 */
    var zcrThreshold: Float = 0.5f,

    /** 最小静音持续时间(毫秒)，低于此值不算句子间隔 */
    val minSilenceDurationMs: Int = 500,

    /** 最小语音持续时间(毫秒)，低于此值不算一句话 */
    val minSpeechDurationMs: Int = 100,

    /** 句子前后扩展时间(毫秒)，避免截断 */
    var paddingMs: Int = 150
  )

  /**
   * 能量和过零率数据
   */
  private data class AudioFeature(
    val sampleIndex: Int,      // 对应的采样点索引
    val energy: Float,         // 短时能量(0.0-1.0)
    val zcr: Float,            // 过零率(0.0-1.0)
    val isSpeech: Boolean      // 是否为语音
  )

  /**
   * 主函数：分离语音片段
   * @param pcmData 原始PCM数据
   * @param config 配置参数
   * @return 每句话的起始和结束采样点位置 List<Pair<起始索引, 结束索引>>
   */
  fun detectSentences(
    pcmData: ShortArray,
    config: SentenceDetectorConfig = SentenceDetectorConfig()
  ): List<Sentence> {

    if (pcmData.isEmpty()) return emptyList()

    // 提取音频特征
    val features = extractFeatures(pcmData, config)

    // 启用自适应阈值，计算阈值
    val actualThreshold =  calculateThreshold(features, config.defaultThreshold)
    Log.i("wangzixu", "detectSentences actualThreshold:$actualThreshold")

    // 3. 标记语音/静音片段
    val speechMarks = markSpeechRegions(features, actualThreshold, config)

    // 4. 合并和过滤语音片段
    val segments = extractSegments(speechMarks, config)

    // 5. 添加padding并确保在有效范围内
    return segments.map { (start, end) ->
      val paddingSamples = (config.paddingMs * sampleRate / 1000)
      val realStart = (start - paddingSamples).coerceAtLeast(0)
      val realEnd = (end + paddingSamples).coerceAtMost(pcmData.size - 1)
      Sentence(realStart.toFloat()/sampleRate, realEnd.toFloat()/sampleRate)
    }
  }

  /**
   * 提取音频特征（能量和过零率）
   */
  private fun extractFeatures(
    pcmData: ShortArray,
    config: SentenceDetectorConfig
  ): List<AudioFeature> {
    val windowSizeSamples = (config.windowSizeMs * sampleRate / 1000)
    val hopSize = (windowSizeSamples * (1 - config.overlapRatio)).toInt()

    val features = mutableListOf<AudioFeature>()
    var position = 0

    while (position + windowSizeSamples <= pcmData.size) {
      val window = pcmData.sliceArray(position until position + windowSizeSamples)

      val energy = calculateEnergy(window)
      val zcr = calculateZCR(window)

      features.add(AudioFeature(
        sampleIndex = position + windowSizeSamples / 2,
        energy = energy,
        zcr = zcr,
        isSpeech = false  // 暂时设为false，后续更新
      ))

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
        (samples[i] < 0 && samples[i - 1] >= 0)) {
        zeroCrossings++
      }
    }

    return zeroCrossings.toFloat() / samples.size
  }

  /**
   * 计算自适应阈值,
   */
  private fun calculateThreshold(
    energies: List<AudioFeature>,
    baseThreshold: Float
  ): Float {
    if (energies.isEmpty()) {
      return baseThreshold
    }
    val average = energies.filter {
      it.energy > baseThreshold
    }.map { it.energy }.average()
    return average.toFloat() / 2.0f
  }
  /**
   * 标记语音区域
   */
  private fun markSpeechRegions(
    features: List<AudioFeature>,
    threshold: Float,
    config: SentenceDetectorConfig
  ): List<AudioFeature> {

    // 初步标记
    val marked = features.map { feature ->
      val isSpeech = (feature.energy > threshold && feature.zcr < config.zcrThreshold) || (feature.energy < threshold && feature.zcr > config.zcrThreshold)
//      val isSpeech = thresholdCalc.isActive(feature.energy) && feature.zcr < config.zcrThreshold
      feature.copy(isSpeech = isSpeech)
    }

    // 中值滤波平滑（去除孤立的噪点）
    return medianFilter(marked)
  }

  /**
   * 中值滤波平滑
   */
  private fun medianFilter(
    features: List<AudioFeature>,
    windowSize: Int = 8
  ): List<AudioFeature> {
    val halfWindow = windowSize / 2
    return features.mapIndexed { index, feature ->
      val start = (index - halfWindow).coerceAtLeast(0)
      val end = (index + halfWindow).coerceAtMost(features.size - 1)
      val window = features.subList(start, end + 1)
      val speechCount = window.count { it.isSpeech }
      val isSpeech = speechCount > window.size / 2
      feature.copy(isSpeech = isSpeech)
    }
  }

  /**
   * 提取语音片段
   */
  private fun extractSegments(
    features: List<AudioFeature>,
    config: SentenceDetectorConfig
  ): List<Pair<Int, Int>> {

    val segments = mutableListOf<Pair<Int, Int>>()

    var speechStart: Int? = null
    var lastSpeechIndex: Int? = null
    features.forEachIndexed { index, feature ->
      if (feature.isSpeech) {
        // 检测到语音
        if (speechStart == null) {
          speechStart = feature.sampleIndex
        }
        lastSpeechIndex = feature.sampleIndex
      } else {
        // 检测到静音
        if (speechStart != null && lastSpeechIndex != null) {
          // 检查静音持续时间
          val silenceDuration = if (index < features.size) {
            (feature.sampleIndex - lastSpeechIndex) * 1000 / sampleRate
          } else {
            Int.MAX_VALUE
          }

          if (silenceDuration >= config.minSilenceDurationMs) {
            // 静音足够长，结束当前语音片段
            val duration = (lastSpeechIndex - speechStart) * 1000 / sampleRate

            if (duration >= config.minSpeechDurationMs) {
              segments.add(Pair(speechStart, lastSpeechIndex))
            }

            speechStart = null
            lastSpeechIndex = null
          }
        }
      }
    }

    // 处理最后一个片段
    if (speechStart != null && lastSpeechIndex != null) {
      val duration = (lastSpeechIndex - speechStart) * 1000 / sampleRate
      if (duration >= config.minSpeechDurationMs) {
        segments.add(Pair(speechStart, lastSpeechIndex))
      }
    }
    return segments
  }
}