@file:OptIn(ExperimentalMaterial3Api::class)

package com.language.repeater.playvideo.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp

/**
 * 自定义进度条 — 匹配旧 View 的 SeekBar 样式
 * 2dp 高度、圆角、灰色背景 + 白色进度、无滑块
 */
@Composable
fun ProgressSeekBar(
  value: Float,
  onSeek: (Float) -> Unit,
) {
  Slider(
    value = value,
    onValueChange = onSeek,
    modifier = Modifier
      .fillMaxWidth()
      .height(SliderH)
      .padding(horizontal = 15.dp),
    track = { state ->
      val bgColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
      val onBgColor = MaterialTheme.colorScheme.onBackground
      Canvas(
        modifier = Modifier
          .fillMaxWidth()
          .height(SliderTrackHeight)
      ) {
        drawRoundRect(
          color = bgColor,
          cornerRadius = CornerRadius(4.dp.toPx()),
        )


        val w = this.size.width * state.value
        drawRoundRect(
          color = onBgColor,
          cornerRadius = CornerRadius(2.dp.toPx()),
          size = this.size.copy(width = w)
        )
      }
    }, thumb = {
      Box(
        Modifier
          .height(SliderH)
          .width(SliderThumbSize),
        contentAlignment = Alignment.Center
      ) {
        Box(
          Modifier
            .size(SliderThumbSize)
            .background(MaterialTheme.colorScheme.onBackground, CircleShape),
        )
      }
    })
}

private val SliderThumbSize = 6.dp
private val SliderTrackHeight = 2.dp
private val SliderH = 20.dp

