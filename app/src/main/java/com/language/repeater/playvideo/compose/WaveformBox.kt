package com.language.repeater.playvideo.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.language.repeater.pcm.PCMSegmentLoader
import com.language.repeater.sentence.Sentence
import com.language.repeater.widgets.ScrollWaveformView

/** 将当前 Compose 主题映射到波形配色，颜色透明度包含在 ARGB 值中。 */
@Composable
fun rememberWaveformColors(): ScrollWaveformView.WaveformColors {
  val colorScheme = MaterialTheme.colorScheme
  return remember(colorScheme) {
    ScrollWaveformView.WaveformColors(
      outline = colorScheme.primary.copy(alpha = 0.75f).toArgb(),
      played = colorScheme.onSurface.copy(alpha = 0.2f).toArgb(),
      unplayed = colorScheme.primary.copy(alpha = 0.5f).toArgb(),
      progressIndicator = colorScheme.primary.toArgb(),
      sentenceBoundary = colorScheme.onSurfaceVariant.copy(alpha = 0.65f).toArgb(),
      currentSentenceBoundary = colorScheme.primary.toArgb(),
      centerLine = colorScheme.onSurface.copy(alpha = 0.15f).toArgb(),
      timeText = colorScheme.onSurfaceVariant.toArgb(),
      abBackground = colorScheme.primary.toArgb(),
      abText = colorScheme.onPrimary.toArgb(),
      editHandle = colorScheme.primary.toArgb(),
    )
  }
}

/**
 * 波形图区通过 AndroidView 绑定数据、交互与主题配色。
 */
@Composable
fun WaveformBox(
  sentences: List<Sentence>,
  curAbSentence: Sentence?,
  editMode: Boolean,
  repeatable: Boolean,
  pcmLoader: PCMSegmentLoader?,
  currentPositionSec: Float,
  onCustomClick: () -> Unit,
  onSeekBegin: () -> Boolean,
  onSeeking: (Float) -> Unit,
  onSeekEnd: (position: Float, wasPlaying: Boolean) -> Unit,
  onAbDragBegin: () -> Boolean,
  onAbDragEnd: (wasPlaying: Boolean) -> Unit,
  modifier: Modifier = Modifier,
  height: Dp = 100.dp,
  colors: CardColors = CardDefaults.cardColors(),
  waveformColors: ScrollWaveformView.WaveformColors = rememberWaveformColors()
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .height(height),
    shape = MaterialTheme.shapes.small,
    colors = colors,
  ) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { context ->
        ScrollWaveformView(context).apply {
          setOnSeekListener(object : ScrollWaveformView.OnSeekListener {
            private var wasPlaying = false

            override fun onSeekStart() {
              wasPlaying = onSeekBegin()
            }

            override fun onSeeking(position: Float) {
              onSeeking(position)
            }

            override fun onSeekEnd(position: Float) {
              onSeekEnd(position, wasPlaying)
            }
          })

          setOnABChangeListener(object : ScrollWaveformView.OnABChangeListener {
            private var wasPlaying = false

            override fun onABDragStart(dragAbResult: ScrollWaveformView.ABHitResult?) {
              wasPlaying = onAbDragBegin()
            }

            override fun onABDragging(dragAbResult: ScrollWaveformView.ABHitResult?) {}

            override fun onABDragEnd(dragAbResult: ScrollWaveformView.ABHitResult?) {
              onAbDragEnd(wasPlaying)
            }
          })

          setOnCustomClickListener { onCustomClick() }
        }
      },
      update = { view ->
        view.setWaveformColors(waveformColors)
        view.setPCMLoader(pcmLoader)
        view.setSentenceData(sentences)
        view.curABSentence = curAbSentence
        view.isEditMode = editMode
        view.isRepeated = repeatable
        view.updatePosition(currentPositionSec)
        view.invalidate() // 触发重绘，显示 AB 句子和编辑模式
      },
      onRelease = { view ->
        view.cleanup()
      },
    )
  }
}
