package com.language.repeater.playvideo

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.platform.ComposeView
import com.language.repeater.foundation.BaseFragment


/**
 * PlayVideoFragmentCompose — 播放视频页的 Compose 薄壳版本 (阶段 1).
 *
 * 命名说明:
 *   - 旧的 View 版本继续叫 PlayVideoFragment (因为 playvideo/components/ 下 6 个 Component 里
 *     都用了 BaseComponent<PlayVideoFragment> 泛型, 改名会连锁修改)
 *   - 新的 Compose 版本叫 PlayVideoFragmentCompose, MainActivity 的 NavGraph 注册它作为启动页
 *   - 阶段 8 全部迁移完成、旧 PlayVideoFragment 删除后, 可以把本类重命名回 PlayVideoFragment
 *
 * 阶段 1 的职责:
 *   1. 创建 ComposeView 作为根 View
 *   2. 用 activityViewModels() 持有 PlayerViewModel
 *   3. 系统 UI 隐藏/显示 (Activity Window API, 无法搬到 Composable)
 *   4. 提供 BottomSheet 弹出和跳转设置页的回调 (阶段 1 保留旧的 BottomSheetDialogFragment,
 *      阶段 5 会全部换成 M3 ModalBottomSheet)
 *   5. 阶段 3 会在这里加 rememberLauncherForActivityResult 处理文件选择
 *
 * 所有 UI 逻辑、状态管理都在 playvideo/compose/x.kt 和 PlayerViewModel 里.
 * 旧 View 实现对照参考: PlayVideoFragment.kt (同目录下)
 */
class PlayVideoFragmentCompose : BaseFragment() {
  companion object {
    const val TAG = "wangzixu_PlayVideoFragmentCompose"
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    return ComposeView(requireContext()).apply {
      setContent {
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
          PlayVideoScreen()
        }
      }
    }
  }
}
//onOpenPlaylist = {
//  // 阶段 1: 复用旧的 PlaylistSheetFragment. 阶段 5 换成 Compose ModalBottomSheet.
//  PlaylistSheetFragment().show(childFragmentManager, "playlist")
//},
//onOpenHistory = {
//  HistorySheetFragment().show(childFragmentManager, "history")
//},
//onOpenSleepTimer = {
//  SleepTimerSheetFragment().show(childFragmentManager, "sleep_timer")
//},
//onPickVideoFiles = {
//  // 阶段 3 会在这里接 rememberLauncherForActivityResult 或者 Fragment 的
//  // registerForActivityResult 启动文件选择器. 阶段 1 先打 Log 观察点击行为.
//  Log.i(TAG, "onPickVideoFiles: 阶段 3 会接入文件选择器")
//},
