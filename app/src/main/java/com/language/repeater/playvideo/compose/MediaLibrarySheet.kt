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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.language.repeater.R
import com.language.repeater.utils.MediaStoreItem
import com.language.repeater.utils.MediaStoreQuery
import com.language.repeater.utils.TimeFormatUtil.formatDuration
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.launch

/**
 * 打开媒体库的入口：已有权限直接执行 [onGranted]，否则先申请。
 *
 * 权限申请被拒时显示媒体访问权限提示；后续点击仍按当前权限状态处理。
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
      ToastUtil.toast(R.string.media_access_permission_required)
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
  val selectedItems = remember { mutableStateOf(emptySet<MediaStoreItem>()) }
  // 当前展示的文件夹，null 表示全部媒体
  var selectedFolder by remember { mutableStateOf<String?>(null) }
  var showFolderMenu by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    launch {
      mediaList = MediaStoreQuery.loadAll(context)
      loading = false
    }
  }

  // 文件夹按媒体条目数倒序排列，条目最多的文件夹显示在菜单顶部
  val folderCounts = remember(mediaList) {
    mediaList
      .map { item -> item.folderName }
      .filter { folder -> folder.isNotBlank() }
      .groupingBy { folder -> folder }
      .eachCount()
      .toList()
      .sortedByDescending { (_, count) -> count }
  }
  // 当前展示的列表：全部媒体或指定文件夹内的条目
  val displayedList = remember(mediaList, selectedFolder) {
    if (selectedFolder == null) {
      mediaList
    } else {
      mediaList.filter { item -> item.folderName == selectedFolder }
    }
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
      // 标题即可点击的文件夹切换器：默认显示全部媒体，点击后从菜单选择文件夹
      Box(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable { showFolderMenu = true }
            .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = if (selectedFolder == null) {
              stringResource(R.string.media_library_all_count, mediaList.size)
            } else {
              stringResource(R.string.media_folder_count, selectedFolder.orEmpty(), displayedList.size)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis
          )
          Icon(
            painter = painterResource(
              com.composables.icons.tabler.outline.R.drawable.tabler_ic_chevron_down_outline
            ),
            contentDescription = stringResource(R.string.switch_media_folder),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        DropdownMenu(
          containerColor = MaterialTheme.colorScheme.surface,
          expanded = showFolderMenu,
          onDismissRequest = { showFolderMenu = false }
        ) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.media_library_all_count, mediaList.size)) },
            onClick = {
              selectedFolder = null
              showFolderMenu = false
            }
          )
          folderCounts.forEach { (folder, count) ->
            DropdownMenuItem(
              text = { Text(stringResource(R.string.media_folder_count, folder, count)) },
              onClick = {
                selectedFolder = folder
                showFolderMenu = false
              }
            )
          }
        }
      }
      //Spacer(modifier = Modifier.weight(1f))
      TextButton(
        enabled = selectedItems.value.isNotEmpty(),
        onClick = {
          // 选中项跨文件夹保留，一次添加全部已选媒体
          onConfirm(selectedItems.value.toList())
        }
      ) {
        Text(stringResource(R.string.media_library_add_count, selectedItems.value.size))
      }
    }

    Box(modifier = Modifier.height(sheetHeight)) {
      when {
        loading -> {
          CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        mediaList.isEmpty() -> {
          Text(
            text = stringResource(R.string.media_library_empty),
            color = LocalContentColor.current.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.Center)
          )
        }

        else -> {
          LazyColumn {
            items(
              items = displayedList,
              key = { item -> item.uri.toString() }
            ) { item ->
              val uriKey = item.uri.toString()
              MediaLibraryRow(
                item = item,
                checked = item in selectedItems.value,
                onToggle = {
                  selectedItems.value = if (item in selectedItems.value) {
                    selectedItems.value - item
                  } else {
                    selectedItems.value + item
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
