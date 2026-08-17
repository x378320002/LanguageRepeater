package com.language.repeater.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.fragment.findNavController
import com.language.repeater.app.GlobalState
import com.language.repeater.foundation.BaseFragment

/**
 * SettingFragment 现在只是一个薄壳，职责只有三件事：
 *   1. 创建 ComposeView 作为根 View
 *   2. 持有需要 Fragment API 才能做的事（文件选择器 Launcher）
 *
 * 所有 UI 逻辑、状态管理都在 SettingScreen.kt / SettingViewModel.kt 里
 */
class SettingFragment : BaseFragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    return ComposeView(requireContext()).apply {
      setContent {
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
          SettingScreen(findNavController())
        }
      }
    }
  }
}
