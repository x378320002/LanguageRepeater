package com.language.repeater.playcore

import android.annotation.SuppressLint
import androidx.media3.common.Player
import com.language.repeater.MyApp
import com.language.repeater.R
import com.language.repeater.utils.ResourcesUtil
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

//Repository层, 比ViewModel更低一个层级
object SleepTimerManager {

  // 使用 SupervisorJob + MainDispatcher，比 GlobalScope 更可控
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var timerJob: Job? = null

  private val _remainingSeconds = MutableStateFlow<Long>(-1)
  val remainingSeconds: StateFlow<Long> = _remainingSeconds

  val isRunning: Boolean
    get() = _remainingSeconds.value > 0

  fun startByCurrentItem(player: Player?) {
    if (player == null) {
      ToastUtil.toast("当前播放器未就绪")
      return
    }

    val duration = player.duration
    val current = player.currentPosition
    if (duration > 0 && current >= 0) {
      val remainingMillis = duration - current
      // 向上取整转为秒，加 1 秒缓冲
      val seconds = (remainingMillis / 1000) + 1
      startTimer(seconds)
    } else {
      ToastUtil.toast("无法获取当前时长")
    }
  }

  fun startTimerMinutes(minutes: Int, onTimeout: (() -> Unit)? = null) {
    startTimer(minutes * 60L, onTimeout)
  }

  /**
   * 开始倒计时
   * @param seconds 秒数
   * @param onTimeout 倒计时结束时执行的回调
   */
  fun startTimer(seconds: Long, onTimeout: (() -> Unit)? = null) {
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
        onTimeout?.invoke()
        PlaybackCore.getInstance(MyApp.instance).pause()
      }

      // 重置状态
      _remainingSeconds.value = -1
    }

    val string = MyApp.instance.resources.getString(R.string.timer_set_toast, formatTime(seconds))
    ToastUtil.toast(string)
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