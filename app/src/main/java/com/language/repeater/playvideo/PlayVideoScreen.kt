package com.language.repeater.playvideo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * 播放页入口。
 *
 * 本身不摆放任何控件，只做两件事：
 * 1. 按「是否全屏」与窗口实际方向选择一套布局；
 * 2. 把全屏状态落到 Activity 上——请求屏幕方向、控制系统栏可见性、接管返回键。
 *
 * 方向与系统栏是 Activity 级别的副作用，集中在这里做，各套布局只关心自己的排布。
 */
@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun PlayVideoScreen(
  backStack: NavBackStack<NavKey>? = null,
  viewModel: PlayerViewModel = viewModel()
) {
  val fullScreen = viewModel.isFullScreen
  val activity = LocalActivity.current

  // 用真实窗口尺寸判方向：分屏、多窗口下也准确，且请求旋转后会自动跟上。
  val containerSize = LocalWindowInfo.current.containerSize
  val landscape = containerSize.width > containerSize.height

  // 全屏朝向跟着画面走：横向视频转横屏、竖向视频留竖屏，避免铺满后两侧留大黑边。
  val videoAspectRatio by viewModel.videoAspectRatio.collectAsState()

  LaunchedEffect(fullScreen, videoAspectRatio, activity) {
    val window = activity?.window ?: return@LaunchedEffect
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    if (fullScreen) {
      activity.requestedOrientation = fullScreenOrientation(videoAspectRatio)
      if (landscape) {
        controller.systemBarsBehavior =
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
      }
    } else {
      activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
      controller.show(WindowInsetsCompat.Type.systemBars())
    }
  }

  // 离开播放页时把方向与系统栏交还系统，避免设置页等页面继承全屏状态。
  DisposableEffect(activity) {
    onDispose {
      val window = activity?.window ?: return@onDispose
      WindowInsetsControllerCompat(window, window.decorView)
        .show(WindowInsetsCompat.Type.systemBars())
      activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
  }

  // 全屏下返回键先退回默认布局，不直接退出页面。
  BackHandler(enabled = fullScreen) {
    viewModel.changeScreenMode(false)
  }

  // 布局跟随窗口实际方向：既覆盖竖向视频的常态，也承接刚进全屏、屏幕还没转过来的过渡期。
  when {
    fullScreen && landscape -> {
      HorizontalFullPlayVideoScreen(viewModel = viewModel)
    }

    fullScreen -> {
      VerticalFullPlayVideoScreen(viewModel = viewModel)
    }

    else -> {
      DefaultPlayVideoScreen(
        backStack = backStack,
        viewModel = viewModel
      )
    }
  }
}

/**
 * 全屏时该请求的屏幕朝向。
 *
 * 宽高比为 0 表示画面未就绪或是纯音频，此时按横屏处理——这是绝大多数学习素材的形态，
 * 真实比例解析出来后会再触发一次修正。
 * 用 SENSOR 变体是为了让用户仍能翻转设备换正反面。
 */
private fun fullScreenOrientation(videoAspectRatio: Float): Int {
  val portraitVideo = videoAspectRatio > 0f && videoAspectRatio < 1f
  return if (portraitVideo) {
    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
  } else {
    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
  }
}
