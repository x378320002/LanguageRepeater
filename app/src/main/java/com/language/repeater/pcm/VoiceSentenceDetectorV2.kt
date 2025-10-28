package com.language.repeater.pcm

import android.content.Context
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.silero.config.FrameSize as SileroFrameSize
import com.konovalov.vad.silero.config.Mode as SileroMode
import com.konovalov.vad.silero.config.SampleRate as SileroSampleRate
import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import java.io.File
import java.io.RandomAccessFile

/**
 * PCM语音断句检测器（流式读取，支持大文件）
 *
 * 依赖:
 * implementation("com.github.gkonovalov.android-vad:webrtc:2.0.10")
 * implementation("com.github.gkonovalov.android-vad:silero:2.0.10")
 *
 * @param context Android Context
 */
class VoiceSentenceDetectorV2(private val context: Context) {

  /**
   * VAD引擎类型
   */
  enum class VadEngine {
    WEBRTC,  // 轻量级，速度快，实时性好
    SILERO   // 深度学习，准确率高
  }

  /**
   * 配置参数（用于构造VAD实例）
   */
  data class Config(
    /** VAD引擎 */
    val engine: VadEngine = VadEngine.WEBRTC,

    /** 采样率 */
    val sampleRate: Int = PcmConfig.PCM_SAMPLE_RATE,

    /** 静音持续时间阈值(毫秒) */
    val silenceDurationMs: Int = 300,

    /** 语音持续时间阈值(毫秒) */
    val speechDurationMs: Int = 100,

    // WebRTC 专用参数
    /** WebRTC模式 */
    val webrtcMode: Mode = Mode.VERY_AGGRESSIVE,

    /** WebRTC帧大小 */
    val webrtcFrameSize: FrameSize = FrameSize.FRAME_SIZE_480,  // 30ms for 16kHz

    // Silero 专用参数
    /** Silero模式 */
    val sileroMode: SileroMode = SileroMode.AGGRESSIVE,

    /** Silero帧大小 */
    val sileroFrameSize: SileroFrameSize = SileroFrameSize.FRAME_SIZE_512,

    //后处理字段
    /** 前后扩展时间(毫秒) - 用于后处理 */
    val paddingMsPre: Float = -0.1f,
    val paddingMsAfter: Float = -0.05f,
    /** 最小句子间隔 */
    val minSentenceGap: Float = 0.3f,
    /** 最小句子长度 */
    val minSentenceTime: Float = 0.3f,
  )

  /**
   * 主函数：从PCM文件检测语音片段（流式读取）
   *
   * @param pcmFile PCM文件路径
   * @param config 配置参数
   * @return 每句话的起始和结束时间(秒) List<Pair<起始时间, 结束时间>>
   */
  fun detectSentences(
    pcmFile: File,
    config: Config = Config(),
  ): List<Sentence> {

    return when (config.engine) {
      VadEngine.WEBRTC -> detectWithWebRTC(pcmFile, config)
      VadEngine.SILERO -> detectWithSilero(pcmFile, config)
    }
  }

  // ========== WebRTC VAD 实现（流式读取）==========

  private fun detectWithWebRTC(
    pcmFile: File,
    config: Config,
  ): List<Sentence> {

    // 创建VAD实例（参数必须在构造函数中传入）
    VadWebRTC(
      sampleRate = convertWebRTCSampleRate(config.sampleRate),
      frameSize = config.webrtcFrameSize,
      mode = config.webrtcMode,
      silenceDurationMs = config.silenceDurationMs,
      speechDurationMs = config.speechDurationMs
    ).use { vad ->
      // 后处理：过滤、合并、添加padding
      val size = vad.frameSize.value
      return splitSentence(pcmFile, size, config) {
        vad.isSpeech(it)
      }
    }
  }

  private fun convertWebRTCSampleRate(rate: Int): SampleRate {
    return when (rate) {
      8000 -> SampleRate.SAMPLE_RATE_8K
      16000 -> SampleRate.SAMPLE_RATE_16K
      32000 -> SampleRate.SAMPLE_RATE_32K
      48000 -> SampleRate.SAMPLE_RATE_48K
      else -> throw IllegalArgumentException("WebRTC不支持采样率: $rate")
    }
  }

  // ========== Silero VAD 实现（流式读取）==========

  private fun detectWithSilero(
    pcmFile: File,
    config: Config,
  ): List<Sentence> {

    // 创建VAD实例（参数必须在构造函数中传入）
    VadSilero(
      context = context,
      sampleRate = convertSileroSampleRate(config.sampleRate),
      frameSize = config.sileroFrameSize,
      mode = config.sileroMode,
      silenceDurationMs = config.silenceDurationMs,
      speechDurationMs = config.speechDurationMs
    ).use { vad ->
      // 后处理：过滤、合并、添加padding
      val size = vad.frameSize.value
      return splitSentence(pcmFile, size, config) {
        vad.isSpeech(it)
      }
    }
  }

  private fun convertSileroSampleRate(rate: Int): SileroSampleRate {
    return when (rate) {
      8000 -> SileroSampleRate.SAMPLE_RATE_8K
      16000 -> SileroSampleRate.SAMPLE_RATE_16K
      else -> throw IllegalArgumentException("Silero不支持采样率: $rate")
    }
  }

  // ========== 后处理：过滤、合并、添加padding ==========

  private fun splitSentence(
    pcmFile: File,
    frameSize: Int,
    config: Config,
    doCheck: (buffer: ByteArray) -> Boolean,
  ): List<Sentence> {
    // 计算每帧需要的字节数（1个采样点 = 2字节）
    val bufferSize = frameSize * PcmConfig.BYTES_PER_SAMPLE
    val segments = mutableListOf<Pair<Long, Long>>()
    var speechStartSample: Long? = null
    var currentSampleIndex = 0L
    val buffer = ByteArray(bufferSize)

    RandomAccessFile(pcmFile, "r").use { input ->
      while (currentSampleIndex * 2 < input.length()) {
        input.seek(currentSampleIndex * 2)
        val bytesRead = input.read(buffer)
        if (bytesRead < bufferSize) break

        // VAD检测
        val isSpeech = doCheck(buffer)

        if (isSpeech) {
          // 检测到语音，记录起始位置
          if (speechStartSample == null) {
            speechStartSample = currentSampleIndex
          }
        } else {
          // 检测到静音，如果之前有语音，保存片段
          if (speechStartSample != null) {
            segments.add(Pair(speechStartSample, currentSampleIndex))
            speechStartSample = null
          }
        }
        currentSampleIndex += (frameSize / 2)
      }

      // 处理最后一个片段
      if (speechStartSample != null) {
        segments.add(Pair(speechStartSample, currentSampleIndex))
      }
    }

    // 后处理：过滤、合并、添加padding
    return postProcessSegments(segments, config)
  }

  private fun postProcessSegments(
    segments: List<Pair<Long, Long>>,
    config: Config,
  ): List<Sentence> {

    val ranges = segments.map { (start, end) ->
      Sentence(
        start.toFloat() / config.sampleRate,
        end.toFloat() / config.sampleRate
      )
    }

    if (ranges.isEmpty()) {
      return ranges
    }

    val merged = mutableListOf<Sentence>()
    var currentStart = ranges[0].start
    var currentEnd = ranges[0].end

    for (i in 1 until ranges.size) {
      val (nextStart, nextEnd) = ranges[i]

      // 判断是否重叠或间隔小于 0.5
      if (nextStart <= currentEnd + config.minSentenceGap) {
        // 合并区间，取较大的 end
        currentEnd = maxOf(currentEnd, nextEnd)
      } else {
        // 不重叠，保存上一个区间
        merged.add(Sentence(currentStart, currentEnd))
        currentStart = nextStart
        currentEnd = nextEnd
      }
    }

    // 别忘了最后一个
    merged.add(Sentence(currentStart, currentEnd))

    // 1. 过滤过短的片段
    val filteredSegments = merged.filter { (start, end) ->
      (end - start) >= config.minSentenceTime
    }

    // 2. 添加padding并转换为时间（秒）
    return filteredSegments.map { (start, end) ->
      val paddedStart = (start + config.paddingMsPre).coerceAtLeast(0f)
      val paddedEnd = end + config.paddingMsAfter
      Sentence(paddedStart, paddedEnd)
    }
  }
}


// ========== 参数说明 ==========

/*
参数详解:

1. silenceDurationMs (VAD内部参数)
   - 作用: VAD检测时，连续多长时间的静音才算是真正的静音段
   - 值越大: 对静音要求越严格，不容易中断语音
   - 值越小: 更敏感，容易把短暂停顿当作静音
   - 建议: 200-500ms

2. speechDurationMs (VAD内部参数)
   - 作用: VAD检测时，连续多长时间的语音才算是真正的语音段
   - 值越大: 对语音要求越严格，过滤短促音
   - 值越小: 更敏感，容易把噪音当作语音
   - 建议: 50-100ms

3. minSentenceDurationMs (后处理参数)
   - 作用: 过滤掉太短的片段，只保留真正的句子
   - 建议: 500-1000ms

4. paddingMs (后处理参数)
   - 作用: 在检测到的句子前后各扩展一段时间，避免截断
   - 建议: 100-200ms

典型配置:

// 清晰朗读
Config(
    silenceDurationMs = 400,      // 需要400ms静音才算间隔
    speechDurationMs = 50,         // 50ms语音就算有效
    minSentenceDurationMs = 600,   // 至少600ms才是一句话
    paddingMs = 150                // 前后各扩展150ms
)

// 快速对话
Config(
    silenceDurationMs = 200,       // 200ms静音就算间隔
    speechDurationMs = 50,
    minSentenceDurationMs = 300,   // 允许较短的句子
    paddingMs = 100
)

// 嘈杂环境
Config(
    silenceDurationMs = 500,       // 需要更长静音
    speechDurationMs = 100,        // 需要更长语音才算有效
    minSentenceDurationMs = 700,   // 过滤短句
    paddingMs = 100
)

WebRTC Mode选择:
- QUALITY: 高质量（保守，不容易误判）
- LOW_BITRATE: 平衡
- AGGRESSIVE: 积极检测静音
- VERY_AGGRESSIVE: 非常积极（推荐，能更好地检测静音）

Silero Mode选择:
- NORMAL: 标准模式（推荐）
- AGGRESSIVE: 积极模式（更敏感）
*/