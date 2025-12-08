package com.language.repeater

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import kotlinx.serialization.json.Json

val Context.subtitleStore: DataStore<Preferences> by preferencesDataStore(name = "video_subtitle_map")
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_config")

// 配置 Json 实例 (可选配置)
val json = Json {
  ignoreUnknownKeys = true // 如果以后增加了字段，旧版本数据读取时不崩溃
  encodeDefaults = true    // 确保默认值被写入
}

/**
 * Date: 2025-07-14
 * Time: 16:51
 * Description:
 */
class MyApp : Application(), SingletonImageLoader.Factory {
  companion object {
    lateinit var instance: MyApp
      private set
  }

  override fun onCreate() {
    super.onCreate()
    instance = this
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