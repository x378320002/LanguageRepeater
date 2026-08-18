package com.language.repeater.playvideo.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView

/**
 * 视频渲染区 — Android 平台实现
 *
 * 注意：这里 import media3 是平台差异点，属于 Android 特定实现。
 * 未来 KMP 时，这个文件会移到 androidMain，iOS 会有自己的实现。
 */
@Composable
fun VideoPlayerBox(
  modifier: Modifier = Modifier,
  player: Player?,
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(16f / 9f),
    shape = MaterialTheme.shapes.small,
    colors = CardDefaults.cardColors(containerColor = Color.Black),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
          PlayerView(context).apply {
            useController = false
          }
        },
        update = { playerView ->
          if (playerView.player !== player) {
            playerView.player = player
          }
        },
        onRelease = { playerView ->
          playerView.player = null
        },
      )
    }
  }
}
