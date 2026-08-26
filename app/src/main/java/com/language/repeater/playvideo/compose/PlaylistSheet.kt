package com.language.repeater.playvideo.compose

import android.R.attr.description
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.language.repeater.R
import com.language.repeater.playbacklist.PlaybackListItem
import com.language.repeater.playbacklist.PlaybackMode

/**
 * Room PlaybackList 的 Compose 列表，所有交互都使用稳定 mediaId。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSheet(
  playlist: List<PlaybackListItem>,
  currentMediaId: String?,
  isPlaying: Boolean,
  playbackMode: PlaybackMode,
  onPlaybackModeClick: () -> Unit,
  onItemClick: (String) -> Unit,
  onMoveToFirst: (String) -> Unit,
  onDelete: (String) -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState()
  val screenHeight = LocalWindowInfo.current.containerDpSize.height
  val sheetHeight = screenHeight * 0.6f

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 15.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "当前列表 (${playlist.size})",
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
        key = { item -> item.mediaId }
      ) { item ->
        val mediaId = item.mediaId
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
  item: PlaybackListItem,
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
      .clickable(
        onClick = onClick
      ),
    verticalAlignment = Alignment.CenterVertically
  ) {
    AsyncImage(
      model = item.uri,
      modifier = Modifier.size(44.dp).background(Color.Black, shape = MaterialTheme.shapes.small),
      contentDescription = null
    )
    if (isCurrent) {
      Icon(
        painter = painterResource(
          if (isPlaying) {
            R.drawable.ic_pause
          } else {
            R.drawable.ic_play
          }
        ),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.primary
      )
    } else {
      Spacer(modifier = Modifier.size(20.dp))
    }

    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = item.title,
      style = MaterialTheme.typography.bodyMedium,
      color = if (isCurrent) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.onSurface
      },
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f)
    )

    IconButton(onClick = onMoveToFirst) {
      Icon(
        painter = painterResource(R.drawable.ic_more_vert),
        contentDescription = "移到顶部",
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    IconButton(onClick = onDelete) {
      Icon(
        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_trash_outline),
        contentDescription = "删除",
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
