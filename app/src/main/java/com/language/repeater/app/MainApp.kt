package com.language.repeater.app

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.language.repeater.playback.PlaybackGraph
import com.language.repeater.datastore.DsManager
import com.language.repeater.player.media3.PlaybackServiceConnection
import kotlinx.coroutines.runBlocking

/**
 * Date: 2025-07-14
 * Time: 16:51
 * Description:
 */
class MainApp : Application(), SingletonImageLoader.Factory {

  /** Media3 平台连接，负责启动并维持 PlaybackService。 */
  private val serviceConnection: PlaybackServiceConnection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    PlaybackServiceConnection(this)
  }

  companion object {
    lateinit var instance: MainApp
      private set

    /** 应用级播放依赖图，在 onCreate 中于主线程创建，随进程存活。 */
    lateinit var playbackGraph: PlaybackGraph
      private set
  }

  override fun attachBaseContext(base: Context?) {
    super.attachBaseContext(base)
    instance = this
  }

  @OptIn(ExperimentalFoundationApi::class)
  override fun onCreate() {
    super.onCreate()
    applyNightMode()
    // 必须早于 PlaybackService 与任何页面：ExoPlayer 需要在主线程创建
    playbackGraph = PlaybackGraph(this)
    // 构造完成后再恢复上次播放的媒体，避免协程拿到未构造完的依赖图
    playbackGraph.restoreLastMedia()
    connectPlaybackService()
    ComposeFoundationFlags.isInheritedTextStyleEnabled = true
  }

  /**
   * 把日夜模式同步到系统，供启动窗口与首帧上色。
   *
   * 模式开关保存在 DataStore，必须在任何界面创建之前读取并应用。
   */
  private fun applyNightMode() {
    AppCompatDelegate.setDefaultNightMode(
      if (runBlocking { DsManager.get(DsManager.KEY_NIGHT_MODE, true) }) {
        AppCompatDelegate.MODE_NIGHT_YES
      } else {
        AppCompatDelegate.MODE_NIGHT_NO
      }
    )
  }

  /** 确保播放服务已连接，重复调用无副作用。 */
  fun connectPlaybackService() {
    serviceConnection.connect()
  }

  override fun newImageLoader(context: PlatformContext): ImageLoader {
    return ImageLoader.Builder(context)
      .components {
        add(VideoFrameDecoder.Factory())
      }
      .crossfade(true)
      .build()
  }
}
