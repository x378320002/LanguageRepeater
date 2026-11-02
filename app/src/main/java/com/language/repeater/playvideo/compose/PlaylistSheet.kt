package com.language.repeater.playvideo.compose

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.language.repeater.R
import com.language.repeater.db.VideoEntity
import com.language.repeater.playbacklist.PlaybackMode
import com.language.repeater.utils.TimeFormatUtil.formatDuration

/**
 * Room PlaybackList 的 Compose 列表，所有交互都使用稳定 mediaId。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSheet(
  playlist: List<VideoEntity>,
  currentMediaId: Long?,
  isPlaying: Boolean,
  playbackMode: PlaybackMode,
  onPlaybackModeClick: () -> Unit,
  onItemClick: (Long) -> Unit,
  onMoveToFirst: (Long) -> Unit,
  onDelete: (Long) -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState()
  val screenHeight = LocalWindowInfo.current.containerDpSize.height
  val sheetHeight = screenHeight * 0.6f

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 15.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = stringResource(R.string.current_playlist_count, playlist.size),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.weight(1f))
      IconButton(onClick = onPlaybackModeClick) {
        val iconRes = when (playbackMode) {
          PlaybackMode.Sequential -> R.drawable.ic_repeat_off
          PlaybackMode.RepeatAll -> R.drawable.ic_repeat_all
          PlaybackMode.RepeatOne -> R.drawable.ic_repeat_one
        }
        Icon(
          modifier = Modifier.size(20.dp),
          painter = painterResource(iconRes),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    LazyColumn(modifier = Modifier.height(sheetHeight)) {
      items(
        items = playlist,
        key = { item -> item.id }
      ) { item ->
        val mediaId = item.id
        PlaylistItemRow(
          item = item,
          isCurrent = mediaId == currentMediaId,
          isPlaying = isPlaying && mediaId == currentMediaId,
          onClick = { onItemClick(mediaId) },
          onMoveToFirst = { onMoveToFirst(mediaId) },
          onDelete = { onDelete(mediaId) }
        )
      }
    }
  }
}

@Composable
private fun PlaylistItemRow(
  item: VideoEntity,
  isCurrent: Boolean,
  isPlaying: Boolean,
  onClick: () -> Unit,
  onMoveToFirst: () -> Unit,
  onDelete: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(60.dp)
      .drawBehind {
        if (isCurrent) {
          drawRoundRect(Color.Gray.copy(alpha = 0.1f))
        }
      }
      .padding(horizontal = 10.dp)
      .clickable(
        onClick = onClick
      ),
    verticalAlignment = Alignment.CenterVertically
  ) {
    CoverRegion(item, isCurrent, isPlaying)

    Column(
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 10.dp),
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = item.name,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis
      )
      Spacer(Modifier.height(3.dp))
      Text(
        text = formatDuration(item.durationMs),
        style = MaterialTheme.typography.labelSmall,
        color = LocalContentColor.current.copy(alpha = 0.6f)
      )
    }

    MoreBtn(onMoveToFirst, onDelete)
  }
}

@Composable
private fun CoverRegion(
  item: VideoEntity,
  isCurrent: Boolean,
  isPlaying: Boolean,
) {
  Box(
    modifier = Modifier
      .size(42.dp)
      .clip(MaterialTheme.shapes.small)
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    contentAlignment = Alignment.Center
  ) {
    // 音频没有封面，垫一个音符图标；视频在封面上层加载缩略图
    if (!item.isVideo) {
      Icon(
        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_music_outline),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.primary
      )
    }

    AsyncImage(
      model = item.uri,
      modifier = Modifier.fillMaxSize(),
      contentScale = ContentScale.Crop,
      contentDescription = null
    )

    if (isCurrent) {
      Icon(
        painter = painterResource(
          if (isPlaying) {
            com.composables.icons.tabler.filled.R.drawable.tabler_ic_player_pause_filled
          } else {
            com.composables.icons.tabler.filled.R.drawable.tabler_ic_player_play_filled
          }
        ),
        contentDescription = null,
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(start = 2.dp, bottom = 2.dp)
          .size(16.dp)
          .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
          .padding(3.dp),
        tint = Color.White
      )
    }
  }
}

@Composable
private fun MoreBtn(
  onMoveToFirst: () -> Unit,
  onDelete: () -> Unit
) {
  var showPop by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = {
      showPop = true
    }) {
      Icon(
        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_dots_vertical_outline),
        contentDescription = stringResource(R.string.move_to_top),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    DropdownMenu(
      expanded = showPop,
      onDismissRequest = {
        showPop = false
      }
    ) {
      DropdownMenuItem(
        text = {
          Text(
            text = stringResource(R.string.playlist_item_move_to_first),
            style = MaterialTheme.typography.bodyMedium,
          )
        },
        leadingIcon = {
          Icon(
            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_arrow_narrow_up_dashed_outline),
            contentDescription = stringResource(R.string.move_to_top)
          )
        },
        onClick = onMoveToFirst
      )

      DropdownMenuItem(
        text = {
          Text(
            text = stringResource(R.string.history_item_delete),
            style = MaterialTheme.typography.bodyMedium,
          )
        },
        leadingIcon = {
          Icon(
            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_trash_outline),
            contentDescription = stringResource(R.string.history_item_delete)
          )
        },
        onClick = onDelete
      )
    }
  }
}
