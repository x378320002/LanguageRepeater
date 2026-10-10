package com.language.repeater.playvideo

import android.R.attr.onClick
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
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
 * 竖窗口下的全屏布局：画面铺满，控件收进底部悬浮卡片。
 *
 * 两种情况会走到这里：竖向视频的全屏播放，以及刚进入全屏、屏幕尚未转到横向的过渡期。
 * 交互与横向全屏一致，点击画面切换卡片显隐。
 */
@Composable
fun VerticalFullPlayVideoScreen(
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
        .align(Alignment.BottomCenter)
        .safeDrawingPadding(),
      visible = panelVisible,
      enter = fadeIn() + slideInVertically { height -> height / 2 },
      exit = fadeOut() + slideOutVertically { height -> height / 2 },
    ) {
      FullScreenControlPanel(
        modifier = Modifier.fillMaxWidth(),
        viewModel = viewModel,
        isVerticalFull = true,
        onOpenPlaylist = { showPlaylistSheet = true },
      )
    }

    IconButton(
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(top = 50.dp),
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
