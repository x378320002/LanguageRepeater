package com.language.repeater.playvideo

import androidx.media3.common.util.UnstableApi
import com.language.repeater.foundation.BaseComponent

/**
 * Date: 2025-11-14
 * Time: 16:21
 * Description: 耳机播控处理逻辑
 */
class CutOffSentenceComponent: BaseComponent<PlayVideoFragment>() {
  companion object {
    const val TAG = PlayVideoFragment.TAG
  }

  @UnstableApi
  override fun onCreateView() {
    super.onCreateView()

    fragment.binding.cutOffSentence.setOnClickListener {

    }
  }
}