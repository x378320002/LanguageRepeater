package com.language.repeater.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import kotlinx.coroutines.DelicateCoroutinesApi

/**
 * Date: 2025-07-14
 * Time: 16:51
 * Description:
 */
class MainApp : Application(), SingletonImageLoader.Factory {
  companion object {
    lateinit var instance: MainApp
      private set
  }

  override fun attachBaseContext(base: Context?) {
    super.attachBaseContext(base)
    instance = this
  }

  @OptIn(DelicateCoroutinesApi::class)
  override fun onCreate() {
    super.onCreate()
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
