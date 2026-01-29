package com.language.repeater.sentence

import android.util.Log
import com.language.repeater.pcm.PcmConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.collections.forEachIndexed
import kotlin.collections.lastIndex
import kotlin.collections.map
import kotlin.math.sqrt

class LocalVoiceSentenceDetector(
  private val sampleRate: Int = PcmConfig.PCM_SAMPLE_RATE,  // 采样率
) {
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
    val minSilenceDurationMs: Int = 500,

    /** 最小语音持续时间(毫秒)，低于此值不算一句话 */
    val minSpeechDurationMs: Int = 50,

    /** 句子前后的额外补偿扩展 */
    var paddingBeginMs: Int = 100,
    var paddingEndMs: Int = 200,

    /** 句子前后追加清辅音和低音量的检查计算区间,多少帧 */
    var expandEdgeStartCount: Int = 10, //20帧=300ms
    var expandEdgeEndCount: Int = 15, //20帧=300ms
    var expandEdgeCheckEnergyFactor: Float = 0.3f, //20帧=300ms
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
   * @param file 原始PCM数据
   * @param config 配置参数
   * @return 每句话的起始和结束采样点位置 List<Pair<起始索引, 结束索引>>
   */
  suspend fun detectSentences(
    file: File,
    con: SentenceDetectorConfig? = null,
  ): List<Sentence> {

    if (con != null) {
      config = con
    }

    // 提取音频特征
    val features = extractFeatures(file)

    // 启用自适应阈值，计算阈值
    calculateThreshold(features)

    // 标记语音/静音片段
    markSpeechRegions(features)

    // 中值滤波平滑（去除孤立的噪点）, 复读机的环境没必要开启
    //medianFilter(features)

    val totalSamples = ((file.length() - PcmConfig.WAV_HEAD_SIZE) / PcmConfig.BYTES_PER_SAMPLE).toInt()
    return extractSentence(features, totalSamples)

    // 分离出语音片段 //旧的写法
    //val segments = extractSegments(features)
    ////扩展语音片段的边缘
    //expandSegmentEdge(features, segments)
    ////片段转成句子
    //val sentences = convertToSentence(features, segments, pcmData.lastIndex)
    //return sentences.map {
    //  Sentence(
    //    it.sampleStart.toFloat() / sampleRate,
    //    it.sampleEnd.toFloat() / sampleRate
    //  )
    //}
  }

  /**
   * 提取音频特征（能量和过零率）
   */
  private suspend fun extractFeatures(file: File): List<AudioFrame> = withContext(Dispatchers.IO) {
    val features = mutableListOf<AudioFrame>()

    // 1. 基础参数计算
    val windowSizeSamples = (config.windowSizeMs * sampleRate / 1000)
    // hopSize 是每次向前滑动的距离，也是每次需要从文件新读取的数据量
    val hopSize = (windowSizeSamples * (1 - config.overlapRatio)).toInt()
    // overlapSize 是保留下来的旧数据长度
    val overlapSize = windowSizeSamples - hopSize

    // 2. 准备缓冲区
    // windowBuffer 用于存放当前窗口的 Short 数据
    val windowBuffer = ShortArray(windowSizeSamples)

    // readBuffer 用于从流中读取字节，大小等于 hopSize * 2 (因为 1 Short = 2 Bytes)
    // 初始读取时需要读满整个窗口，所以取最大值
    val byteBuffer = ByteArray(windowSizeSamples * 2)

    var position = 0 // 当前处理到的采样点位置
    FileInputStream(file).buffered().use { input ->
      // 跳过 WAV 头部 44 字节
      input.skip(PcmConfig.WAV_HEAD_SIZE.toLong())

      // --- 阶段 A: 第一次填满窗口 ---
      // 我们需要先读取 windowSizeSamples 长度的数据来启动第一个窗口
      val firstReadBytes = input.read(byteBuffer, 0, windowSizeSamples * 2)
      if (firstReadBytes < windowSizeSamples * 2) {
        // 文件太小，连一个窗口都凑不齐，直接返回空或做特殊处理
        return@withContext features
      }

      // 将字节转换为 Short 填入 windowBuffer
      bytesToShorts(byteBuffer, windowBuffer, 0, windowSizeSamples)

      // --- 阶段 B: 循环滑动处理 ---
      while (true) {
        // 1. 提取当前窗口特征
        // 注意：calculateEnergy 不能修改 windowBuffer 的内容，也不能长期持有它的引用
        val energy = calculateEnergy(windowBuffer)

        features.add(
          AudioFrame(
            sampleIndex = position + windowSizeSamples / 2,
            energy = energy
          )
        )

        // 更新位置
        position += hopSize

        // 2. 移动数据 (Shift)
        // 将重叠部分 (overlapSize) 从尾部搬运到头部
        // System.arraycopy(源数组, 源起始, 目标数组, 目标起始, 长度)
        if (overlapSize > 0) {
          System.arraycopy(windowBuffer, hopSize, windowBuffer, 0, overlapSize)
        }

        // 3. 读取新数据 (Refill)
        // 我们只需要读取 hopSize 长度的新数据
        val bytesToRead = hopSize * 2
        val bytesRead = input.read(byteBuffer, 0, bytesToRead)

        // 如果读不到数据了，或者数据不够凑满一个 hop，说明文件结束
        if (bytesRead < bytesToRead) {
          break
        }

        // 4. 将新读取的字节转换并填入 windowBuffer 的尾部
        // 填充起始位置是 overlapSize
        bytesToShorts(byteBuffer, windowBuffer, overlapSize, hopSize)
      }
    }

    return@withContext features
  }

  /**
   * 辅助方法：将字节数组转换为 Short 数组 (小端序)
   * @param byteArray 源字节数组
   * @param shortArray 目标 Short 数组
   * @param offsetInShorts 目标数组的起始填充索引
   * @param lengthInShorts 要转换的 Short 数量
   */
  private fun bytesToShorts(
    byteArray: ByteArray,
    shortArray: ShortArray,
    offsetInShorts: Int,
    lengthInShorts: Int
  ) {
    for (i in 0 until lengthInShorts) {
      val byteIndex = i * 2
      // 小端序转换: 低位在前，高位在后
      val low = byteArray[byteIndex].toInt() and 0xFF
      val high = byteArray[byteIndex + 1].toInt() and 0xFF
      val shortVal = ((high shl 8) or low).toShort()
      shortArray[offsetInShorts + i] = shortVal
    }
  }

  /**
   * @param features 音频特征列表
   * @param totalDataLength PCM数据的总采样数 (用于防止尾部填充越界)
   */
  private fun extractSentence(features: List<AudioFrame>, totalDataLength: Int): List<Sentence> {
    val sentences = mutableListOf<Sentence>()
    if (features.isEmpty()) return sentences

    // 预计算阈值 (将毫秒转换为采样点数)
    val minSilenceSamples = (config.minSilenceDurationMs * sampleRate / 1000).toLong()
    val minSpeechSamples = (config.minSpeechDurationMs * sampleRate / 1000).toLong()
    // 预计算 Padding 采样数
    val paddingBeginSamples = (config.paddingBeginMs * sampleRate / 1000).toInt()
    val paddingEndSamples = (config.paddingEndMs * sampleRate / 1000).toInt()

    fun addSentence(start: Int, end: Int) {
      val coreDuration = end - start
      if (coreDuration >= minSpeechSamples) {
        var finalStart = start - paddingBeginSamples
        if (finalStart < 0) finalStart = 0

        var finalEnd = end + paddingEndSamples
        if (finalEnd > totalDataLength) finalEnd = totalDataLength
        sentences.add(
          Sentence(
            start = finalStart.toFloat() / sampleRate,
            end = finalEnd.toFloat() / sampleRate
          )
        )
      }
    }

    // 状态变量
    var coreStartSample: Int? = null // 核心语音段起始
    var lastSpeechSample: Int? = null // 核心语音段结束 (最后一次检测到语音的位置)
    for (frame in features) {
      if (frame.isSpeech) {
        // 如果是新句子的开始
        if (coreStartSample == null) {
          coreStartSample = frame.sampleIndex
        }
        // 更新最后一次说话的位置
        lastSpeechSample = frame.sampleIndex
      } else {
        // 如果处于静音段，检查是否达到了断句阈值
        if (coreStartSample != null && lastSpeechSample != null) {
          val silenceGap = frame.sampleIndex - lastSpeechSample
          if (silenceGap > minSilenceSamples) {
            // --- 触发断句逻辑 ---
            addSentence(coreStartSample, lastSpeechSample)
            // 重置状态，准备捕捉下一句
            coreStartSample = null
            lastSpeechSample = null
          }
        }
      }
    }

    // --- 循环结束后的收尾处理 ---
    // 处理文件末尾可能存在的最后一句话
    if (coreStartSample != null && lastSpeechSample != null) {
      addSentence(coreStartSample, lastSpeechSample)
    }

    return sentences
  }

  //private fun extractFeatures(
  //  pcmData: ShortArray,
  //): List<AudioFrame> {
  //  val windowSizeSamples = (config.windowSizeMs * sampleRate / 1000)
  //  val hopSize = (windowSizeSamples * (1 - config.overlapRatio)).toInt()
  //
  //  val features = mutableListOf<AudioFrame>()
  //  var position = 0
  //
  //  while (position + windowSizeSamples <= pcmData.size) {
  //    val window = pcmData.sliceArray(position until position + windowSizeSamples)
  //
  //    val energy = calculateEnergy(window)
  //    //暂时不需要过零率
  //    //val zcr = calculateZCR(window)
  //    features.add(
  //      AudioFrame(
  //        sampleIndex = position + windowSizeSamples / 2,
  //        energy = energy
  //      )
  //    )
  //
  //    position += hopSize
  //  }
  //
  //  return features
  //}

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
      .subList(0, (energies.size * 0.95f).toInt())
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

    fun expandBegin(seg: Segment) {
      val begin = (seg.frameStart - config.expandEdgeStartCount).coerceAtLeast(0)
      val end = seg.frameStart - 1
      for (i in end downTo begin) {
        val feature = features[i]
        val isSpeech = isSpeech(feature)
        if (isSpeech) {
          seg.frameStart = i
        } else {
          break
        }
      }
    }

    fun expandEnd(seg: Segment) {
      val begin = seg.frameEnd + 1
      val end = (seg.frameEnd + config.expandEdgeEndCount).coerceAtMost(features.lastIndex)
      for (i in begin .. end) {
        val feature = features[i]
        val isSpeech = isSpeech(feature)
        if (isSpeech) {
          seg.frameEnd = i
        } else {
          break
        }
      }
    }

    for (i in segments.indices) {
      val seg = segments[i]
      expandBegin(seg)
      expandEnd(seg)
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

    val paddingStart = config.paddingBeginMs * sampleRate / 1000
    val paddingEnd = config.paddingEndMs * sampleRate / 1000
    sentences.forEach {
      it.sampleStart = (it.sampleStart - paddingStart).coerceAtLeast(0)
      it.sampleEnd = (it.sampleEnd + paddingEnd).coerceAtMost(maxSampleIndex)
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