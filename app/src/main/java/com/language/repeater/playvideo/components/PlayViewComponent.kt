package com.language.repeater.playvideo.components

import android.graphics.Color
import android.widget.TextView
import androidx.annotation.Dimension
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.playvideo.PlayVideoFragment
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Date: 2025-12-01
 * Time: 10:36
 * Description:
 */
class PlayViewComponent: BaseComponent<PlayVideoFragment>() {
  override fun onCreateView() {
    super.onCreateView()

    fragment.viewModel.playerState.onEach {
      fragment.binding.exoVideoView.player = it
    }.launchIn(uiScope)

    fragment.viewModel.currentMediaItem.onEach {
      val title = it?.mediaMetadata?.title ?: "UnKnown"
      fragment.binding.tvTitle.text = title
    }.launchIn(uiScope)

    setSubtitleStyle()
  }

  @OptIn(UnstableApi::class)
  private fun setSubtitleStyle() {
    val subtitleView = fragment.binding.exoVideoView.subtitleView
    if (subtitleView != null) {
      // 2. 设置样式
      // 参数顺序：文字颜色, 背景色, 窗口色, 边缘类型, 边缘颜色, 字体(Typeface)
      val style = CaptionStyleCompat(
        Color.WHITE,             // 文字改成亮黄色
        Color.TRANSPARENT, // 背景半透明黑 (0x00000000 为全透明)
        Color.TRANSPARENT,        // 窗口背景透明
        CaptionStyleCompat.EDGE_TYPE_OUTLINE, // 描边 (OUTLINE, DROP_SHADOW, NONE)
        Color.BLACK,              // 描边黑色
        null                      // 字体 (Typeface)，如果要自定义字体传 Typeface 对象
      )

      subtitleView.setStyle(style)
      // 3. 设置字体大小 (单位：像素) -> 建议转换成 sp
      // SubtitleView.VIEW_TYPE_WEB (默认) 支持分数大小，VIEW_TYPE_CANVAS 支持固定大小
      // 这里设置占视频高度的 5% (默认是 0.0533)
      subtitleView.setFractionalTextSize(0.09f)
//      subtitleView.setFixedTextSize(Dimension.DP,20f)

      // 或者强制固定大小 (不推荐，全屏时会显得太小)
      // subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)

      // 4. 设置位置 (底部距离)
      // 0.9f 表示在屏幕 90% 的位置 (靠近底部)
      subtitleView.setBottomPaddingFraction(0.05f)
    }
  }
}