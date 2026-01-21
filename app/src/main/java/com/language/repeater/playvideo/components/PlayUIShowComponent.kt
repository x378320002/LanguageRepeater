package com.language.repeater.playvideo.components

import android.view.View
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
class PlayUIShowComponent : BaseComponent<PlayVideoFragment>() {
  companion object {
    const val TAG = PlayVideoFragment.Companion.TAG
  }

  @UnstableApi
  override fun onCreateView() {
    super.onCreateView()

    fragment.viewModel.repeatable.onEach {
      fragment.binding.voiceRepeatSwitch.isSelected = it
      fragment.binding.audioProgressWaveView.isRepeated = it
      fragment.binding.audioProgressWaveView.invalidate()
    }.launchIn(uiScope)

    fragment.viewModel.isUiPlaying.onEach {
      fragment.binding.playPauseBtn.isSelected = it
    }.launchIn(uiScope)

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
}