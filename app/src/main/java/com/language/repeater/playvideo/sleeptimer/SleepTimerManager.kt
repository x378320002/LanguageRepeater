package com.language.repeater.playvideo.sleeptimer

import android.annotation.SuppressLint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object SleepTimerManager {

  // 使用 SupervisorJob + MainDispatcher，比 GlobalScope 更可控
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var timerJob: Job? = null

  private val _remainingSeconds = MutableStateFlow<Long>(-1)
  val remainingSeconds: StateFlow<Long> = _remainingSeconds

  val isRunning: Boolean
    get() = _remainingSeconds.value > 0

  /**
   * 开始倒计时
   * @param seconds 秒数
   * @param onTimeout 倒计时结束时执行的回调
   */
  fun startTimer(seconds: Long, onTimeout: () -> Unit) {
    stopTimer()

    if (seconds <= 0) return

    timerJob = scope.launch {
      _remainingSeconds.value = seconds

      while (_remainingSeconds.value > 0) {
        delay(1000)
        _remainingSeconds.value -= 1
      }

      // 倒计时结束，执行回调
      if (_remainingSeconds.value == 0L) {
        onTimeout()
      }

      // 重置状态
      _remainingSeconds.value = -1
    }
  }

  fun stopTimer() {
    timerJob?.cancel()
    timerJob = null
    _remainingSeconds.value = -1
  }

  @SuppressLint("DefaultLocale")
  fun formatTime(seconds: Long): String {
    if (seconds <= 0) return ""
    val m = TimeUnit.SECONDS.toMinutes(seconds)
    val s = seconds - TimeUnit.MINUTES.toSeconds(m)
    return String.format("%02d:%02d", m, s)
  }
}