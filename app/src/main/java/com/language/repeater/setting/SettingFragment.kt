package com.language.repeater.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.fragment.findNavController
import com.language.repeater.foundation.BaseFragment
import com.language.repeater.utils.FileUtil

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
        val viewModel: SettingViewModel = viewModel()
        val nightMode by viewModel.nightMode.collectAsState()

        MaterialTheme(colorScheme = if (nightMode) darkColorScheme() else lightColorScheme()) {
          SettingScreen(
            nightMode = nightMode,
            onNightModeChange = {
              viewModel.saveNightMode(it)
            },
            onBack = {
              findNavController().navigateUp()
            },
            onPickSubtitleFolder = {

            },
          )
        }
      }
    }
  }
}
