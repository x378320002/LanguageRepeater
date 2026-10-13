package com.language.repeater.playvideo.compose

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.language.repeater.BuildConfig
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
  onRemovePermission: (String) -> Unit,
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
          onDelete = { onDelete(mediaId) },
          onRemovePermission = { onRemovePermission(mediaId) }
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
  onDelete: () -> Unit,
  onRemovePermission: () -> Unit
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

    Text(
      text = item.title,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .weight(1f)
        .padding(start = 8.dp)
    )

    MoreBtn(onMoveToFirst, onDelete, onRemovePermission)
  }
}

@Composable
private fun CoverRegion(
  item: PlaybackListItem,
  isCurrent: Boolean,
  isPlaying: Boolean,
) {
  Box(
    modifier = Modifier
      .size(42.dp)
      .clip(MaterialTheme.shapes.small)
      .background(Color.Black)
  ) {
    AsyncImage(
      model = item.uri,
      modifier = Modifier.fillMaxSize(),
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
          .padding(start = 4.dp, top = 4.dp)
          .size(12.dp)
          .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun MoreBtn(
  onMoveToFirst: () -> Unit,
  onDelete: () -> Unit,
  onRemovePermission: ()->Unit
) {
  var showPop by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = {
      showPop = true
    }) {
      Icon(
        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_dots_vertical_outline),
        contentDescription = "移到顶部",
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
            text = "移到首位",
            style = MaterialTheme.typography.bodyMedium,
          )
        },
        leadingIcon = {
          Icon(
            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_arrow_narrow_up_dashed_outline),
            contentDescription = "移到顶部"
          )
        },
        onClick = onMoveToFirst
      )

      DropdownMenuItem(
        text = {
          Text(
            text = "删除",
            style = MaterialTheme.typography.bodyMedium,
          )
        },
        leadingIcon = {
          Icon(
            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_trash_outline),
            contentDescription = "删除"
          )
        },
        onClick = onDelete
      )

      // 调试入口：构造「授权已失效」的条目，用来验证失效后的提示与恢复流程
      if (BuildConfig.DEBUG) {
        DropdownMenuItem(
          text = {
            Text(
              text = "释放授权（测试）",
              style = MaterialTheme.typography.bodyMedium,
            )
          },
          leadingIcon = {
            Icon(
              painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_lock_off_outline),
              contentDescription = "释放授权"
            )
          },
          onClick = onRemovePermission
        )
      }
    }
  }
}
