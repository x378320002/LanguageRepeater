package com.language.repeater.playvideo.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.ui.components.BottomSheet
import com.language.repeater.R
import com.language.repeater.player.PlaylistItem

/**
 * 播放列表 BottomSheet — 纯 Compose 实现，只依赖 PlayerEngine 抽象层
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSheet(
  playlist: List<PlaylistItem>,
  currentIndex: Int,
  onItemClick: (Int) -> Unit,
  onMoveToFirst: (Int) -> Unit,
  onDelete: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState()
  val screenHeight = LocalWindowInfo.current.containerDpSize.height
  val sheetHeight = screenHeight * 0.6f

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    // 标题栏
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "当前列表 (${playlist.size})",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.weight(1f))
      // 循环模式按钮（后续实现）
      Icon(
        painter = painterResource(R.drawable.ic_repeat_one),
        contentDescription = "循环模式",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // 播放列表
    LazyColumn(
      modifier = Modifier.height(sheetHeight)
    ) {
      itemsIndexed(playlist, key = { _, item ->
        item.id
      }) { index, item ->
        PlaylistItemRow(
          item = item,
          isCurrent = index == currentIndex,
          onClick = { onItemClick(index) },
          onMoveToFirst = { onMoveToFirst(index) },
          onDelete = { onDelete(index) },
        )
      }
    }
  }
}

@Composable
private fun PlaylistItemRow(
  item: PlaylistItem,
  isCurrent: Boolean,
  onClick: () -> Unit,
  onMoveToFirst: () -> Unit,
  onDelete: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
      )
      .padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // 播放状态指示
    if (isCurrent) {
      Icon(
        painter = painterResource(
          if (item.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        ),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.primary,
      )
    } else {
      Spacer(modifier = Modifier.size(20.dp))
    }

    Spacer(modifier = Modifier.width(12.dp))

    // 标题
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
      modifier = Modifier.weight(1f),
    )

    // 更多操作
    IconButton(
      onClick = { /* TODO: 弹出菜单 */ },
      modifier = Modifier.size(32.dp),
    ) {
      Icon(
        painter = painterResource(R.drawable.ic_more_vert),
        contentDescription = "更多",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
