package com.language.repeater.playvideo

import android.content.res.Configuration
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.activityViewModels
import androidx.media3.common.Player
import com.language.repeater.databinding.VideoPlayFragmentBinding
import com.language.repeater.foundation.BaseFragment
import com.language.repeater.playvideo.components.PlayGestureComponent
import com.language.repeater.playvideo.components.PlayItemChangeComponent
import com.language.repeater.playvideo.components.PlayScrollWaveComponent
import com.language.repeater.playvideo.components.PlayUIActComponent
import com.language.repeater.playvideo.components.SelectFileComponent
import com.language.repeater.playvideo.components.SetSubtitleComponent


class PlayVideoFragment: BaseFragment(), Player.Listener  {
  companion object {
    const val TAG = "wangzixu_PlayVideoFragment"
  }

  lateinit var binding: VideoPlayFragmentBinding
  val viewModel: PlayerViewModel by activityViewModels()
  var isLandScreen = false

  var uiActComponent: PlayUIActComponent? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val orientation = resources.configuration.orientation
    Log.i(TAG, "onCreate, pid:${Process.myPid()}, orientation:$orientation")
    addComponent(SelectFileComponent())
    addComponent(PlayScrollWaveComponent())
    addComponent(PlayUIActComponent().apply { uiActComponent = this })
    addComponent(SetSubtitleComponent())
    addComponent(PlayGestureComponent())
    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      isLandScreen = false
      showSystemUI()
      addComponent(PlayItemChangeComponent())
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
    binding = VideoPlayFragmentBinding.inflate(inflater, container, false)
    if (!isLandScreen) {
      ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        Log.i(TAG, "$TAG onCreateView systemBars.top:${systemBars.top}, bottom:${systemBars.bottom}")
        binding.mainLayout?.setPadding(
          systemBars.left,
          systemBars.top,
          systemBars.right,
          systemBars.bottom
        )
        insets
      }
    }
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