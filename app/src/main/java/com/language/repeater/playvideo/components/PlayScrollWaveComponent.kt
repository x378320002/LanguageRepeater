package com.language.repeater.playvideo.components

import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.widgets.ScrollingWaveformView
import com.language.repeater.widgets.ScrollingWaveformView.ABHitResult
import com.language.repeater.widgets.ScrollingWaveformView.OnSeekListener
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Date: 2025-11-14
 * Time: 16:21
 * Description: 耳机播控处理逻辑
 */
class PlayScrollWaveComponent: BaseComponent<PlayVideoFragment>() {
  companion object {
    const val TAG = PlayVideoFragment.Companion.TAG
  }

  val binding
    get() = fragment.binding

  val playComponent
    get() = fragment.playComponent

  val viewModel
    get() = fragment.viewModel

  @UnstableApi
  override fun onCreateView() {
    super.onCreateView()
    //滚动波形图句子数据填充
    viewModel.sentencesFlow.onEach {
      binding.audioProgressWaveView.setSentenceData(it)
    }.launchIn(uiScope)

    //滚动波形图数据填充
    viewModel.pcmLoaderStateFlow.onEach {loader ->
      if (loader != null) {
        // 加载PCM文件
        binding.audioProgressWaveView.setPCMLoader(loader, 0) {
          Log.i(PlayVideoFragment.Companion.TAG, "audioProgressWaveView loadWindow $it")
        }
      }
    }.launchIn(uiScope)

    //波形图ab句子更新
    playComponent.curAbSentenceFlow.onEach {
      binding.audioProgressWaveView.curABSeg = it
      binding.audioProgressWaveView.invalidate()
    }.launchIn(uiScope)

    //波形进度的更新
    playComponent.curPosSecFlow.onEach {
      //处理波形图的更新
      if (it >= 0) {
        binding.audioProgressWaveView.updatePosition(it)
      }
    }.launchIn(uiScope)

    handleDrag()
  }

  private fun handleDrag() {
    val playComponent = fragment.playComponent
    val player = playComponent.player

    //拖动波形图的逻辑
    fragment.binding.audioProgressWaveView.setOnSeekListener(object : OnSeekListener {
      var isPlayWhenStart = false
      override fun onSeekStart() {
        isPlayWhenStart = player.isPlaying
        if (isPlayWhenStart) {
          player.pause()
        }
      }

      override fun onSeeking(position: Float) {
      }

      override fun onSeekEnd(position: Float) {
        playComponent.updateAbSentence(position)
        player.seekTo((position * 1000).toLong())
        if (isPlayWhenStart) {
          player.play()
        }
      }
    })

    //拖动AB边界的逻辑
    fragment.binding.audioProgressWaveView.setOnABChangeListener(object : ScrollingWaveformView.OnABChangeListener{
      var isPlayWhenStart = false
      override fun onABDragStart(dragAbResult: ABHitResult?) {
        isPlayWhenStart = player.isPlaying
        if (isPlayWhenStart) {
          player.pause()
        }
      }

      override fun onABDragging(dragAbResult: ABHitResult?) {
      }

      override fun onABDragEnd(dragAbResult: ABHitResult?) {
        if (isPlayWhenStart) {
          player.play()
        }
      }
    })
  }
}