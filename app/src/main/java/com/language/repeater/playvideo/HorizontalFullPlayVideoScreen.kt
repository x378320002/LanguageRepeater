package com.language.repeater.playvideo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.language.repeater.playvideo.compose.FullScreenControlPanel
import com.language.repeater.playvideo.compose.PlayerPlaylistSheet
import com.language.repeater.playvideo.compose.VideoPlayerBox

/**
 * 横窗口下的全屏布局：视频铺满整屏，全部控件收进底部的悬浮卡片。
 *
 * 点击画面任意位置切换卡片显隐，其余手势（双击回句首、长按倍速、
 * 左右滑动切句、上下滑动调亮度音量）与默认布局一致。
 * 屏幕方向与系统栏由 [PlayVideoScreen] 统一处理。
 */
@Composable
fun HorizontalFullPlayVideoScreen(
  viewModel: PlayerViewModel = viewModel()
) {
  var panelVisible by remember { mutableStateOf(false) }
  var showPlaylistSheet by remember { mutableStateOf(false) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    VideoPlayerBox(
      viewModel = viewModel,
      fullScreen = true,
      onTap = { panelVisible = !panelVisible }
    )

    AnimatedVisibility(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(top = 15.dp)
        .safeDrawingPadding(),
      visible = panelVisible,
      enter = fadeIn() + slideInHorizontally { fullWidth -> fullWidth },
      exit = fadeOut() + slideOutHorizontally { fullWidth -> fullWidth },
    ) {
      FullScreenControlPanel(
        modifier = Modifier.width(280.dp),
        viewModel = viewModel,
        onOpenPlaylist = { showPlaylistSheet = true }
      )
    }

    IconButton(
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(top = 25.dp, start = 15.dp),
      onClick = {
        viewModel.changeScreenMode(false)
      }) {
      Icon(
        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_chevron_left_outline),
        tint = Color.White.copy(alpha = 0.6f),
        contentDescription = null
      )
    }
  }

  if (showPlaylistSheet) {
    PlayerPlaylistSheet(
      viewModel = viewModel,
      onDismiss = { showPlaylistSheet = false }
    )
  }
}
