package com.language.repeater.playvideo.components

import android.R.attr.repeatMode
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.language.repeater.MainActivity
import com.language.repeater.R
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.playvideo.history.HistorySheetFragment
import com.language.repeater.playvideo.playlist.PlaylistSheetFragment
import com.language.repeater.playvideo.sleeptimer.SleepTimerSheetFragment
import com.language.repeater.utils.ResourcesUtil
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Date: 2025-11-14
 * Time: 16:21
 * Description: 耳机播控处理逻辑
 */
class PlayUIActComponent : BaseComponent<PlayVideoFragment>(), View.OnClickListener {
  companion object {
    const val TAG = PlayVideoFragment.Companion.TAG
  }

  @UnstableApi
  override fun onCreateView() {
    super.onCreateView()
    fragment.binding.voiceRepeatSwitch.setOnCheckedChangeListener { _, checked ->
      fragment.viewModel.toggleRepeat()
    }
    fragment.binding.voiceNext.setOnClickListener(this)
    fragment.binding.voicePrevious.setOnClickListener(this)
    fragment.binding.reloadSentence.setOnClickListener(this)
    fragment.binding.splitSentence.setOnClickListener(this)
    fragment.binding.deleteSentence.setOnClickListener(this)
    fragment.binding.historyList.setOnClickListener(this)
    fragment.binding.playList.setOnClickListener(this)
    fragment.binding.repeatMode.setOnClickListener(this)
    fragment.binding.sleepTimeBtn.setOnClickListener(this)

    fragment.viewModel.repeatable.onEach {
      fragment.binding.voiceRepeatSwitch.isChecked = it
    }.launchIn(uiScope)

    fragment.viewModel.playerRepeatMode.onEach {
      val text = when (it) {
        Player.REPEAT_MODE_ONE -> {
          ResourcesUtil.getString(R.string.repeat_one)
        }

        Player.REPEAT_MODE_ALL -> {
          ResourcesUtil.getString(R.string.repeat_all)
        }

        else -> {
          ResourcesUtil.getString(R.string.repeat_off)
        }
      }
      fragment.binding.repeatMode.text = text
    }.launchIn(uiScope)
  }

  override fun onClick(v: View?) {
    when (v) {
      fragment.binding.sleepTimeBtn -> {
        val sheet = SleepTimerSheetFragment()
        sheet.show(fragment.childFragmentManager, "SleepTimer")
      }

      fragment.binding.repeatMode -> {
        fragment.viewModel.togglePlayerRepeatMode()
      }

      fragment.binding.playList -> {
        val sheet = PlaylistSheetFragment()
        sheet.show(fragment.childFragmentManager, "PlaylistSheet")
      }

      fragment.binding.historyList -> {
        val sheet = HistorySheetFragment()
        sheet.show(fragment.childFragmentManager, "HistorySheet")
      }

      fragment.binding.voiceNext -> {
        fragment.viewModel.seekToNextSentence()
      }

      fragment.binding.voicePrevious -> {
        fragment.viewModel.seekToPreviousSentence()
      }

      fragment.binding.reloadSentence -> {
        uiScope.launch {
          fragment.showLoading()
          fragment.viewModel.loadSentenceData()
          fragment.hideLoading()
        }
      }

      fragment.binding.splitSentence -> {
        splitCurSen()
      }

      fragment.binding.deleteSentence -> {
        deleteCurSen()
      }
    }
  }

  private fun splitCurSen() {
    val isPlaying = fragment.viewModel.isUiPlaying.value
    AlertDialog.Builder(context)
      .setTitle("确认拆分")
      .setMessage("确定要拆分当前句子吗？")
      .setPositiveButton("确认") { _, _ ->
        fragment.viewModel.splitCurrentSentence()
      }
      .setNegativeButton("取消", null)
      .setOnDismissListener {
        if (isPlaying) {
          fragment.viewModel.play()
        }
      }
      .show()
    if (isPlaying) {
      fragment.viewModel.pause()
    }
  }

  private fun deleteCurSen() {
    val isPlaying = fragment.viewModel.isUiPlaying.value
    AlertDialog.Builder(context)
      .setTitle("确认删除")
      .setMessage("确定要删除当前AB句吗？")
      .setPositiveButton("删除") { _, _ ->
        fragment.viewModel.deleteCurrentSentence()
      }
      .setNegativeButton("取消", null)
      .setOnDismissListener {
        if (isPlaying) {
          fragment.viewModel.play()
        }
      }
      .show()
    if (isPlaying) {
      fragment.viewModel.pause()
    }
  }
}