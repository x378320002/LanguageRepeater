package com.language.repeater.playcore

import android.annotation.SuppressLint
import com.language.repeater.R
import com.language.repeater.app.MainApp
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object SleepTimerManager {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var timerJob: Job? = null

  private val _remainingSeconds = MutableStateFlow(-1L)
  val remainingSeconds: StateFlow<Long> = _remainingSeconds

  val isRunning: Boolean
    get() = _remainingSeconds.value > 0

  fun startTimerMinutes(minutes: Int, onTimeout: (() -> Unit)? = null) {
    startTimer(minutes * 60L, onTimeout)
  }

  fun startTimer(seconds: Long, onTimeout: (() -> Unit)? = null) {
    stopTimer()
    if (seconds <= 0) {
      return
    }

    timerJob = scope.launch {
      _remainingSeconds.value = seconds
      while (_remainingSeconds.value > 0) {
        delay(1000)
        _remainingSeconds.value -= 1
      }
      if (_remainingSeconds.value == 0L) {
        onTimeout?.invoke()
        MainApp.instance.playbackGraph.player.pause()
      }
      _remainingSeconds.value = -1
    }

    val message = MainApp.instance.resources.getString(
      R.string.timer_set_toast,
      formatTime(seconds)
    )
    ToastUtil.toast(message)
  }

  fun stopTimer() {
    timerJob?.cancel()
    timerJob = null
    _remainingSeconds.value = -1
  }

  @SuppressLint("DefaultLocale")
  fun formatTime(seconds: Long): String {
    if (seconds <= 0) {
      return ""
    }
    val minutes = TimeUnit.SECONDS.toMinutes(seconds)
    val remainingSeconds = seconds - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, remainingSeconds)
  }
}
