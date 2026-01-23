package com.language.repeater.playvideo.components

import android.view.View
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.transition.AutoTransition
import androidx.transition.ChangeBounds
import androidx.transition.ChangeClipBounds
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialContainerTransform
import com.language.repeater.R
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.playvideo.history.HistorySheetFragment
import com.language.repeater.playvideo.playlist.PlaylistSheetFragment
import com.language.repeater.playvideo.sleeptimer.SleepTimerSheetFragment
import com.language.repeater.pcm.FFmpegUtil
import com.language.repeater.utils.ResourcesUtil
import com.language.repeater.sentence.SentenceStoreUtil
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
    fragment.binding.voiceRepeatSwitch.setOnClickListener(this)
    fragment.binding.playPauseBtn.setOnClickListener(this)
    fragment.binding.seekPreSentence.setOnClickListener(this)
    fragment.binding.seekNextSentence.setOnClickListener(this)
    fragment.binding.backSentenceHead.setOnClickListener(this)
    fragment.binding.playList.setOnClickListener(this)
    fragment.binding.editSwitch.setOnClickListener(this)
    fragment.binding.mergePre.setOnClickListener(this)
    fragment.binding.mergeNext.setOnClickListener(this)
    fragment.binding.deleteSentence.setOnClickListener(this)
    fragment.binding.splitSentence.setOnClickListener(this)
    //fragment.binding.voiceNext.setOnClickListener(this)
    //fragment.binding.voicePrevious.setOnClickListener(this)
    //fragment.binding.reloadSentence.setOnClickListener(this)
    //fragment.binding.splitSentence.setOnClickListener(this)
    //fragment.binding.deleteSentence.setOnClickListener(this)
    //fragment.binding.historyList.setOnClickListener(this)
    //fragment.binding.playList.setOnClickListener(this)
    //fragment.binding.repeatMode.setOnClickListener(this)
    //fragment.binding.sleepTimeBtn.setOnClickListener(this)
    //fragment.binding.clearTemp.setOnClickListener(this)
    //fragment.binding.mergePre.setOnClickListener(this)
    //fragment.binding.mergeNext.setOnClickListener(this)

    //fragment.viewModel.playerRepeatMode.onEach {
    //  val text = when (it) {
    //    Player.REPEAT_MODE_ONE -> {
    //      ResourcesUtil.getString(R.string.repeat_one)
    //    }
    //
    //    Player.REPEAT_MODE_ALL -> {
    //      ResourcesUtil.getString(R.string.repeat_all)
    //    }
    //
    //    else -> {
    //      ResourcesUtil.getString(R.string.repeat_off)
    //    }
    //  }
    //  fragment.binding.repeatMode.text = text
    //}.launchIn(uiScope)
  }

  override fun onClick(v: View?) {
    when (v) {
      fragment.binding.mergePre -> {
        fragment.viewModel.mergePreSentence()
      }
      fragment.binding.mergeNext -> {
        fragment.viewModel.mergeNextSentence()
      }
      fragment.binding.deleteSentence -> {
        deleteCurSen()
      }
      fragment.binding.splitSentence -> {
        splitCurSen()
      }
      fragment.binding.voiceRepeatSwitch -> {
        fragment.viewModel.toggleRepeat()
      }
      fragment.binding.playPauseBtn -> {
        fragment.viewModel.togglePlayPause()
      }
      fragment.binding.seekPreSentence -> {
        fragment.viewModel.seekToPreviousSentence()
      }
      fragment.binding.seekNextSentence -> {
        fragment.viewModel.seekToNextSentence()
      }
      fragment.binding.backSentenceHead -> {
        fragment.viewModel.backToSentenceHead()
      }
      fragment.binding.playList -> {
        val sheet = HistorySheetFragment()
        sheet.show(fragment.childFragmentManager, "HistorySheet")
      }
      fragment.binding.editSwitch -> {
        if (fragment.binding.editSwitch.isSelected) {
          TransitionManager.beginDelayedTransition(fragment.binding.root)
          fragment.binding.editSwitch.isSelected = false
          fragment.binding.editLayout.visibility = View.GONE
          fragment.viewModel.editMode(false)
        } else {
          TransitionManager.beginDelayedTransition(fragment.binding.root)
          fragment.binding.editSwitch.isSelected = true
          fragment.binding.editLayout.visibility = View.VISIBLE
          fragment.viewModel.editMode(true)
        }
      }
    }
     //when (v) {
    //  fragment.binding.mergePre -> {
    //    fragment.viewModel.mergePreSentence()
    //  }
    //
    //  fragment.binding.mergeNext -> {
    //    fragment.viewModel.mergeNextSentence()
    //  }
    //
    //  fragment.binding.sleepTimeBtn -> {
    //    val sheet = SleepTimerSheetFragment()
    //    sheet.show(fragment.childFragmentManager, "SleepTimer")
    //  }
    //
    //  fragment.binding.repeatMode -> {
    //    fragment.viewModel.togglePlayerRepeatMode()
    //  }
    //
    //  fragment.binding.playList -> {
    //    val sheet = PlaylistSheetFragment()
    //    sheet.show(fragment.childFragmentManager, "PlaylistSheet")
    //  }
    //
    //  fragment.binding.historyList -> {
    //    val sheet = HistorySheetFragment()
    //    sheet.show(fragment.childFragmentManager, "HistorySheet")
    //  }
    //
    //  fragment.binding.voiceNext -> {
    //    fragment.viewModel.seekToNextSentence()
    //  }
    //
    //  fragment.binding.voicePrevious -> {
    //    fragment.viewModel.seekToPreviousSentence()
    //  }
    //
    //  fragment.binding.reloadSentence -> {
    //    autoLoadSentences()
    //  }
    //
    //  fragment.binding.splitSentence -> {
    //    splitCurSen()
    //  }
    //
    //  fragment.binding.deleteSentence -> {
    //    deleteCurSen()
    //  }
    //
    //  fragment.binding.clearTemp -> {
    //    val player = fragment.viewModel.getPlayer() ?: return
    //    // 必须在主线程提取数据
    //    val items = mutableListOf<String>()
    //    for (i in 0 until player.mediaItemCount) {
    //      items.add(player.getMediaItemAt(i).mediaId)
    //    }
    //    fScope.launch {
    //      FFmpegUtil.clearTempData(context, items)
    //      SentenceStoreUtil.clearTempData(context, items)
    //      ToastUtil.toast("清除成功")
    //    }
    //  }
    //}
  }

  private fun autoLoadSentences() {
    val isPlaying = fragment.viewModel.isUiPlaying.value
    MaterialAlertDialogBuilder(context)
      .setTitle("重新生成断句信息")
      .setMessage("确定要重新生成断句信息吗？这会覆盖当前的句子信息")
      .setNeutralButton("基于字幕分割(如果有)") { dialog, _ ->
        fragment.viewModel.loadSentenceData(false)
      }
      .setPositiveButton("自动分割") { _, _ ->
        fragment.viewModel.loadSentenceData(true)
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

  private fun splitCurSen() {
    val isPlaying = fragment.viewModel.isUiPlaying.value
    MaterialAlertDialogBuilder(context)
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
    MaterialAlertDialogBuilder(context)
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
    fragment.viewModel.pause()
  }
}