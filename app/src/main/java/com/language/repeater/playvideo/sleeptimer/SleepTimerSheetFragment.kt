package com.language.repeater.playvideo.sleeptimer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import com.language.repeater.R
import com.language.repeater.databinding.LayoutSleepTimerSheetBinding
import com.language.repeater.playvideo.BasePlaySheetFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SleepTimerSheetFragment(
  private val player: Player
) : BasePlaySheetFragment(player) {

  private var _binding: LayoutSleepTimerSheetBinding? = null
  private val binding get() = _binding!!

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = LayoutSleepTimerSheetBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    setupClickListeners()
    observeTimerState()
  }

  private fun setupClickListeners() {
    // 关闭定时
    binding.btnOff.setOnClickListener {
      SleepTimerManager.stopTimer()
      dismiss()
      Toast.makeText(context, "定时已关闭", Toast.LENGTH_SHORT).show()
    }

    // 10 分钟
    binding.btn10Min.setOnClickListener { startTimer(10 * 60) }

    // 20 分钟
    binding.btn20Min.setOnClickListener { startTimer(20 * 60) }

    // 30 分钟
    binding.btn30Min.setOnClickListener { startTimer(30 * 60) }

    // 60 分钟
    binding.btn60Min.setOnClickListener { startTimer(60 * 60) }

    // 播完当前首 (高级功能)
    binding.btnEndOfTrack.setOnClickListener {
      // 计算当前视频剩余多少秒
      val duration = player.duration
      val current = player.currentPosition
      if (duration > 0 && current >= 0) {
        val remainingMillis = duration - current
        // 向上取整转为分钟，加 1 分钟缓冲防止提前一点点关
        val seconds = remainingMillis / 1000
        startTimer(seconds)
      } else {
        Toast.makeText(context, "无法获取当前时长", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun startTimer(seconds: Long) {
    SleepTimerManager.startTimer(seconds, player)
    dismiss()
    Toast.makeText(context, "将在 ${SleepTimerManager.formatTime(seconds)} 分钟后停止播放", Toast.LENGTH_SHORT).show()
  }

  // 监听倒计时，如果用户再次打开这个弹窗，能看到还剩多久
  private fun observeTimerState() {
    viewLifecycleOwner.lifecycleScope.launch {
      SleepTimerManager.remainingSeconds.collectLatest { seconds ->
        if (seconds > 0) {
          binding.tvCountdownStatus.visibility = View.VISIBLE
          binding.tvCountdownStatus.text = getString(R.string.sleep_time, SleepTimerManager.formatTime(seconds))

          // 高亮显示"关闭定时"按钮，提示用户可以取消
          binding.btnOff.text = "关闭定时 (运行中)"
        } else {
          binding.tvCountdownStatus.visibility = View.GONE
          binding.btnOff.text = "不开启"
        }
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}