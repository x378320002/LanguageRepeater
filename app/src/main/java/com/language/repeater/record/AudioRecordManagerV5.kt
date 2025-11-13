package com.language.repeater.record

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.language.repeater.record.IAudioRecorderManger.Companion.FAIL_TYPE_CANCEL
import com.language.repeater.record.IAudioRecorderManger.Companion.FAIL_TYPE_ERROR
import com.language.repeater.record.IAudioRecorderManger.Companion.FAIL_TYPE_NO_INPUT
import com.language.repeater.record.IAudioRecorderManger.Companion.FAIL_TYPE_TOO_SHORT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * AudioRecord 本地录音管理器
 * 直接调用手机录音功能录音, 通过AudioRecord而不是MediaRecorder,
 * 目的是为了暴露出pcm数据, 方便后续自己对pcm数据进行加工
 * 当前的实现是
 * AudioRecord-录制pcm
 * MediaCodeC-编码pcm成aac
 * MediaMuxer-封装aac成mp4音频文件
 * 本期把pcm的录制和编码封装分离, 方便后续切换其他pcm源
 */
class AudioRecordManagerV5(private val fragment: Fragment) : IAudioRecorderManger {

  companion object {
    private const val TAG = "SearchVoiceV3ViewModel"
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT_IN = AudioFormat.ENCODING_PCM_16BIT

    private const val SILENCE_TIMEOUT = 5000L //开始等待说话后, 多久不说话就自动报失败
    private const val SPEECH_DETECT_THRESHOLD = 42 //检测到开始说话的音量阈值
    private const val PROGRESS_INTERVAL_MS = 50L //多久回调一次音量
    private const val MIN_RECORDING_MS = 1000L //最短录音时长
    private const val MAX_RECORDING_MS = 60000L //最长录音时长
  }

  // 自定义一个异常, 用于 "时长太短" 和 静音5秒
  private class RecordingTooShortException : Exception("Record less than 1 second")
  private class SilentToLongCancellation : CancellationException("Silent for more than 5 seconds")

  private var audioRecord: AudioRecord? = null
  private var pcmBufferSize: Int = 0

  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private var recordingJob: Job? = null
  private var silenceTimeOutJob: Job? = null //开始等待说话后, 多久不说话就自动报失败的超时任务
  private var autoDoneTimeOutJob: Job? = null //检测说话完的检测任务, 隔x秒不说话了就任务说话完了
  //自动说话完的检测时长
  private val autoDoneTime = 5000L
  private var maxTimeJob: Job? = null
  private val isRecording = AtomicBoolean(false)

  //降噪
  private var ns: NoiseSuppressor? = null

  private var listener: AudioRecordingListener? = null

  fun startRecording() {
    val context = fragment.context ?: return
    if (!isRecording.compareAndSet(false, true)) {
      return
    }

    recordingJob = scope.launch { // ⬅️ 这是父协程 (Job R)
      val pcmChannel = Channel<ByteArray>(Channel.BUFFERED)
      var aacFile: File? = null
      var lastError: Throwable? = null
      var recordingSuccess = false
      val startTime = System.currentTimeMillis()

      try {
        // 准备文件
        val timestamp = System.currentTimeMillis()
        aacFile = File(context.cacheDir, "rec_${timestamp}.m4a")

        pcmBufferSize = AudioRecord.getMinBufferSize(
          SAMPLE_RATE,
          CHANNEL_CONFIG_IN,
          AUDIO_FORMAT_IN
        )
        if (pcmBufferSize == AudioRecord.ERROR_BAD_VALUE) {
          throw IllegalStateException("AudioRecord getMinBufferSize failed.")
        }

        coroutineScope {
          // 消费者 (Job C)
          launch {
            PcmToAacEncoder(pcmBufferSize).encode(pcmChannel, aacFile)
          }

          // 生产者 (Job P)
          launch {
            runRecordingLoop(pcmChannel, startTime)
          }
        }

        // 6. 检查时长
        val finalElapsed = System.currentTimeMillis() - startTime
        if (finalElapsed < MIN_RECORDING_MS) {
          throw RecordingTooShortException()
        }

        // 两个协程都成功完成 (无异常抛出)
        Log.i(TAG, "top job recording successfully.")
        recordingSuccess = true
      } catch (e: Exception) {
        Log.i(TAG, "top job recording failed or was cancelled: ${e.message}")
        lastError = e
      } finally {
        //清理本job的资源, 子任务的资源自己负责清理, 如子录音job, 子编码job
        isRecording.set(false)
        pcmChannel.close(lastError)
        silenceTimeOutJob?.cancel()
        maxTimeJob?.cancel()
        autoDoneTimeOutJob?.cancel()

        if (recordingSuccess && aacFile != null) {
          val size = "${aacFile.length() / 1024} kb"
          Log.i(TAG, "top job Callback: Success, size:$size")
          withContext(Dispatchers.Main) {
            listener?.onRecordComplete(aacFile.absolutePath, System.currentTimeMillis() - startTime)
          }
        } else {
          // 如果失败了, 删除垃圾文件
          aacFile?.delete()

          withContext(Dispatchers.Main + NonCancellable) {
            // 转换错误类型
            when (lastError) {
              is RecordingTooShortException -> {
                listener?.onRecordFailure(FAIL_TYPE_TOO_SHORT, null, lastError.message)
              }
              is SilentToLongCancellation -> {
                listener?.onRecordFailure(FAIL_TYPE_NO_INPUT, null, lastError.message)
              }
              is CancellationException -> {
                listener?.onRecordFailure(FAIL_TYPE_CANCEL, null, "Cancelled by user")
              }
              else -> {
                listener?.onRecordFailure(FAIL_TYPE_ERROR, lastError, "errMsg:${lastError?.message}")
              }
            }
            listener?.onStopAudioRecording()
          }
        }
      }
    }
  }

  @SuppressLint("MissingPermission")
  @Throws(IOException::class, IllegalStateException::class)
  private fun setupAudioRecord() {
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
          Log.i(TAG, "Noise suppression enabled")
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
//        Log.i(TAG", "Echo cancellation enabled")
//      }
//    }
//  }

  /**
   * 生产者循环 (V5)
   */
  @Throws(IOException::class, IllegalStateException::class)
  private suspend fun runRecordingLoop(pcmChannel: Channel<ByteArray>, startTime: Long) {
    try {
      setupAudioRecord()

      val audioRecord = audioRecord ?: return
      audioRecord.startRecording()
      withContext(Dispatchers.Main) {
        listener?.onRecordStart()
      }

      //5秒超时自动失败的任务
      silenceTimeOutJob?.cancel()
      silenceTimeOutJob = scope.launch {
        delay(SILENCE_TIMEOUT)
        if (isRecording.get()) {
          Log.i(TAG, "Record 静音5秒超时")
          recordingJob?.cancel(SilentToLongCancellation())
        }
      }

      val pcmBuffer = ByteArray(pcmBufferSize)
      var lastProgressTime = System.currentTimeMillis()
      var hasDetectedSpeech = false

      while (isRecording.get()) {
        val readSize = audioRecord.read(pcmBuffer, 0, pcmBuffer.size)
        when {
          readSize > 0 -> {
            //发送给编码器 (必须 copy, 否则 pcmBuffer 会被覆盖)
            val dataToSend = pcmBuffer.copyOf(readSize)
            pcmChannel.send(dataToSend) // 挂起点 (可取消)

            // 立即回调进度
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
                      Log.i(TAG, "Record 录音时长已经60秒超时")
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
                    Log.i(TAG, "Recording $autoDoneTime ms没说话了, 结束!")
                    stopRecording()
                  }
                }
              }
              withContext(Dispatchers.Main) { listener?.onRecordProgress(elapsed, volume) }
            }
          }
          readSize < 0 -> {
            throw IOException("AudioRecord read error: $readSize")
          }
          // (readSize == 0 被忽略, 继续循环)
        }
      }
    } catch (e: Exception) {
      Log.i(TAG, "Recording job failed: ${e.message}")
      throw e // 向上抛出, 让 coroutineScope 捕获
    } finally {
      Log.i(TAG, "Cleaning job record resources...")
      pcmChannel.close()

      try {
        audioRecord?.stop()
        audioRecord?.release()
      } catch (e: Exception) {
        Log.e(TAG, "Error stopping AudioRecord", e)
      }
      audioRecord = null

      // 5. 释放 effects
      try {
        ns?.setEnabled(false)
        ns?.release()
      } catch (e: Exception) {
        Log.e(TAG, "Error stopping NoiseSuppression", e)
      }
      ns = null
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

    var sumSquares = 0.0
    for (sample in shortBuffer) {
      sumSquares += sample.toDouble() * sample.toDouble()
    }
    val rms = sqrt(sumSquares / shortBuffer.size)

    if (rms < 1.0) {
      return 0
    }

    //分贝计算, 0是最大声, -60是最安静
    val dbfs = 20 * log10(rms / 32767.0)
    //将 dBFS 映射到 0-100 范围
    val clampedDbfs = dbfs.coerceIn(-60.0, 0.0)
    val normalized = (clampedDbfs + 60) / 60
    return (normalized * 100).toInt()

//    var sum = 0
//    for (sample in shortBuffer) {
//      sum += abs(sample.toInt())
//    }
//    val avg = sum / shortBuffer.size
//    val volume = (avg / 32768.0 * 400).toInt().coerceIn(0, 100)
//    return volume
  }

  /**
   * 停止录音。
   */
  fun stopRecording() {
    Log.i(TAG, "stopRecording() called.")
    isRecording.set(false)
  }

  /**
   * 从外部取消 (例如 ViewModel.onClear())
   */
  fun cancel() {
    Log.i(TAG, "cancel() called.")
    recordingJob?.cancel()
  }

  //对齐上版本的AudioRecordManger方法
  override fun start() {
    startRecording()
  }

  override fun destroy() {
    recordingJob?.cancel()
  }

  override fun stop() {
    stopRecording()
  }

  override fun setRecordListener(l: AudioRecordingListener?) {
    listener = l
  }
}