package com.language.repeater.playvideo.sleeptimer

import android.annotation.SuppressLint
import androidx.annotation.MainThread
import androidx.media3.common.Player
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 全局睡眠定时管理器
 * 使用协程 Flow 来分发剩余时间，UI 可以监听它来显示倒计时
 */
object SleepTimerManager {

  private var timerJob: Job? = null
  private val _remainingSeconds = MutableStateFlow<Long>(-1) // -1 表示未开启

  // UI 可以监听这个 Flow 来实时显示 "还剩 10:00 关闭"
  val remainingSeconds: StateFlow<Long> = _remainingSeconds

  // 记录是否正在运行
  val isRunning: Boolean
    get() = _remainingSeconds.value > 0

  /**
   * 开始倒计时
   * @param minutes 分钟数
   * @param player 播放器实例 (倒计时结束时用来暂停)
   */
  @OptIn(DelicateCoroutinesApi::class)
  fun startTimer(seconds: Long, player: Player) {
    stopTimer() // 先取消之前的

    val totalSeconds = seconds

    // 使用 MainScope 或自定义 Scope，确保应用在前台时能跑
    // 这里为了简单，我们在内部开启协程，实际开发中最好注入 Scope
    timerJob = GlobalScope.launch {
      _remainingSeconds.value = totalSeconds

      // 倒计时循环
      while (_remainingSeconds.value > 0) {
        delay(1000) // 等待 1 秒
        _remainingSeconds.value -= 1
      }

      // 倒计时结束
      if (_remainingSeconds.value == 0L) {
        withContext(Dispatchers.Main) {
          performSleepAction(player)
        }
      }

      stopTimer()
    }
  }

  /**
   * 取消定时
   */
  fun stopTimer() {
    timerJob?.cancel()
    timerJob = null
    _remainingSeconds.value = -1
  }

  /**
   * 执行关闭动作
   */
  @MainThread
  private fun performSleepAction(player: Player) {
    if (player.isPlaying) {
      player.pause()
    }
    // 你也可以在这里添加 System.exit(0) 或者 finishAffinity() 退出 App
    // 但通常暂停播放对用户来说就足够了
  }

  // 辅助方法：格式化时间 (mm:ss)
  @SuppressLint("DefaultLocale")
  fun formatTime(seconds: Long): String {
    if (seconds <= 0) return ""
    val m = TimeUnit.SECONDS.toMinutes(seconds)
    val s = seconds - TimeUnit.MINUTES.toSeconds(m)
    return String.format("%02d:%02d", m, s)
  }
}