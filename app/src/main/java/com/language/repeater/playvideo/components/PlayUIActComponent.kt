package com.language.repeater.playvideo.components

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

  val playComponent
    get() = fragment.playComponent

  @UnstableApi
  override fun onCreateView() {
    super.onCreateView()

    val activity = (fragment.activity as? MainActivity)
    activity?.volumeFlow?.onEach {
      if (it == 1) {
        playComponent.seekToNextSentence()
      } else {
        playComponent.seekToPreviousSentence()
      }
    }?.launchIn(uiScope)

    playComponent.setRepeat(fragment.binding.voiceRepeatSwitch.isChecked)
    fragment.binding.voiceRepeatSwitch.setOnCheckedChangeListener { _, checked ->
      playComponent.setRepeat(fragment.binding.voiceRepeatSwitch.isChecked)
    }

    fragment.binding.voiceNext.setOnClickListener(this)
    fragment.binding.voicePrevious.setOnClickListener(this)
    fragment.binding.reloadSentence.setOnClickListener(this)
    fragment.binding.saveSentence.setOnClickListener(this)
    fragment.binding.splitSentence.setOnClickListener(this)
    fragment.binding.deleteSentence.setOnClickListener(this)
    fragment.binding.historyList.setOnClickListener(this)
    fragment.binding.playList.setOnClickListener(this)
    fragment.binding.repeatMode.setOnClickListener(this)
    fragment.binding.sleepTimeBtn.setOnClickListener(this)
  }

  override fun onClick(v: View?) {
    when (v) {
      fragment.binding.sleepTimeBtn -> {
        val sheet = SleepTimerSheetFragment(playComponent.player)
        sheet.show(fragment.childFragmentManager, "SleepTimer")
      }

      fragment.binding.repeatMode -> {
        if (playComponent.player.repeatMode == Player.REPEAT_MODE_ONE) {
          playComponent.player.repeatMode = Player.REPEAT_MODE_ALL
          fragment.binding.repeatMode.text = ResourcesUtil.getString(R.string.repeat_all)
        } else {
          playComponent.player.repeatMode = Player.REPEAT_MODE_ONE
          fragment.binding.repeatMode.text = ResourcesUtil.getString(R.string.repeat_one)
        }
      }

      fragment.binding.playList -> {
        val sheet = PlaylistSheetFragment(fragment, fragment.playComponent.player)
        sheet.show(fragment.childFragmentManager, "PlaylistSheet")
      }

      fragment.binding.historyList -> {
        val sheet = HistorySheetFragment(fragment, fragment.playComponent.player)
        sheet.show(fragment.childFragmentManager, "HistorySheet")
      }

      fragment.binding.voiceNext -> {
        playComponent.seekToNextSentence()
      }

      fragment.binding.voicePrevious -> {
        playComponent.seekToPreviousSentence()
      }

      fragment.binding.reloadSentence -> {
        uiScope.launch {
          fragment.showLoading()
          fragment.viewModel.reloadSentencesAuto()
          fragment.hideLoading()
        }
      }

      fragment.binding.saveSentence -> {
        uiScope.launch {
          fragment.showLoading()
          fragment.viewModel.saveSentenceDataToFile()
          fragment.hideLoading()
        }
      }

      fragment.binding.splitSentence -> {
        AlertDialog.Builder(context)
          .setTitle("确认拆分")
          .setMessage("确定要拆分当前句子吗？")
          .setPositiveButton("确认") { _, _ ->
            val sentence = playComponent.findCurSentence()
            if (sentence != null) {
              fragment.viewModel.splitSentence(sentence, playComponent.curPosSecFlow.value)
            } else {
              ToastUtil.toast("当前没有没有句子可供拆分")
            }
          }
          .setNegativeButton("取消", null)
          .show()
      }

      fragment.binding.deleteSentence -> {
        AlertDialog.Builder(context)
          .setTitle("确认删除")
          .setMessage("确定要删除当前AB句吗？")
          .setPositiveButton("删除") { _, _ ->
            val sentence = playComponent.curAbSentenceFlow.value
            if (sentence != null) {
              fragment.viewModel.deleteSentence(sentence)
            } else {
              ToastUtil.toast("当前没有没有句子可删除")
            }
          }
          .setNegativeButton("取消", null)
          .show()
      }
    }
  }
}