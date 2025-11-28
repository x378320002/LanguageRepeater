package com.language.repeater.playvideo.components

import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.launch

/**
 * Date: 2025-11-14
 * Time: 16:21
 * Description: 耳机播控处理逻辑
 */
class PlayUIActComponent: BaseComponent<PlayVideoFragment>() {
  companion object {
    const val TAG = PlayVideoFragment.Companion.TAG
  }

  @UnstableApi
  override fun onCreateView() {
    super.onCreateView()
    val playComponent = fragment.playComponent

    playComponent.setRepeat(fragment.binding.voiceRepeatSwitch.isChecked)
    fragment.binding.voiceRepeatSwitch.setOnCheckedChangeListener { _, checked->
      playComponent.setRepeat(fragment.binding.voiceRepeatSwitch.isChecked)
    }

    fragment.binding.voiceNext.setOnClickListener {
      playComponent.seekToNext()
    }

    fragment.binding.voicePrevious.setOnClickListener {
      playComponent.seekToPrevious()
    }

    fragment.binding.reloadSentence.setOnClickListener {
      fragment.viewLifecycleOwner.lifecycleScope.launch {
        fragment.showLoading()
        fragment.viewModel.reloadSentencesAuto()
        fragment.hideLoading()
      }
    }

    fragment.binding.saveSentence.setOnClickListener {
      fragment.viewLifecycleOwner.lifecycleScope.launch {
        fragment.showLoading()
        fragment.viewModel.saveSentenceDataToFile()
        fragment.hideLoading()
      }
    }

    fragment.binding.splitSentence.setOnClickListener {
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

    fragment.binding.deleteSentence.setOnClickListener {
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