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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.RetainedValuesStoreRegistry
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.retain.retainManagedRetainedValuesStore
import androidx.compose.runtime.retain.retainRetainedValuesStoreRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.SaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.composables.ui.theme.ComposablesTheme
import com.composeunstyled.theme.buildTheme
import com.language.repeater.playvideo.PlayVideoScreen
import com.language.repeater.setting.SettingScreen
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
      val time = 1000
      ComposablesTheme {
        // Your application content
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
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
                animationSpec = tween(time)
              ) togetherWith slideOutHorizontally(
                targetOffsetX = { -it/2 },
                animationSpec = tween(time)
              )
            },
            popTransitionSpec = {
              // 返回上一页：从左滑入，当前页面右滑出
              slideInHorizontally(
                initialOffsetX = { -it/2 },
                animationSpec = tween(time)
              ) togetherWith slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(time)
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