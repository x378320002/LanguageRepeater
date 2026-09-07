package com.language.repeater.playvideo.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.language.repeater.pcm.PCMSegmentLoader
import com.language.repeater.sentence.Sentence
import com.language.repeater.widgets.ScrollWaveformView

/**
 * 波形图区 — 包装旧 ScrollWaveformView
 *
 * 阶段 2 先用 AndroidView 包装，阶段 7 再用 Compose Canvas 重写
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
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .height(110.dp),
    shape = MaterialTheme.shapes.small,
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
