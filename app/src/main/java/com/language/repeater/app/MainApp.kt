package com.language.repeater.app

import android.app.Application
import android.content.Context
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.language.repeater.playback.PlaybackGraph
import com.language.repeater.player.media3.PlaybackServiceConnection

/**
 * Date: 2025-07-14
 * Time: 16:51
 * Description:
 */
class MainApp : Application(), SingletonImageLoader.Factory {
  val playbackGraph: PlaybackGraph by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    PlaybackGraph(this)
  }

  /** Media3 平台连接，负责启动并维持 PlaybackService。 */
  private val serviceConnection: PlaybackServiceConnection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    PlaybackServiceConnection(this)
  }

  companion object {
    lateinit var instance: MainApp
      private set
  }

  override fun attachBaseContext(base: Context?) {
    super.attachBaseContext(base)
    instance = this
  }

  @OptIn(ExperimentalFoundationApi::class)
  override fun onCreate() {
    super.onCreate()
    serviceConnection.connect()
    ComposeFoundationFlags.isInheritedTextStyleEnabled = true
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
