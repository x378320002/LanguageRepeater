package com.language.repeater.app

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.language.repeater.databinding.ActivityNavHostBinding
import com.language.repeater.foundation.BaseActivity
import com.language.repeater.playvideo.PlayVideoScreen
import com.language.repeater.setting.SettingScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object PlayVideoPageKey : NavKey

@Serializable
object SettingPageKey : NavKey

class MainActivity : BaseActivity<ActivityNavHostBinding>() {
  override fun inflateBinding(layoutInflater: LayoutInflater): ActivityNavHostBinding {
    return ActivityNavHostBinding.inflate(layoutInflater)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        val backStack = rememberNavBackStack(PlayVideoPageKey)

        NavDisplay(
          backStack = backStack,
          onBack = { backStack.removeLastOrNull() },
          entryProvider = entryProvider {
            entry<PlayVideoPageKey> {
              PlayVideoScreen(
                backStack = backStack
              )
            }
            entry<SettingPageKey> {
              SettingScreen(
                backStack = backStack
              )
            }
          }
        )
      }
    }

    lifecycleScope.launch {
      GlobalState.nightMode.collect {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (it) {
          AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
          controller.isAppearanceLightStatusBars = false
          controller.isAppearanceLightNavigationBars = false
        } else {
          AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
          controller.isAppearanceLightStatusBars = true
          controller.isAppearanceLightNavigationBars = true
        }
      }
    }
  }
}