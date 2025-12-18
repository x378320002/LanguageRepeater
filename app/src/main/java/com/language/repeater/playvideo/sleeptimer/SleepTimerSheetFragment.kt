package com.language.repeater.playvideo.sleeptimer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.language.repeater.R
import com.language.repeater.databinding.LayoutSleepTimerSheetBinding
import com.language.repeater.playvideo.BasePlaySheetFragment
import com.language.repeater.playvideo.PlayerViewModel
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SleepTimerSheetFragment : BasePlaySheetFragment() {

  private val viewModel: PlayerViewModel by activityViewModels()
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
    binding.btnOff.setOnClickListener {
      viewModel.stopSleepTimer()
      dismiss()
      ToastUtil.toast("定时已关闭")
    }

    binding.btn10Min.setOnClickListener { startTimer(10 * 60) }
    binding.btn20Min.setOnClickListener { startTimer(20 * 60) }
    binding.btn30Min.setOnClickListener { startTimer(30 * 60) }
    binding.btn60Min.setOnClickListener { startTimer(60 * 60) }

    // 播完当前首
    binding.btnEndOfTrack.setOnClickListener {
      // 获取 Player 信息依然通过 ViewModel (或 connection) 的状态流，或者 ViewModel 的 getPlayer()
      // 这里为了计算时长，短暂获取一下 player 是可以的，但不传给 Manager
      val player = viewModel.getPlayer()
      if (player == null) {
        ToastUtil.toast("当前播放器未就绪")
        return@setOnClickListener
      }

      val duration = player.duration
      val current = player.currentPosition
      if (duration > 0 && current >= 0) {
        val remainingMillis = duration - current
        // 向上取整转为秒，加 2 秒缓冲
        val seconds = (remainingMillis / 1000) + 2
        startTimer(seconds)
      } else {
        ToastUtil.toast("无法获取当前时长")
      }
    }
  }

  private fun startTimer(seconds: Long) {
    // 直接调用 ViewModel，不传 Player
    viewModel.startSleepTimer(seconds)
    dismiss()
    ToastUtil.toast("将在 ${SleepTimerManager.formatTime(seconds)} 后停止播放")
  }

  private fun observeTimerState() {
    viewLifecycleOwner.lifecycleScope.launch {
      SleepTimerManager.remainingSeconds.collectLatest { seconds ->
        if (seconds > 0) {
          binding.tvCountdownStatus.visibility = View.VISIBLE
          binding.tvCountdownStatus.text = getString(R.string.sleep_time, SleepTimerManager.formatTime(seconds))
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