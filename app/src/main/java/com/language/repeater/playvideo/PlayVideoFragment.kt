package com.language.repeater.playvideo

import android.content.res.Configuration
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.activityViewModels
import androidx.media3.common.Player
import com.language.repeater.databinding.VideoPlayFragmentBinding
import com.language.repeater.foundation.BaseFragment
import com.language.repeater.playvideo.components.PlayAllWaveComponent
import com.language.repeater.playvideo.components.PlayGestureComponent
import com.language.repeater.playvideo.components.PlayScrollWaveComponent
import com.language.repeater.playvideo.components.PlayUIActComponent
import com.language.repeater.playvideo.components.SelectFileComponent


class PlayVideoFragment: BaseFragment(), Player.Listener  {
  companion object {
    const val TAG = "wangzixu_PlayVideoFragment"
  }

  lateinit var binding: VideoPlayFragmentBinding
  val viewModel: PlayerViewModel by activityViewModels()
  var isLandScreen = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val orientation = resources.configuration.orientation
    Log.i(TAG, "onCreate, pid:${Process.myPid()}, orientation:$orientation")
    addComponent(SelectFileComponent())
    addComponent(PlayScrollWaveComponent())
    addComponent(PlayAllWaveComponent())
    addComponent(PlayUIActComponent())
    addComponent(PlayGestureComponent())
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      isLandScreen = false
      showSystemUI()
    } else {
      isLandScreen = true
      hideSystemUI()
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    Log.i(TAG, "$TAG onCreateView")
    binding = VideoPlayFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  private fun hideSystemUI() {
    val window = activity?.window ?: return
    // 获取 WindowInsetsController
    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
    // 1. 隐藏状态栏 (顶部) 和 导航栏 (底部)
    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    // 2. 设置交互模式：
    // BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE:
    // 用户从边缘滑动时，系统栏会暂时显示，几秒后自动隐藏（最适合视频播放）
    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
  }

  private fun showSystemUI() {
    val window = activity?.window ?: return
    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
    // 显示状态栏和导航栏
    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
  }

  override fun onDestroyView() {
    super.onDestroyView()
    Log.i(TAG, "$TAG onDestroyView")
  }
}