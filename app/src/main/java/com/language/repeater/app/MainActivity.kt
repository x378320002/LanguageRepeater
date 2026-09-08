package com.language.repeater.app

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import com.language.repeater.datastore.DsManager
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.language.repeater.PointerEventDemo
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

  override fun onStart() {
    super.onStart()
    val app = application as MainApp
    // 幂等调用：仅在连接确实缺失（如重试耗尽）时才真正发起，兜住服务连不上导致无法播放的情况
    app.connectPlaybackService()
    // 长时间后台可能被系统收走解码器资源，播放器停在 IDLE、画面全黑，这里把首帧找回来
    app.playbackGraph.media3Player.prepareIfIdle()
  }

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

//              PointerEventDemo()
            }
          }
        )
      }
    }

    // 系统栏图标明暗跟随当前配色
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    lifecycleScope.launch {
      DsManager.observe(DsManager.KEY_NIGHT_MODE, true).collect { nightMode ->
        Log.i("MainActivity", "nightMode is $nightMode")
        controller.isAppearanceLightStatusBars = !nightMode
        controller.isAppearanceLightNavigationBars = !nightMode
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