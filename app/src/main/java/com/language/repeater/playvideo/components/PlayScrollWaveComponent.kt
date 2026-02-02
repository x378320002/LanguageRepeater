package com.language.repeater.playvideo.components

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

  val viewModel
    get() = fragment.viewModel

  val waveformView
    get() = fragment.binding.audioProgressWaveView

  @UnstableApi
  override fun onCreateView() {
    super.onCreateView()
    //滚动波形图总句子
    viewModel.sentencesFlow.onEach {
      waveformView.setSentenceData(it)
    }.launchIn(uiScope)

    //波形图ab句子
    viewModel.curAbSentenceFlow.onEach {
      if (waveformView.curABSentence != it) {
        waveformView.curABSentence = it
        waveformView.invalidate()
      }
    }.launchIn(uiScope)

    viewModel.editMode.onEach {
      waveformView.isEditMode = it
      waveformView.invalidate()
    }.launchIn(uiScope)

    //滚动波形图数据填充
    viewModel.pcmLoaderStateFlow.onEach {loader ->
      if (loader != null) {
        // 加载PCM文件
        waveformView.setPCMLoader(loader)
      }
    }.launchIn(uiScope)

    //波形进度的更新
    viewModel.currentPositionSeconds.onEach {
      //处理波形图的更新
      if (it >= 0) {
        waveformView.updatePosition(it)
      }
    }.launchIn(uiScope)

    waveformView.setOnCustomClickListener {
      viewModel.togglePlayPause()
    }
    handleDrag()
  }

  private fun handleDrag() {
    //拖动波形图的逻辑
    waveformView.setOnSeekListener(object : OnSeekListener {
      var isPlayWhenStart = false
      override fun onSeekStart() {
        viewModel.getPlayer() ?: return
        isPlayWhenStart = viewModel.isUiPlaying.value
        viewModel.pause()
      }

      override fun onSeeking(position: Float) {
        viewModel.updatePosition(position)
      }

      override fun onSeekEnd(position: Float) {
        val player = viewModel.getPlayer() ?: return
        //viewModel.updateAbSentence(position)
        player.seekTo((position * 1000).toLong())
        if (isPlayWhenStart) {
          viewModel.play()
        }
      }
    })

    //拖动AB边界的逻辑
    waveformView.setOnABChangeListener(object : ScrollWaveformView.OnABChangeListener{
      var isPlayWhenStart = false
      override fun onABDragStart(dragAbResult: ABHitResult?) {
        viewModel.getPlayer() ?: return
        isPlayWhenStart = viewModel.isUiPlaying.value
        if (isPlayWhenStart) {
          viewModel.pause()
        }
      }

      override fun onABDragging(dragAbResult: ABHitResult?) {
      }

      override fun onABDragEnd(dragAbResult: ABHitResult?) {
        viewModel.getPlayer() ?: return
        if (isPlayWhenStart) {
          viewModel.play()
        }
        viewModel.saveSentenceData()
      }
    })
  }
}