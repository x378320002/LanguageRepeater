package com.language.repeater.playvideo.components

import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.playvideo.PlayVideoFragment
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Date: 2025-11-14
 * Time: 16:21
 * Description: 耳机播控处理逻辑
 */
class PlayAllWaveComponent: BaseComponent<PlayVideoFragment>() {
  companion object {
    const val TAG = PlayVideoFragment.Companion.TAG
  }

  val viewModel
    get() = fragment.viewModel

  @UnstableApi
  override fun onCreateView() {
    super.onCreateView()
    //全量波形图数据填充
    viewModel.allWaveDataFlow.onEach {
      fragment.binding.audioWaveView.setPcmData(it)
    }.launchIn(uiScope)

    //波形进度的更新
    fragment.playComponent.curPosSecFlow.onEach {
      //处理波形图的更新
      if (it >= 0) {
        val total = fragment.playComponent.player.duration
        fragment.binding.audioWaveView.updatePosition(it * 1000 / total)
      }
    }.launchIn(uiScope)
  }
}