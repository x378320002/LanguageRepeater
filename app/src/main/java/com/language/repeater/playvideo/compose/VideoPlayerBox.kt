package com.language.repeater.playvideo.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.language.repeater.R

@Composable
fun VideoPlayerBox(
  modifier: Modifier = Modifier,
  player: Player?,
  onClickFullScreen: () -> Unit
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(16f / 9f),
    shape = MaterialTheme.shapes.small,
    colors = CardDefaults.cardColors(containerColor = Color.Black),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
          PlayerView(context).apply {
            useController = false // 不显示 ExoPlayer 自带的控制条 (我们用 Compose 自绘)
            // 其他 XML 配置对齐:
            //   android:background="@color/color_background_container"
            //   app:shutter_background_color="@color/color_background_container"
            //   app:resize_mode="fixed_height"  (Media3 常量: AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT)
            // 如果要精确对齐, 可以补上. 阶段 1 用默认值先跑通.
          }
        },
        update = { playerView ->
          // 每次外部 player 变化 (从 null 到有实例, 或切换实例) 都同步到 View.
          // 直接赋值即可, Media3 会自动接管 Surface 绑定.
          if (playerView.player !== player) {
            playerView.player = player
          }
        },
        onRelease = { playerView ->
          // Composable 从组合树移除时, 断开 Player 引用避免持有引用泄漏.
          playerView.player = null
        },
      )

      // ==================================================================
      // 右下角浮层: 字幕按钮 + 全屏按钮
      // ==================================================================
      // 教学点: Box 的子元素默认位置是 TopStart, 用 Modifier.align 单独指定某个子元素的位置.
      // Column 让两个按钮竖排 (对应旧 XML 里 orientation="vertical").
      Column(
        modifier = Modifier.align(Alignment.BottomEnd),
      ) {

          // ---------- TODO 1: 字幕按钮 (btn_subtitle) ----------
        // 提示:
        //   IconButton(
        //     onClick = onClickSubtitle,
        //     modifier = Modifier.size(40.dp),
        //   ) {
        //     Icon(
        //       painter = painterResource(R.drawable.ic_subtitles),
        //       contentDescription = "字幕",
        //       tint = MaterialTheme.colorScheme.onBackground,
        //     )
        //   }
        // 需要 import: androidx.compose.foundation.layout.size

        // ---------- TODO 2: 全屏按钮 (btn_fullscreen) ----------
        // 参照 TODO 1:
        //   - Icon 资源: R.drawable.ic_fullscreen_enter
        //     (阶段 4 横屏适配时会根据是否横屏切换 enter/exit 两个图标)
        //   - contentDescription: "全屏切换"
        //   - onClick: onClickFullScreen
      }
    }
  }
}

// =========================================================================
// 补充: AndroidView 的 factory 会不会重复被调?
// =========================================================================
// factory 只在这个 Composable 第一次进入组合树时调一次. 相同位置的 Composable 重组
// (recomposition) 时不会重建 View, 只会跑 update.
//
// 如果 Composable 被卸载 (从组合树移除, 比如切换到别的页面), View 会被销毁, onRelease 触发.
// 下次再次进入组合树, factory 会重新调用.
// =========================================================================

// =========================================================================
// 补充: Compose Player? 状态怎么传进来?
// =========================================================================
// 在 PlayVideoScreen 里会这么写:
//   val player: Player? by viewModel.playerInstance.collectAsState()
//   VideoPlayerBox(player = player, ...)
//
// player 从 null → 有实例, 会触发这个 Composable 重组, update lambda 被调用, PlayerView.player 被赋值.
// 后面 player 稳定后不再变, update 也就不再更新 player 引用 (因为 `!==` 判断).
// =========================================================================
