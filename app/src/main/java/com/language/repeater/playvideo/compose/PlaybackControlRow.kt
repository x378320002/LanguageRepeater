package com.language.repeater.playvideo.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(80.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    // 上一句
    IconButton(
      onClick = onSeekPrevSentence,
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight(),
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_pre_sentence),
        contentDescription = "上一句",
        tint = MaterialTheme.colorScheme.onBackground,
      )
    }

    // 回到句首
    IconButton(
      onClick = onBackSentenceHead,
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight(),
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_go_sentence_head_1),
        contentDescription = "回到当前句开头",
        tint = MaterialTheme.colorScheme.onBackground,
      )
    }

    // 播放/暂停大按钮
    val playPauseIconRes = if (isPlaying) {
      R.drawable.ic_pause
    } else {
      R.drawable.ic_play
    }
    IconButton(
      onClick = onTogglePlayPause,
      modifier = Modifier
        .size(80.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant),
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
        .weight(1f)
        .fillMaxHeight(),
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_next_sentence),
        contentDescription = "下一句",
        tint = MaterialTheme.colorScheme.onBackground,
      )
    }

    // 播放列表
    IconButton(
      onClick = onOpenPlaylist,
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight(),
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_list),
        contentDescription = "播放列表",
        tint = MaterialTheme.colorScheme.onBackground,
      )
    }
  }
}
