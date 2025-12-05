package com.language.repeater.playvideo.components

import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.widgets.ScrollWaveformView
import com.language.repeater.widgets.ScrollWaveformView.ABHitResult
import com.language.repeater.widgets.ScrollWaveformView.OnSeekListener
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Date: 2025-11-14
 * Time: 16:21
 * Description: 可以拖动的波形图控件组件, 这个空间可能非常复杂, 单独开一个组件
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

  val waveformView
    get() = fragment.binding.audioProgressWaveView

  @UnstableApi
  override fun onCreateView() {
    super.onCreateView()
    //滚动波形图句子数据填充
    viewModel.sentencesFlow.onEach {
      waveformView.setSentenceData(it)
    }.launchIn(uiScope)

    //滚动波形图数据填充
    viewModel.pcmLoaderStateFlow.onEach {loader ->
      if (loader != null) {
        // 加载PCM文件
        waveformView.setPCMLoader(loader, 0) {
          Log.i(PlayVideoFragment.Companion.TAG, "audioProgressWaveView loadWindow $it")
        }
      }
    }.launchIn(uiScope)

    //波形图ab句子更新
    playComponent.curAbSentenceFlow.onEach {
      waveformView.curABSeg = it
      waveformView.invalidate()
    }.launchIn(uiScope)

    //波形进度的更新
    playComponent.curPosSecFlow.onEach {
      //处理波形图的更新
      if (it >= 0) {
        waveformView.updatePosition(it)
      }
    }.launchIn(uiScope)


    waveformView.setOnCustomClickListener {
      val isPlaying = playComponent.player.isPlaying
      if (isPlaying) {
        playComponent.player.pause()
      } else {
        playComponent.player.play()
      }
    }
    handleDrag()
  }

  private fun handleDrag() {
    val playComponent = fragment.playComponent
    val player = playComponent.player

    //拖动波形图的逻辑
    waveformView.setOnSeekListener(object : OnSeekListener {
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
    waveformView.setOnABChangeListener(object : ScrollWaveformView.OnABChangeListener{
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