@file:OptIn(ExperimentalMaterial3Api::class)

package com.language.repeater.playvideo

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip

/**
 * 竖屏默认布局：顶栏 + 16:9 视频画面 + 自上而下依次排开的各功能区。
 */
@Composable
fun DefaultPlayVideoScreen(
  backStack: NavBackStack<NavKey>? = null,
  viewModel: PlayerViewModel = viewModel()
) {
  var showPlaylistSheet by remember { mutableStateOf(false) }

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
      if (viewModel.playbackListController.items.collectAsState().value.isEmpty()) {
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

      // 底部安全间距
      Spacer(modifier = Modifier.height(20.dp))
    }
  }

  if (showPlaylistSheet) {
    PlayerPlaylistSheet(
      viewModel = viewModel,
      onDismiss = { showPlaylistSheet = false }
    )
  }
}

private val HorizontalPadding = 15.dp
