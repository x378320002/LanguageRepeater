package com.language.repeater.playvideo.compose

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.language.repeater.R
import com.language.repeater.utils.MediaStoreItem
import com.language.repeater.utils.MediaStoreQuery
import com.language.repeater.utils.TimeFormatUtil.formatDuration
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 打开媒体库选择弹窗，媒体访问授权由弹窗内的交互处理。 */
@Composable
fun rememberMediaLibraryOpener(onGranted: () -> Unit): () -> Unit {
  return {
    onGranted()
  }
}

/**
 * 设备媒体库选择弹窗。
 *
 * 列表直接来自 MediaStore，条目的 Uri 靠 app 的媒体权限访问，不占用持久化 Uri 授权配额。
 * 媒体库仅在音频和完整视频访问权限均已授予时加载。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLibrarySheet(
  onConfirm: (List<MediaStoreItem>) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val lifecycle = lifecycleOwner.lifecycle
  val sheetState = rememberModalBottomSheetState()
  val sheetHeight = LocalWindowInfo.current.containerDpSize.height * 0.6f

  var loading by remember { mutableStateOf(true) }
  var hasMediaPermission by remember { mutableStateOf(MediaStoreQuery.hasPermission(context)) }
  var permissionRequestDenied by remember { mutableStateOf(false) }
  var reloadRevision by remember { mutableIntStateOf(0) }
  var lifecycleActive by remember(lifecycleOwner) {
    mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
  }
  var mediaList by remember { mutableStateOf<List<MediaStoreItem>>(emptyList()) }
  val selectedItems = remember { mutableStateOf(emptySet<MediaStoreItem>()) }
  // 当前展示的文件夹，null 表示全部媒体。
  var selectedFolder by remember { mutableStateOf<String?>(null) }
  var showFolderMenu by remember { mutableStateOf(false) }

  val refresh: () -> Unit = {
    hasMediaPermission = MediaStoreQuery.hasPermission(context)
    loading = true
    reloadRevision += 1
  }
  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) {
    // 完整权限以系统状态为准，选定视频授权不满足媒体库加载条件。
    permissionRequestDenied = !MediaStoreQuery.hasPermission(context)
    refresh()
  }
  val settingsLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) {
    permissionRequestDenied = !MediaStoreQuery.hasPermission(context)
    refresh()
  }
  val requestAccess: () -> Unit = {
    permissionLauncher.launch(MediaStoreQuery.REQUIRED_PERMISSIONS)
  }
  val openSettings: () -> Unit = {
    val intent = Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.fromParts("package", context.packageName, null)
    )
    try {
      settingsLauncher.launch(intent)
    } catch (_: ActivityNotFoundException) {
      ToastUtil.toast(R.string.media_settings_unavailable)
    }
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> {
          lifecycleActive = true
          refresh()
        }

        Lifecycle.Event.ON_PAUSE -> {
          // 后台期间禁止列表操作和查询结果写入，前台恢复时重新检查权限。
          lifecycleActive = false
          loading = true
        }

        else -> Unit
      }
    }
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
    }
  }

  // 查询代次和生命周期共同约束结果写入，加载前后都必须具备完整权限。
  LaunchedEffect(reloadRevision, lifecycleActive) {
    if (!lifecycleActive || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      return@LaunchedEffect
    }
    val queryRevision = reloadRevision
    hasMediaPermission = MediaStoreQuery.hasPermission(context)
    if (!hasMediaPermission) {
      mediaList = emptyList()
      selectedItems.value = emptySet()
      selectedFolder = null
      showFolderMenu = false
      loading = false
      return@LaunchedEffect
    }
    loading = true
    val items = MediaStoreQuery.loadAll(context)
    currentCoroutineContext().ensureActive()
    if (queryRevision != reloadRevision || !lifecycleActive ||
      !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    ) {
      return@LaunchedEffect
    }
    hasMediaPermission = MediaStoreQuery.hasPermission(context)
    if (!hasMediaPermission) {
      mediaList = emptyList()
      selectedItems.value = emptySet()
      selectedFolder = null
      showFolderMenu = false
      loading = false
      return@LaunchedEffect
    }
    permissionRequestDenied = false
    val selectedUris = selectedItems.value.map { item -> item.uri }.toSet()
    mediaList = items
    // 选择按 Uri 关联最新条目，文件夹切换保留选择，失去访问的条目从选择中移除。
    selectedItems.value = items.filter { item -> item.uri in selectedUris }.toSet()
    if (selectedFolder != null && items.none { item -> item.folderName == selectedFolder }) {
      selectedFolder = null
    }
    loading = false
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
    if (hasMediaPermission) {
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
              .clickable(enabled = hasMediaPermission && !loading) {
                showFolderMenu = true
              }
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
            expanded = showFolderMenu && hasMediaPermission && !loading,
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
        TextButton(
          enabled = hasMediaPermission && !loading && selectedItems.value.isNotEmpty(),
          onClick = {
            if (lifecycleActive && !loading && MediaStoreQuery.hasPermission(context)) {
              // 选中项跨文件夹保留，一次添加全部已选媒体。
              onConfirm(selectedItems.value.toList())
            } else {
              refresh()
            }
          }
        ) {
          Text(stringResource(R.string.media_library_add_count, selectedItems.value.size))
        }
      }
    }

    Box(modifier = Modifier.height(sheetHeight)) {
      when {
        !hasMediaPermission -> {
          Column(
            modifier = Modifier
              .align(Alignment.TopCenter)
              .fillMaxWidth()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = stringResource(R.string.media_access_permission_required),
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceEvenly
            ) {
              TextButton(onClick = requestAccess) {
                Text(stringResource(R.string.media_allow_access))
              }
              // 系统设置入口始终可点击，永久拒绝时仍可由用户手动授予权限。
              TextButton(onClick = openSettings) {
                Text(stringResource(R.string.media_open_settings))
              }
            }

            if (permissionRequestDenied) {
              OutlinedCard(
                modifier = Modifier.padding(15.dp)
              ) {
                Text(
                  modifier = Modifier.padding(15.dp),
                  text = stringResource(R.string.media_permission_settings_hint),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

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
      .height(70.dp)
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
      .size(54.dp)
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
