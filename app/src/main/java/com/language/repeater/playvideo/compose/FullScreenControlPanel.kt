package com.language.repeater.playvideo.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.language.repeater.playvideo.PlayerViewModel
import com.language.repeater.theme.VideoOverlayTheme

/**
 * 全屏模式下叠在视频画面上的控制卡片。
 *
 * 黑色半透明底 + 浅色描边，与背后的视频画面明确分离。
 * 卡片内自上而下为：波形区、句子编辑栏、副操作栏、播放控制栏、播放进度条。
 * 全屏可用宽度远大于内容所需，宽度封顶后居中，避免按钮被拉得过散。
 */
@Composable
fun FullScreenControlPanel(
  viewModel: PlayerViewModel,
  modifier: Modifier = Modifier,
  isVerticalFull: Boolean = false,
  onOpenPlaylist: () -> Unit,
) {
  VideoOverlayTheme {
    Card(
      modifier = modifier,
      shape = MaterialTheme.shapes.medium,
      colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
      border = if (isVerticalFull) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
    ) {
      Column {
        Spacer(Modifier.height(4.dp))
        PlayerWaveformSection(
          viewModel = viewModel,
          height = WaveformHeight,
          colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        )

        PlayerEditSection(viewModel = viewModel)

        PlayerSubActionsSection(
          viewModel = viewModel,
          fullScreenEntered = true,
        )

        PlayerControlSection(
          viewModel = viewModel,
          onOpenPlaylist = onOpenPlaylist,
          isFull = true
        )

        PlayerProgressSection(
          viewModel = viewModel,
          modifier = Modifier.padding(horizontal = 12.dp),
        )
      }
    }
  }
}

private val WaveformHeight = 72.dp
