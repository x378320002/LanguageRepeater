package com.language.repeater.record

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.fragment.app.Fragment
import com.language.repeater.record.IAudioRecorderManger.Companion.FAIL_TYPE_CANCEL
import com.language.repeater.record.IAudioRecorderManger.Companion.FAIL_TYPE_ERROR
import com.language.repeater.record.IAudioRecorderManger.Companion.FAIL_TYPE_NO_INPUT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * 录音和实时编码管理器 (PCM + 实时 AAC)
 *
 * @param context 用于获取应用缓存目录
 * @param listener 回调监听器
 *
 * @RequiresPermission android.Manifest.permission.RECORD_AUDIO
 */
// MediaCodec 异步 API 和 Muxer 需要 21+
class AudioRecordManagerV2(private val fragment: Fragment) : IAudioRecorderManger {

  companion object {
    private const val TAG = "SearchVoiceV3ViewModel"
    private const val SILENCE_TIMEOUT = 5000L //开始等待说话后, 多久不说话就自动报失败

    // --- 音频配置 (必须在 PCM 和 AAC 间保持一致) ---
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT_IN = AudioFormat.ENCODING_PCM_16BIT
    private const val CHANNEL_COUNT_OUT = 1 // CHANNEL_IN_MONO 对应 1

    private const val AAC_MIME_TYPE = "audio/mp4a-latm"

    private const val AAC_BIT_RATE = 64000 // 64 kbps

    // --- 功能配置 ---
    private const val SPEECH_DETECT_THRESHOLD = 5 //检测到开始说话的音量阈值
    private const val PROGRESS_INTERVAL_MS = 50L //多久回调一次音量
    private const val MIN_RECORDING_MS = 1000L //最短录音时长
    private const val MAX_RECORDING_MS = 60000L //最长录音时长

    // MediaCodec 轮询超时
    private const val CODEC_TIMEOUT_US = 10000L // 10ms
  }

  private var audioRecord: AudioRecord? = null
  private var pcmBufferSize: Int = 0
  // 协程和状态
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  private var recordingJob: Job? = null
  private var silenceTimeOutJob: Job? = null //开始等待说话后, 多久不说话就自动报失败的超时任务
  private var autoDoneTimeOutJob: Job? = null //检测说话完的检测任务, 隔x秒不说话了就任务说话完了
  //自动说话完的检测时长
  private val autoDoneTime = 2000L
  private var maxTimeJob: Job? = null
  private val isRecording = AtomicBoolean(false)

  // 文件和时间
  private var pcmFile: File? = null //预留的后面后期处理的原始文件,暂时不用
  private var aacFile: File? = null
  private var startTime: Long = 0L

  // 实时编码器组件
  private var mediaCodec: MediaCodec? = null
  private var mediaMuxer: MediaMuxer? = null
  private var pcmFileOutputStream: FileOutputStream? = null
  private var muxerTrackIndex: Int = -1
  private var pcmPresentationTimeUs: Long = 0L

  //降噪
  private var ns: NoiseSuppressor? = null

  private var isMuxerStarted = false

  var listener: AudioRecordingListener? = null

  fun startRecording() {
    val context = fragment.context ?: return
//    if (!AudioRecorder.hasPermission()) {
//      KLogSearch.i(TAG, "cannot record directly, check permissions and dfm first")
//      AudioRecorder.requestPermission(fragment.activity).subscribe()
//      listener?.onRecordFailure(FAIL_TYPE_ERROR, null, "没有权限")
//      return
//    }

    if (!isRecording.compareAndSet(false, true)) {
      return
    }

    recordingJob = scope.launch {
      var recordingSuccess = false
      try {
        // 内部创建文件
        val timestamp = System.currentTimeMillis()
//        val pcmFile = File(context.cacheDir, "rec_${timestamp}.pcm")
//        this@AudioRecordManagerV2.pcmFile = pcmFile
        val aacFile = File(context.cacheDir, "rec_${timestamp}.m4a")
        this@AudioRecordManagerV2.aacFile = aacFile

        if (pcmFile != null) {
          pcmFileOutputStream = FileOutputStream(pcmFile)
        }

        // 初始化所有组件
        setupAudioRecord()

        // 输入AAC,mp4文件准备
        setupMediaCodecAndMuxer(aacFile)

        // 开始录音
        audioRecord?.startRecording()

        //开始mediaCodec, 准备接受pcm并编码
        mediaCodec?.start()

        startTime = System.currentTimeMillis()
        withContext(Dispatchers.Main) {
          listener?.onRecordStart()
        }

        //启动实时录音和编码循环, 内部会循环处理录音数据
        runRecordingLoop()

        //只有正常结束录音才会调用到这里, 取消或异常会直接跳过
        recordingSuccess = true
      } catch (e: Exception) {
        Log.d(TAG, "Record exception:${e.message}")
        withContext(Dispatchers.Main) {
          //如果是被取消的任务, 此处不被调用
          listener?.onRecordFailure(FAIL_TYPE_ERROR, e, e.message)
        }
      } finally {
        // 5. 停止和释放所有资源
        val finalElapsed = System.currentTimeMillis() - startTime
        cleanupResources()
        isRecording.set(false)

        // 最终回调和时长检查
        if (recordingSuccess) {
          if (finalElapsed < MIN_RECORDING_MS) {
            pcmFile?.delete()
            aacFile?.delete()
            withContext(Dispatchers.Main) {
              //如果是被取消的任务, 此处不被调用
              listener?.onRecordFailure(FAIL_TYPE_CANCEL, null, "录音时长太短")
            }
          } else {
            withContext(Dispatchers.Main) {
              //如果是被取消的任务, 此处不被调用
              listener?.onRecordComplete(aacFile!!.absolutePath, System.currentTimeMillis() - startTime)
            }
          }
        } else {
          // 录制本身就失败了, 删除文件
          pcmFile?.delete()
          aacFile?.delete()
        }
      }
    }

    recordingJob?.invokeOnCompletion {
      listener?.onStopAudioRecording()
      silenceTimeOutJob?.cancel()
      maxTimeJob?.cancel()
      autoDoneTimeOutJob?.cancel()
    }

    //5秒超时自动失败的任务
    silenceTimeOutJob?.cancel()
    silenceTimeOutJob = scope.launch {
      delay(SILENCE_TIMEOUT)
      if (isRecording.get()) {
        Log.d(TAG, "Record 静音5秒超时")
        recordingJob?.cancel("静音5秒超时")
        withContext(Dispatchers.Main) {
          //如果是被取消的任务, 此处不被调用
          listener?.onRecordFailure(FAIL_TYPE_NO_INPUT, null, "长时间没检测到输入")
        }
      }
    }
  }

  /**
   * 停止录音
   * 仅设置标志位, 循环将在下一轮迭代中自行处理停止和编码器刷新。
   */
  fun stopRecording() {
    if (!isRecording.get()) {
      return
    }
    isRecording.set(false)
  }

  fun isRecording() = isRecording.get()

  @SuppressLint("MissingPermission")
  @Throws(IOException::class, IllegalStateException::class)
  private fun setupAudioRecord() {
    pcmBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT_IN)
    if (pcmBufferSize == AudioRecord.ERROR_BAD_VALUE) {
      throw IllegalStateException("AudioRecord getMinBufferSize failed.")
    }

    /*
      1, 如果在做 VoIP (实时通话), 用VOICE_RECOGNITION模式, 并启动AudioManager.MODE_IN_COMMUNICATION,
         会自动激活 AEC/NS 所需的整个音频处理链路
      2, 正常语音识别应该使用VOICE_RECOGNITION模式, 会自动哦关闭所有系统效果 (AEC/NS)，以便 ASR 引擎获取原始信号
      3, 本次需求是为了获取原始的清晰带降噪的录音音频, 需要开启NS而不开启AEC, AEC 的工作原理是“消除您正在播放的声音”,
         如果您没有在同一个 Audio Session 中播放音频 (通过 AudioTrack)，AEC 根本无法工作，甚至可能使声音恶化
     */
    audioRecord = AudioRecord(
      MediaRecorder.AudioSource.MIC,
      SAMPLE_RATE,
      CHANNEL_CONFIG_IN,
      AUDIO_FORMAT_IN,
      pcmBufferSize
    )

    if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
      throw IllegalStateException("AudioRecord initialization failed.")
    }

    enableNoiseSuppressionIfAvailable()
    //如果在做一个全双工（同时播放和录制）的应用，再启动AcousticEchoCanceler回声消除
    //enableAcousticEchoCancelerIfAvailable()
  }

  private fun enableNoiseSuppressionIfAvailable() {
    if (NoiseSuppressor.isAvailable()) {
      try {
        audioRecord?.audioSessionId?.let { sessionId ->
          ns = NoiseSuppressor.create(sessionId)
          ns?.enabled = true
          Log.d(TAG, "Noise suppression enabled")
        }
      } catch (e: Exception) {
        e.printStackTrace()
        //启动降噪失败的话忽略即可, 不影响正常录音
        Log.i(TAG, "Noise suppression failed:${e.message}")
      }
    }
  }

//  private fun enableAcousticEchoCancelerIfAvailable() {
//    if (AcousticEchoCanceler.isAvailable()) {
//      audioRecord?.audioSessionId?.let { sessionId ->
//        val echoCanceler = AcousticEchoCanceler.create(sessionId)
//        echoCanceler?.enabled = true
//        Log.d(TAG", "Echo cancellation enabled")
//      }
//    }
//  }

  @Throws(IOException::class, IllegalStateException::class)
  private fun setupMediaCodecAndMuxer(aacFile: File) {
    // 1. Muxer
    mediaMuxer = MediaMuxer(aacFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    isMuxerStarted = false

    // 2. Codec Format
    val audioFormat =
      MediaFormat.createAudioFormat(
        AAC_MIME_TYPE,
        SAMPLE_RATE,
        CHANNEL_COUNT_OUT
      ).apply {
        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        setInteger(MediaFormat.KEY_BIT_RATE, AAC_BIT_RATE)
        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, pcmBufferSize)
      }

    // 3. Codec
    mediaCodec = MediaCodec.createEncoderByType(AAC_MIME_TYPE)
    mediaCodec?.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

    // 重置状态
    muxerTrackIndex = -1
    pcmPresentationTimeUs = 0L
  }

  private fun cleanupResources() {
    Log.d(TAG, "Cleaning up resources...")
    try {
      audioRecord?.stop()
      audioRecord?.release()
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping AudioRecord", e)
    }
    audioRecord = null

    try {
      pcmFileOutputStream?.close()
    } catch (e: IOException) {
      Log.e(TAG, "Error closing PCM stream", e)
    }
    pcmFileOutputStream = null

    try {
      mediaCodec?.stop()
      mediaCodec?.release()
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping MediaCodec", e)
    }
    mediaCodec = null

    try {
      if (isMuxerStarted) {
        mediaMuxer?.stop()
      }
      mediaMuxer?.release()
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping MediaMuxer", e)
    }
    mediaMuxer = null
    isMuxerStarted = false

    // 5. 释放 effects
    try {
      ns?.setEnabled(false)
      ns?.release()
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping NoiseSuppression", e)
    }
    ns = null
  }

  /**
   * --- 核心循环 ---
   * 先从编码器取输出缓存区, 再从编码器取输入缓存区, 取到输入缓存区后再去录音器中取录音数据
   * 录音PCM - 编码AAC - 合成MP4
   */
  private suspend fun runRecordingLoop() {
    val mediaCodec = mediaCodec ?: return
    val pcmBuffer = ByteArray(pcmBufferSize)
    val codecBufferInfo = MediaCodec.BufferInfo()
    var isInputEos = false // 标记是否已发送 EOS (End of Stream)
    var lastProgressTime = startTime
    var hasDetectedSpeech = false

    while (true) {
      // 步骤 1: 优先从编码器取出（并写入 Muxer）
      // 这会释放输出缓冲区, 并在 EOS 时退出循环
      val drainResult = drainCodecOutput(mediaCodec, codecBufferInfo)
      if (drainResult == DrainResult.EOS_REACHED) {
        Log.d(TAG, "End of stream reached. Exiting loop.")
        break
      }

      //循环中检查协程是否还是活的
      currentCoroutineContext().ensureActive()

      //如果输入流尚未结束, 尝试向编码器输入
      if (!isInputEos) {
        // 检查是否被要求停止
        if (!isRecording.get()) {
          Log.d(TAG, "Stop requested. Sending EOS to codec.")
          // 向编码器发送 EOS 信号
          val inputIndex = mediaCodec.dequeueInputBuffer(CODEC_TIMEOUT_US)
          if (inputIndex >= 0) {
            mediaCodec.queueInputBuffer(
              inputIndex,
              0,
              0,
              pcmPresentationTimeUs,
              MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
            isInputEos = true
          }
          // (如果 inputIndex < 0, 下一轮循环会继续尝试发送 EOS)
        } else {
          // 录音仍在进行
          val inputIndex = mediaCodec.dequeueInputBuffer(CODEC_TIMEOUT_US)
          if (inputIndex >= 0) {
            // 拿到了一个可用的输入缓冲区
            // *现在*才去 AudioRecord 读取数据
            val readSize = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: 0

            if (readSize > 0) {
              // --- 1. 正常读取到数据 ---
              val inputData = mediaCodec.getInputBuffer(inputIndex)
              inputData?.clear()
              inputData?.put(pcmBuffer, 0, readSize)

              // 计算 PTS
              val pts =
                (readSize.toDouble() / (SAMPLE_RATE * CHANNEL_COUNT_OUT * 2) * 1_000_000).toLong()
              pcmPresentationTimeUs += pts

              // 喂给 Codec
              mediaCodec.queueInputBuffer(inputIndex, 0, readSize, pcmPresentationTimeUs, 0)

              // 写入 PCM 文件
              pcmFileOutputStream?.write(pcmBuffer, 0, readSize)

              // --- (进度回调) ---
              val currentTime = System.currentTimeMillis()
              if (currentTime - lastProgressTime >= PROGRESS_INTERVAL_MS) {
                lastProgressTime = currentTime
                val elapsed = currentTime - startTime
                val volume = calculateVolume(pcmBuffer, readSize)

                if (volume > SPEECH_DETECT_THRESHOLD) {
                  if (!hasDetectedSpeech) {
                    hasDetectedSpeech = true

                    //检测到声音了, 取消静音超时回调
                    silenceTimeOutJob?.cancel()
                    silenceTimeOutJob = null

                    //检测到声音了, 添加总时长超时回调
                    maxTimeJob?.cancel()
                    maxTimeJob = scope.launch {
                      delay(MAX_RECORDING_MS)
                      if (isRecording.get()) {
                        Log.d(TAG, "Record 录音时长已经60秒超时")
                        stopRecording()
                      }
                    }

                    withContext(Dispatchers.Main) {
                      //回调开始说话
                      listener?.onRecordDetect()
                    }
                  }

                  //设置x秒不说话自动说完了的任务
                  autoDoneTimeOutJob?.cancel()
                  autoDoneTimeOutJob = scope.launch {
                    delay(autoDoneTime)
                    if (isRecording.get()) {
                      Log.d(TAG, "Recording $autoDoneTime ms没说话了, 结束!")
                      stopRecording()
                    }
                  }
                }
                withContext(Dispatchers.Main) { listener?.onRecordProgress(elapsed, volume) }
              }
              // --- (回调结束) ---
            } else if (readSize == 0) {
              // --- 2. 没读到数据 (罕见情况, 兜底处理) ---
              // 必须把 buffer 还回去, 否则 Codec 会饿死
              mediaCodec.queueInputBuffer(inputIndex, 0, 0, pcmPresentationTimeUs, 0)
            } else {
              // --- 3. readSize < 0, 发生错误 ---
              // 必须把 buffer 还回去
              mediaCodec.queueInputBuffer(inputIndex, 0, 0, pcmPresentationTimeUs, 0)
              Log.e(TAG, "AudioRecord read error: $readSize")
              throw IOException("AudioRecord read error: $readSize")
            }
          }
          // else (inputIndex < 0) {
          //    // 编码器输入缓冲区已满, 没关系,
          //    // 本轮循环的 drainCodecOutput() 会释放,
          //    // 下一轮循环再试
          // }
        }
      }
    }
  }

  private enum class DrainResult { CONTINUE, EOS_REACHED }

  /**
   * 从编码器输出缓冲区取数据, 并写入 Muxer
   */
  private fun drainCodecOutput(
    mediaCodec: MediaCodec,
    bufferInfo: MediaCodec.BufferInfo,
  ): DrainResult {
    val mediaMuxer = mediaMuxer ?: return DrainResult.EOS_REACHED
    val outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)

    return when (outputIndex) {
      MediaCodec.INFO_TRY_AGAIN_LATER -> DrainResult.CONTINUE
      MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
        // Muxer 必须在获取到 Format Changed 后启动
        val newFormat = mediaCodec.outputFormat
        muxerTrackIndex = mediaMuxer.addTrack(newFormat)
        mediaMuxer.start()
        isMuxerStarted = true //标记 Muxer 已启动
        Log.d(TAG, "Muxer started with new format.")
        DrainResult.CONTINUE
      }

      else -> {
        if (outputIndex < 0) return DrainResult.CONTINUE // 忽略无效索引

        val outputData = mediaCodec.getOutputBuffer(outputIndex)
        if (outputData == null) {
          mediaCodec.releaseOutputBuffer(outputIndex, false)
          return DrainResult.CONTINUE
        }

        // 确保 Muxer 已经启动
        if (isMuxerStarted
          && bufferInfo.size > 0
          && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0)
        ) {
          // 写入 .m4a 文件
          outputData.position(bufferInfo.offset)
          outputData.limit(bufferInfo.offset + bufferInfo.size)
          mediaMuxer.writeSampleData(muxerTrackIndex, outputData, bufferInfo)
        }

        mediaCodec.releaseOutputBuffer(outputIndex, false)

        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
          return DrainResult.EOS_REACHED // 成功收到结束信号
        }

        DrainResult.CONTINUE
      }
    }
  }

  /**
   * 计算 PCM 数据的 RMS (均方根) 值以估算音量,
   * 这里对齐原始的录音sdk实现, 计算平均值而不是rms, 用平均值*400/32768作为音量值
   */
  private fun calculateVolume(data: ByteArray, readSize: Int): Int {
    if (readSize == 0) return 0
    val shortBuffer = ShortArray(readSize / 2)
    ByteBuffer.wrap(data, 0, readSize)
      .order(ByteOrder.LITTLE_ENDIAN)
      .asShortBuffer()
      .get(shortBuffer)
//    var sumSquares = 0.0
//    for (sample in shortBuffer) {
//      sumSquares += sample.toDouble() * sample.toDouble()
//    }
//    return sqrt(sumSquares / shortBuffer.size)
    var sum = 0
    for (sample in shortBuffer) {
      sum += abs(sample.toInt())
    }
    val avg = sum / shortBuffer.size
    val volume = (avg / 32768.0 * 400).toInt().coerceIn(0, 100)
    return volume
  }

  //对齐上版本的AudioRecordManger方法
  override fun start() {
    startRecording()
  }

  override fun destroy() {
    scope.cancel()
  }

  override fun stop() {
    stopRecording()
  }

  override fun setRecordListener(l: AudioRecordingListener?) {
    listener = l
  }
}