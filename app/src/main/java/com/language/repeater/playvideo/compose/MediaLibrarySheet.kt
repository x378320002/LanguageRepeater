package com.language.repeater.playvideo.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ContentScale.Companion
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.language.repeater.utils.MediaStoreItem
import com.language.repeater.utils.MediaStoreQuery
import com.language.repeater.utils.TimeFormatUtil.formatDuration
import com.language.repeater.utils.ToastUtil

/**
 * 打开媒体库的入口：已有权限直接执行 [onGranted]，否则先申请。
 *
 * 权限被拒后不再重复弹系统对话框，只提示用户，由用户自行去设置页开启。
 */
@Composable
fun rememberMediaLibraryOpener(onGranted: () -> Unit): () -> Unit {
  val context = LocalContext.current
  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { result ->
    if (result.values.all { granted -> granted }) {
      onGranted()
    } else {
      ToastUtil.toast("需要媒体访问权限才能读取设备中的音视频")
    }
  }

  return {
    if (MediaStoreQuery.hasPermission(context)) {
      onGranted()
    } else {
      permissionLauncher.launch(MediaStoreQuery.REQUIRED_PERMISSIONS)
    }
  }
}

/**
 * 设备媒体库选择弹窗。
 *
 * 列表直接来自 MediaStore，条目的 Uri 靠 app 的媒体权限访问，不占用持久化 Uri 授权配额。
 * 调用方需保证打开前已拿到 [MediaStoreQuery.REQUIRED_PERMISSIONS]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLibrarySheet(
  onConfirm: (List<MediaStoreItem>) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState()
  val sheetHeight = LocalWindowInfo.current.containerDpSize.height * 0.6f

  var loading by remember { mutableStateOf(true) }
  var mediaList by remember { mutableStateOf<List<MediaStoreItem>>(emptyList()) }
  val selectedUris = remember { mutableStateOf(emptySet<String>()) }

  LaunchedEffect(Unit) {
    mediaList = MediaStoreQuery.loadAll(context)
    loading = false
  }

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
        text = "媒体库 (${mediaList.size})",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.weight(1f))
      TextButton(
        enabled = selectedUris.value.isNotEmpty(),
        onClick = {
          val selected = mediaList.filter { item ->
            item.uri.toString() in selectedUris.value
          }
          onConfirm(selected)
        }
      ) {
        Text("添加(${selectedUris.value.size})")
      }
    }

    Box(modifier = Modifier.height(sheetHeight)) {
      when {
        loading -> {
          CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        mediaList.isEmpty() -> {
          Text(
            text = "媒体库里没有找到音视频文件",
            color = LocalContentColor.current.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.Center)
          )
        }

        else -> {
          LazyColumn {
            items(
              items = mediaList,
              key = { item -> item.uri.toString() }
            ) { item ->
              val uriKey = item.uri.toString()
              MediaLibraryRow(
                item = item,
                checked = uriKey in selectedUris.value,
                onToggle = {
                  selectedUris.value = if (uriKey in selectedUris.value) {
                    selectedUris.value - uriKey
                  } else {
                    selectedUris.value + uriKey
                  }
                }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MediaLibraryRow(
  item: MediaStoreItem,
  checked: Boolean,
  onToggle: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(60.dp)
      .clickable(onClick = onToggle)
      .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    MediaCover(item)

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

    Checkbox(
      checked = checked,
      onCheckedChange = { onToggle() }
    )
  }
}

@Composable
private fun MediaCover(item: MediaStoreItem) {
  Box(
    modifier = Modifier
      .size(42.dp)
      .clip(MaterialTheme.shapes.small)
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      painter = painterResource(
        if (item.isVideo) {
          com.composables.icons.tabler.outline.R.drawable.tabler_ic_video_outline
        } else {
          com.composables.icons.tabler.outline.R.drawable.tabler_ic_music_outline
        }
      ),
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = MaterialTheme.colorScheme.primary
    )

    if (item.isVideo) {
      AsyncImage(
        model = item,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}
