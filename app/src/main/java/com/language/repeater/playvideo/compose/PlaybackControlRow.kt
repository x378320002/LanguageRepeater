package com.language.repeater.playvideo.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.language.repeater.R

/**
 * 播放控制栏 — 只依赖 isPlaying 状态，不依赖任何播放器 SDK
 */
@Composable
fun PlaybackControlRow(
  isPlaying: Boolean,
  onSeekPrevSentence: () -> Unit,
  onBackSentenceHead: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onSeekNextSentence: () -> Unit,
  onOpenPlaylist: () -> Unit,
  modifier: Modifier = Modifier,
  isFull: Boolean = false
) {

  val iconSize = 50.dp
  val centerIconSize = 70.dp

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(80.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceEvenly,
  ) {
    IconButton(
      onClick = onBackSentenceHead,
      modifier = Modifier
        .size(iconSize),
    ) {
      Icon(
        painter = painterResource(com.composables.icons.tabler.filled.R.drawable.tabler_ic_transition_left_filled),
        contentDescription = "回句首",
        tint = MaterialTheme.colorScheme.onBackground,
      )
    }

    IconButton(
      onClick = onSeekPrevSentence,
      modifier = Modifier
        .size(iconSize),
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_go_sentence_head_1),
        contentDescription = "上一句",
        tint = MaterialTheme.colorScheme.onBackground,
      )
    }

    // 播放/暂停大按钮
    val playPauseIconRes = if (isPlaying) {
      com.composables.icons.tabler.filled.R.drawable.tabler_ic_player_stop_filled
    } else {
      com.composables.icons.tabler.filled.R.drawable.tabler_ic_player_play_filled
    }
    IconButton(
      onClick = onTogglePlayPause,
      modifier = Modifier
        .size(centerIconSize)
        .background(
          color = if (isFull) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          } else {
            MaterialTheme.colorScheme.surfaceVariant
          },
          shape = CircleShape
        ),
    ) {
      Icon(
        painter = painterResource(id = playPauseIconRes),
        contentDescription = if (isPlaying) "暂停" else "播放",
        modifier = Modifier.size(40.dp),
        tint = MaterialTheme.colorScheme.onBackground,
      )
    }

    // 下一句
    IconButton(
      onClick = onSeekNextSentence,
      modifier = Modifier
        .size(iconSize),
    ) {
      Icon(
        painter = painterResource(com.composables.icons.tabler.filled.R.drawable.tabler_ic_player_play_filled),
        contentDescription = "下一句",
        tint = MaterialTheme.colorScheme.onBackground,
      )
    }

    // 播放列表
    IconButton(
      onClick = onOpenPlaylist,
      modifier = Modifier
        .size(iconSize),
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_list),
        contentDescription = "播放列表",
        tint = MaterialTheme.colorScheme.onBackground,
      )
    }
  }
}
