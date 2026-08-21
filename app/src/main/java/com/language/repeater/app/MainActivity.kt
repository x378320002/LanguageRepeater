package com.language.repeater.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.RetainedValuesStoreRegistry
import androidx.compose.runtime.retain.retainRetainedValuesStoreRegistry
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.language.repeater.playvideo.PlayVideoScreen
import com.language.repeater.setting.SettingScreen
import com.language.repeater.theme.MyTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object PlayVideoPageKey : NavKey

@Serializable
object SettingPageKey : NavKey

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()

    setContent {
      MyTheme {
        val transitionTime = 400
        val backStack = rememberNavBackStack(PlayVideoPageKey)
        NavDisplay(
          backStack = backStack,
          entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
            rememberRetainedValuesStoreNavEntryDecorator()
          ),
          transitionSpec = {
            // 进入新页面：从右滑入，旧页面左滑出
            slideInHorizontally(
              initialOffsetX = { it },
              animationSpec = tween(transitionTime)
            ) togetherWith slideOutHorizontally(
              targetOffsetX = { -it/2 },
              animationSpec = tween(transitionTime)
            )
          },
          popTransitionSpec = {
            // 返回上一页：从左滑入，当前页面右滑出
            slideInHorizontally(
              initialOffsetX = { -it/2 },
              animationSpec = tween(transitionTime)
            ) togetherWith slideOutHorizontally(
              targetOffsetX = { it },
              animationSpec = tween(transitionTime)
            )
          },
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

    val controller = WindowInsetsControllerCompat(window, window.decorView)
    lifecycleScope.launch {
      GlobalState.nightMode.collect {
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


@Composable
fun <T : Any> rememberRetainedValuesStoreNavEntryDecorator(
  registry: RetainedValuesStoreRegistry = retainRetainedValuesStoreRegistry()
): RetainedValuesStoreNavEntryDecorator<T> {
  return remember(registry) {
    RetainedValuesStoreNavEntryDecorator(registry)
  }
}

class RetainedValuesStoreNavEntryDecorator<T : Any>(
  registry: RetainedValuesStoreRegistry,
) : NavEntryDecorator<T>(
  onPop = { key ->
    registry.clearChild(key)
  },
  decorate = { entry ->
    registry.LocalRetainedValuesStoreProvider(entry.contentKey) { entry.Content() }
  },
)