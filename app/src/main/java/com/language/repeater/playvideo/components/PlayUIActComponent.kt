package com.language.repeater.playvideo.components

import android.R.attr.fragment
import android.annotation.SuppressLint
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import androidx.transition.TransitionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.language.repeater.R
import com.language.repeater.SettingPageKey
import com.language.repeater.defaultNavOptions
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.pcm.FFmpegUtil
import com.language.repeater.playcore.SleepTimerManager
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.playvideo.history.HistorySheetFragment
import com.language.repeater.playvideo.playlist.PlaylistSheetFragment
import com.language.repeater.playvideo.sleeptimer.SleepTimerSheetFragment
import com.language.repeater.sentence.SentenceStoreUtil
import com.language.repeater.utils.ResourcesUtil
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.flow.collectLatest
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

  @SuppressLint("SetTextI18n")
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
    fragment.binding.insertSentence.setOnClickListener(this)
    fragment.binding.subActionMore.setOnClickListener(this)
    fragment.binding.ivSetting.setOnClickListener(this)
    fragment.binding.setTimer.setOnClickListener(this)
    fragment.binding.playSpeed.setOnClickListener(this)
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

    fragment.viewModel.editMode.onEach {
      if (it != fragment.binding.editSwitch.isSelected) {
        if (it) {
          TransitionManager.beginDelayedTransition(fragment.binding.root)
          fragment.binding.editSwitch.isSelected = true
          fragment.binding.editLayout.visibility = View.VISIBLE
        } else {
          TransitionManager.beginDelayedTransition(fragment.binding.root)
          fragment.binding.editSwitch.isSelected = false
          fragment.binding.editLayout.visibility = View.GONE
        }
      }
    }.launchIn(uiScope)

    fragment.viewModel.playSpeedState.onEach { speed ->
      when (speed) {
        0.25f -> fragment.binding.playSpeedTv.text = "0.25"
        0.5f -> fragment.binding.playSpeedTv.text = "0.5X"
        0.75f -> fragment.binding.playSpeedTv.text = "0.75"
        1.0f -> fragment.binding.playSpeedTv.text = "1.0X"
        1.25f -> fragment.binding.playSpeedTv.text = "1.25"
        1.5f -> fragment.binding.playSpeedTv.text = "1.5X"
        2.0f -> fragment.binding.playSpeedTv.text = "2.0X"
        else -> fragment.binding.playSpeedTv.text = "未知速度" // 如果有其他速度值，显示为未知速度
      }
    }.launchIn(uiScope)

    uiScope.launch {
      SleepTimerManager.remainingSeconds.collectLatest { seconds ->
        if (seconds > 0) {
          fragment.binding.setTimerTv.visibility = View.VISIBLE
          fragment.binding.setTimerTv.text = SleepTimerManager.formatTime(seconds)
        } else {
          fragment.binding.setTimerTv.visibility = View.GONE
        }
      }
    }
  }

  override fun onClick(v: View?) {
    when (v) {
      fragment.binding.playSpeed -> {
        showSpeedMenu()
      }
      fragment.binding.setTimer -> {
        val sheet = SleepTimerSheetFragment()
        sheet.show(fragment.childFragmentManager, "SleepTimer")
      }
      fragment.binding.ivSetting -> {
        fragment.findNavController().navigate(SettingPageKey, defaultNavOptions)
      }
      fragment.binding.subActionMore -> {
        showMoreMenu()
      }
      fragment.binding.insertSentence -> {
        fragment.viewModel.insertSentence()
      }
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
        val sheet = PlaylistSheetFragment()
        sheet.show(fragment.childFragmentManager, "PlaylistSheet")
      }
      fragment.binding.editSwitch -> {
        if (fragment.binding.editSwitch.isSelected) {
          fragment.viewModel.editMode(false)
        } else {
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

  private fun showSpeedMenu() {
    val popup = ResourcesUtil.createLightPopMenu(context, fragment.binding.playSpeed)
    popup.menuInflater.inflate(R.menu.menu_play_speed, popup.menu)
    popup.setOnMenuItemClickListener { menuItem ->
      when (menuItem.itemId) {
        R.id.action_0_25 -> {
          fragment.viewModel.getPlayer()?.setPlaybackSpeed(0.25f)
        }
        R.id.action_0_5 -> {
          fragment.viewModel.getPlayer()?.setPlaybackSpeed(0.5f)
        }
        R.id.action_0_75 -> {
          fragment.viewModel.getPlayer()?.setPlaybackSpeed(0.75f)
        }
        R.id.action_1_0 -> {
          fragment.viewModel.getPlayer()?.setPlaybackSpeed(1.0f)
        }
        R.id.action_1_25 -> {
          fragment.viewModel.getPlayer()?.setPlaybackSpeed(1.25f)
        }
        R.id.action_1_5 -> {
          fragment.viewModel.getPlayer()?.setPlaybackSpeed(1.5f)
        }
        R.id.action_2_0 -> {
          fragment.viewModel.getPlayer()?.setPlaybackSpeed(2.0f)
        }
      }
      true
    }
    popup.show()
  }

  private fun showMoreMenu() {
    val popup = ResourcesUtil.createLightPopMenu(context, fragment.binding.subActionMore)
    popup.menuInflater.inflate(R.menu.menu_sub_action_more, popup.menu)
    popup.setOnMenuItemClickListener { menuItem ->
      when (menuItem.itemId) {
        R.id.action_split_auto -> {
          autoLoadSentences(true)
        }
        R.id.action_split_subtitle -> {
          autoLoadSentences(false)
        }
        R.id.action_history_list -> {
          val sheet = HistorySheetFragment()
          sheet.show(fragment.childFragmentManager, "HistorySheet")
        }
      }
      true
    }
    popup.show()
  }

  private fun autoLoadSentences(auto: Boolean) {
    if (!auto) {
      val item = fragment.viewModel.currentMediaItem.value
      val subUri = item?.localConfiguration?.subtitleConfigurations?.firstOrNull()?.uri
      if (subUri == null) {
        ToastUtil.toast("当前视频没有对应的字幕文件")
      }
      return
    }

    val isPlaying = fragment.viewModel.isUiPlaying.value
    MaterialAlertDialogBuilder(context)
      .setTitle("重新生成句子信息")
      .setMessage("确定要重新生成断句信息吗？这会覆盖当前的句子列表信息")
      .setPositiveButton("自动分割") { _, _ ->
        fragment.viewModel.loadSentenceData(auto)
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