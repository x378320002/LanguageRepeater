package com.language.repeater.playvideo.sleeptimer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.language.repeater.R
import com.language.repeater.databinding.LayoutSleepTimerSheetBinding
import com.language.repeater.playcore.SleepTimerManager
import com.language.repeater.foundation.BasePlaySheetFragment
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
      SleepTimerManager.stopTimer()
      dismiss()
      ToastUtil.toast(R.string.timer_set_off)
    }

    binding.btn10Min.setOnClickListener { startTimer(10) }
    binding.btn20Min.setOnClickListener { startTimer(20) }
    binding.btn30Min.setOnClickListener { startTimer(30) }
    binding.btn45Min.setOnClickListener { startTimer(45) }
    binding.btn60Min.setOnClickListener { startTimer(60) }

    // 播完当前首
    binding.btnEndOfTrack.setOnClickListener {
      SleepTimerManager.startByCurrentItem(viewModel.getPlayer())
      dismiss()
    }
  }

  private fun startTimer(minutes: Int) {
    // 直接调用 ViewModel，不传 Player
    SleepTimerManager.startTimerMinutes(minutes)
    dismiss()
  }

  private fun observeTimerState() {
    viewLifecycleOwner.lifecycleScope.launch {
      SleepTimerManager.remainingSeconds.collectLatest { seconds ->
        if (seconds > 0) {
          binding.tvCountdownStatus.visibility = View.VISIBLE
          binding.tvCountdownStatus.text = getString(R.string.sleep_time, SleepTimerManager.formatTime(seconds))
        } else {
          binding.tvCountdownStatus.visibility = View.GONE
        }
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}