package com.language.repeater.test

import android.content.Context
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.max
import kotlin.text.Typography.ellipsis

/**
 * Date: 2025-12-01
 * Time: 17:08
 * Description:
 */
// 自定义 TextView 实现多行开头省略
class StartEllipsizeTextView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : AppCompatTextView(context, attrs, defStyle) {

  private var isEllipsizing = false
  private var ellipsizedText: String? = null
  private var originalText = ""

  /**
   * 核心拦截：保存原始文本
   */
  override fun setText(text: CharSequence?, type: BufferType?) {
    if (isEllipsizing) {
      super.setText(text, type)
      return
    }

    // 只有非空文本才保存为 originalText
    if (text != null) {
      originalText = text.toString()
    }

    super.setText(text, type)

    val layout = createLayout(originalText, width - paddingLeft - paddingRight)
    if (layout.lineCount <= maxLines) {
      ellipsizedText = null
      return
    }

    // 如果当前已经有宽度了（比如在运行时动态设置文本），立即触发计算
    if (width > 0) {
      ellipsizedText = startEllipsize(
        originalText,
        width - paddingLeft - paddingRight,
        maxLines,
        paint,
        "..."
      )

      if (ellipsizedText != null && ellipsizedText != originalText) {
        isEllipsizing = true
        setText(ellipsizedText)
        isEllipsizing = false
      }
    }
  }

//  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
//    if (isEllipsizing) {
//      super.onLayout(changed, left, top, right, bottom)
//      return
//    }
//
//    val original = text?.toString() ?: ""
//    val maxLines = maxLines
//    if (maxLines <= 0 || ellipsize != TextUtils.TruncateAt.START) {
//      super.onLayout(changed, left, top, right, bottom)
//      return
//    }
//
//    val layout = createLayout(original, width)
//    if (layout.lineCount <= maxLines) {
//      ellipsizedText = null
//      super.onLayout(changed, left, top, right, bottom)
//      return
//    }
//
//    // 需要裁剪
//    val ellipsis = "…"
//    val paint = paint
//    val availableWidth = width - paddingLeft - paddingRight
//
//    ellipsizedText = startEllipsize(
//      original,
//      availableWidth,
//      maxLines,
//      paint,
//      ellipsis
//    )
//
//    if (ellipsizedText != null && ellipsizedText != original) {
//      isEllipsizing = true
//      text = ellipsizedText
//      isEllipsizing = false
//    }
//
//    super.onLayout(changed, left, top, right, bottom)
//  }

  private fun startEllipsize(
    text: String,
    lineWidth: Int,
    maxLines: Int,
    paint: TextPaint,
    ellipsis: String
  ): String {

    var start = 0
    var end = text.length

    // 二分查找最佳截断点
    while (start < end) {
      val mid = (start + end) / 2
      val candidate = ellipsis + text.substring(mid)

      val layout = createLayout(candidate, lineWidth)

      if (layout.lineCount > maxLines) {
        // 说明截断不够，需要截断更多
        start = mid + 1
      } else {
        end = mid
      }
    }

    return ellipsis + text.substring(end)
  }

  private fun createLayout(text: String, width: Int): StaticLayout {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
        .setIncludePad(includeFontPadding)
        .build()
    } else {
      @Suppress("DEPRECATION")
      StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL,
        lineSpacingMultiplier, lineSpacingExtra, includeFontPadding)
    }

//    return StaticLayout(
//      text,
//      paint,
//      max(realWidth, 0),
//      Layout.Alignment.ALIGN_NORMAL,
//      lineSpacingMultiplier,
//      lineSpacingExtra,
//      false
//    )
  }
}
