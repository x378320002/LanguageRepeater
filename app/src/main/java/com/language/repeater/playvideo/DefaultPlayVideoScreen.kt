@file:OptIn(ExperimentalMaterial3Api::class)

package com.language.repeater.playvideo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.language.repeater.app.SettingPageKey
import com.language.repeater.playvideo.compose.PlayTopBar
import com.language.repeater.playvideo.compose.PlayerControlSection
import com.language.repeater.playvideo.compose.PlayerEditSection
import com.language.repeater.playvideo.compose.PlayerPlaylistSheet
import com.language.repeater.playvideo.compose.PlayerProgressSection
import com.language.repeater.playvideo.compose.PlayerSubActionsSection
import com.language.repeater.playvideo.compose.PlayerWaveformSection
import com.language.repeater.playvideo.compose.VideoPlayerBox
import com.language.repeater.utils.FileUtil

/**
 * 竖屏默认布局：顶栏 + 16:9 视频画面 + 自上而下依次排开的各功能区。
 */
@Composable
fun DefaultPlayVideoScreen(
  backStack: NavBackStack<NavKey>? = null,
  viewModel: PlayerViewModel = viewModel()
) {
  var showPlaylistSheet by remember { mutableStateOf(false) }
  val permissionErrorMediaId by viewModel.permissionErrorMediaId.collectAsState()
  val playlist by viewModel.playbackListController.items.collectAsState()

  val reGrantPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri ->
    val mediaId = permissionErrorMediaId
    if (uri != null && mediaId != null) {
      viewModel.handleReGrantedUri(mediaId, uri)
    } else {
      viewModel.dismissPermissionError()
    }
  }

  Scaffold(
    topBar = {
      PlayTopBar(viewModel, onGoSetting = {
        backStack?.add(SettingPageKey)
      })
    }
  ) { innerPadding: PaddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      if (playlist.isEmpty()) {
        Box(
          modifier = Modifier
            .padding(horizontal = HorizontalPadding)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(CardDefaults.shape)
            .background(CardDefaults.cardColors().containerColor),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "点击+号选择音视频",
            color = LocalContentColor.current.copy(alpha = 0.7f)
          )
        }
      } else {
        VideoPlayerBox(
          viewModel = viewModel,
          modifier = Modifier.padding(horizontal = HorizontalPadding)
        )
      }

      PlayerProgressSection(
        viewModel = viewModel,
        modifier = Modifier.padding(horizontal = HorizontalPadding)
      )

      PlayerWaveformSection(
        viewModel = viewModel,
        modifier = Modifier.padding(horizontal = HorizontalPadding)
      )

      PlayerEditSection(viewModel = viewModel)

      PlayerSubActionsSection(
        viewModel = viewModel,
        fullScreenEntered = false
      )

      PlayerControlSection(
        viewModel = viewModel,
        onOpenPlaylist = { showPlaylistSheet = true }
      )

      Spacer(modifier = Modifier.height(20.dp))
    }
  }

  if (showPlaylistSheet) {
    PlayerPlaylistSheet(
      viewModel = viewModel,
      onDismiss = { showPlaylistSheet = false }
    )
  }

  val invalidMediaId = permissionErrorMediaId
  if (invalidMediaId != null) {
    val invalidItem = playlist.find { item -> item.mediaId == invalidMediaId }
    // 系统选择器无法被定位到指定文件（定位需要该文件的读权限，而此时权限已失效），
    // 因此把原位置显示出来，由用户自行导航过去。
    val location = invalidItem?.uri?.let { uri -> FileUtil.getReadableLocation(uri.toUri()) }
    AlertDialog(
      onDismissRequest = { viewModel.dismissPermissionError() },
      title = { Text("文件访问权限已失效") },
      text = {
        Column {
          Text("「${invalidItem?.title.orEmpty()}」的访问权限已失效，请在文件选择器中重新选中它。")
          if (!location.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "原位置：$location",
              style = MaterialTheme.typography.bodySmall,
              color = LocalContentColor.current.copy(alpha = 0.7f)
            )
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            reGrantPicker.launch(
              arrayOf(
                "video/*",
                "audio/*"
              )
            )
          }
        ) {
          Text("重新选择")
        }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.dismissPermissionError() }) {
          Text("取消")
        }
      }
    )
  }
}

private val HorizontalPadding = 15.dp
