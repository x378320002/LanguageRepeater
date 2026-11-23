package com.language.repeater.playvideo.compose

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.language.repeater.R
import com.language.repeater.datastore.DsManager
import com.language.repeater.utils.FileUtil
import com.language.repeater.utils.SubtitleFile
import com.language.repeater.utils.SubtitleFolderScanner
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 字幕选择弹窗的加载状态。 */
private sealed interface SubtitlePickerState {
  data object Loading : SubtitlePickerState
  data object NoFolder : SubtitlePickerState
  data object FolderUnavailable : SubtitlePickerState
  data class Ready(
    val files: List<SubtitleFile>,
    val truncated: Boolean
  ) : SubtitlePickerState
}

/**
 * 应用内字幕选择弹窗。
 *
 * 主列表递归扫描「字幕文件夹」，按扩展名筛 srt/vtt，不受系统 MIME 映射影响；
 * 系统文件选择器只作为兜底入口，选择后同样按扩展名校验。
 * 字幕文件与文件夹的持久授权由 [com.language.repeater.playback.SubtitleController] 落盘处理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitlePickerSheet(
  currentVideoName: String?,
  onConfirm: (Uri) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  // 打开即完全展开，避免半展开时底部入口被裁掉。
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val listMaxHeight = (LocalWindowInfo.current.containerDpSize.height - 220.dp)
    .coerceIn(120.dp, 420.dp)

  // null 表示尚未从 DataStore 读到首值，避免把异步读的默认空串误判为「未设置文件夹」。
  var folderUriString by remember { mutableStateOf<String?>(null) }
  var pickerState by remember { mutableStateOf<SubtitlePickerState>(SubtitlePickerState.Loading) }
  val currentVideoBase = remember(currentVideoName) {
    currentVideoName?.substringBeforeLast(".")?.lowercase().orEmpty()
  }

  val folderPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocumentTree()
  ) { uri ->
    if (uri != null) {
      FileUtil.takePersistablePermission(context, uri)
      val uriString = uri.toString()
      folderUriString = uriString
      DsManager.setAsync(DsManager.KEY_SUBTITLE_FOLDER, uriString)
    }
  }
  val filePicker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      // 系统选择器无法可靠按 srt/vtt 过滤，拿到结果后按扩展名自行校验。
      scope.launch {
        val name = withContext(Dispatchers.IO) {
          FileUtil.getFileUriName(context, uri)
        }
        if (SubtitleFolderScanner.isSubtitleName(name)) {
          onConfirm(uri)
        } else {
          ToastUtil.toast(R.string.subtitle_file_unsupported)
        }
      }
    }
  }

  LaunchedEffect(Unit) {
    folderUriString = DsManager.get(DsManager.KEY_SUBTITLE_FOLDER, "")
  }

  LaunchedEffect(folderUriString) {
    val folder = folderUriString ?: return@LaunchedEffect
    pickerState = SubtitlePickerState.Loading
    pickerState = withContext(Dispatchers.IO) {
      if (folder.isBlank()) {
        return@withContext SubtitlePickerState.NoFolder
      }
      val result = SubtitleFolderScanner.scan(context, folder.toUri())
        ?: return@withContext SubtitlePickerState.FolderUnavailable
      SubtitlePickerState.Ready(result.files, result.truncated)
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
        .padding(start = 15.dp, end = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = stringResource(R.string.select_subtitle),
        style = MaterialTheme.typography.titleMedium
      )
      Spacer(modifier = Modifier.weight(1f))
      TextButton(
        enabled = pickerState !is SubtitlePickerState.Loading,
        onClick = { folderPicker.launch(null) }
      ) {
        Text(
          stringResource(
            if (folderUriString.isNullOrBlank()) {
              R.string.subtitle_picker_choose_folder
            } else {
              R.string.subtitle_picker_change_folder
            }
          )
        )
      }
    }

    HorizontalDivider(
      modifier = Modifier.padding(horizontal = 15.dp),
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )

    when (val state = pickerState) {
      SubtitlePickerState.Loading -> {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator()
        }
      }

      SubtitlePickerState.NoFolder -> {
        SubtitleStateMessage(
          iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_folder_open_outline,
          title = stringResource(R.string.subtitle_picker_no_folder),
          description = stringResource(R.string.subtitle_picker_no_folder_desc)
        )
      }

      SubtitlePickerState.FolderUnavailable -> {
        SubtitleStateMessage(
          iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_folder_off_outline,
          title = stringResource(R.string.subtitle_picker_folder_unavailable),
          description = stringResource(R.string.subtitle_picker_folder_unavailable_desc)
        )
      }

      is SubtitlePickerState.Ready -> {
        val files = remember(state.files, currentVideoBase) {
          sortSubtitleFiles(state.files, currentVideoBase)
        }
        if (files.isEmpty()) {
          SubtitleStateMessage(
            iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_file_search_outline,
            title = stringResource(
              if (state.truncated) {
                R.string.subtitle_picker_truncated_title
              } else {
                R.string.subtitle_picker_empty_folder
              }
            ),
            description = stringResource(
              if (state.truncated) {
                R.string.subtitle_picker_truncated_desc
              } else {
                R.string.subtitle_picker_empty_folder_desc
              }
            )
          )
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = listMaxHeight)
          ) {
            items(
              items = files,
              key = { file -> file.uri.toString() }
            ) { file ->
              SubtitleFileRow(
                file = file,
                matched = currentVideoBase.isNotEmpty() &&
                  file.baseName.lowercase() == currentVideoBase,
                onClick = { onConfirm(file.uri) }
              )
            }
            if (state.truncated) {
              item {
                Text(
                  text = stringResource(R.string.subtitle_picker_truncated),
                  modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      }
    }

    HorizontalDivider(
      modifier = Modifier.padding(horizontal = 15.dp),
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )

    TextButton(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 4.dp),
      onClick = { filePicker.launch(arrayOf("*/*")) }
    ) {
      Icon(
        painter = painterResource(
          com.composables.icons.tabler.outline.R.drawable.tabler_ic_folder_open_outline
        ),
        contentDescription = null,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(stringResource(R.string.subtitle_picker_from_files))
    }
  }
}

/** 空状态：图标 + 标题 + 说明，整体居中且不依赖固定高度。 */
@Composable
private fun SubtitleStateMessage(
  iconRes: Int,
  title: String,
  description: String
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 32.dp, vertical = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Icon(
      painter = painterResource(iconRes),
      contentDescription = null,
      modifier = Modifier.size(40.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    )
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      textAlign = TextAlign.Center
    )
    Text(
      text = description,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )
  }
}

@Composable
private fun SubtitleFileRow(
  file: SubtitleFile,
  matched: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 15.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      painter = painterResource(
        com.composables.icons.tabler.outline.R.drawable.tabler_ic_file_text_outline
      ),
      contentDescription = null,
      modifier = Modifier.size(22.dp),
      tint = MaterialTheme.colorScheme.primary
    )

    Column(
      modifier = Modifier
        .weight(1f)
        .padding(start = 12.dp)
    ) {
      Text(
        text = file.name,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis
      )
      val location = file.relativeDirectory
      when {
        matched && location.isNotEmpty() -> {
          Text(
            text = stringResource(R.string.subtitle_picker_match_current_with_dir, location),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis
          )
        }

        matched -> {
          Text(
            text = stringResource(R.string.subtitle_picker_match_current),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
          )
        }

        location.isNotEmpty() -> {
          Text(
            text = location,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis
          )
        }
      }
    }

//    Text(
//      text = file.extension.uppercase(),
//      style = MaterialTheme.typography.labelSmall,
//      color = MaterialTheme.colorScheme.onSurfaceVariant
//    )
  }
}

/** 与当前视频同名的字幕排在前面，其余按文件名和相对路径排序。 */
private fun sortSubtitleFiles(
  files: List<SubtitleFile>,
  currentVideoBase: String
): List<SubtitleFile> {
  return files.sortedWith(
    compareByDescending<SubtitleFile> { file ->
      currentVideoBase.isNotEmpty() && file.baseName.lowercase() == currentVideoBase
    }
      .thenBy { file -> file.name.lowercase() }
      .thenBy { file -> file.relativePath.lowercase() }
  )
}
