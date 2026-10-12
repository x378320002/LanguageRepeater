package com.language.repeater.playvideo.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.language.repeater.db.VideoEntity

/**
 * 历史记录 BottomSheet — 纯 Compose 实现
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
  historyList: List<VideoEntity>,
  onItemClick: (VideoEntity) -> Unit,
  onDelete: (VideoEntity) -> Unit,
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
        .padding(horizontal = 15.dp),
    ) {
      Text(
        text = "历史记录",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }

    // 历史记录列表
    LazyColumn(
      modifier = Modifier.height(sheetHeight),
    ) {
      items(historyList, key = { it.id }) { item ->
        HistoryItemRow(
          item = item,
          onClick = { onItemClick(item) },
          onDelete = { onDelete(item) },
        )
      }
    }
  }
}

@Composable
private fun HistoryItemRow(
  item: VideoEntity,
  onClick: () -> Unit,
  onDelete: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(60.dp)
      .padding(horizontal = 15.dp)
      .clickable(
        onClick = onClick
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AsyncImage(
      model = item.uri,
      modifier = Modifier.size(44.dp).background(Color.Black, shape = MaterialTheme.shapes.small),
      contentDescription = null
    )

    Text(
      text = item.name,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f).padding(start = 8.dp),
    )

    // 删除按钮
    IconButton(
      onClick = onDelete
    ) {
      Icon(
        modifier = Modifier.size(22.dp),
        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_trash_outline),
        contentDescription = "删除",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
