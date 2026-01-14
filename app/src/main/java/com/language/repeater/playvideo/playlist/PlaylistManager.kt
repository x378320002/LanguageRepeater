package com.language.repeater.playvideo.playlist

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.MediaItem
import com.language.repeater.dataStore
import com.language.repeater.json
import com.language.repeater.playvideo.model.CurrentPlayVideoEntity
import com.language.repeater.playvideo.model.VideoEntity
import com.language.repeater.playvideo.model.toEntity
import com.language.repeater.subtitleStore
import com.language.repeater.utils.DataStoreKey.KEY_CURRENT_PLAYLIST
import com.language.repeater.utils.DataStoreKey.KEY_CURRENT_PLAY_INFO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Date: 2025-10-27
 * Time: 20:23
 * Description:
 */
object PlaylistManager {
  const val TAG = "wangzixu"

  /**
   * 保存当前播放器的列表到本地
   */
  suspend fun saveCurrentPlaylist(context: Context, items: List<MediaItem>) =
    withContext(Dispatchers.IO) {
      val list = items.map { item ->
        item.toEntity()
      }

      // 【修改】使用 Kotlinx Serialization 序列化
      val jsonString = json.encodeToString(list)
      context.dataStore.edit { prefs ->
        prefs[KEY_CURRENT_PLAYLIST] = jsonString
      }
    }

  /**
   * 读取本地列表并转换为 List<MediaItem>
   */
  suspend fun loadLastPlaylist(context: Context): List<VideoEntity> = withContext(Dispatchers.IO) {
    val jsonString = context.dataStore.data.map { prefs ->
      prefs[KEY_CURRENT_PLAYLIST]
    }.firstOrNull()

    if (jsonString.isNullOrEmpty()) return@withContext emptyList()

    return@withContext try {
      val entities: List<VideoEntity> = json.decodeFromString(jsonString)
      entities.forEach {
        if (it.subUri == null) {
          val prefKey = stringPreferencesKey(it.id)
          val preferences = context.subtitleStore.data.first()
          it.subUri = preferences[prefKey]
        }
      }
      entities
    } catch (e: Exception) {
      e.printStackTrace()
      emptyList()
    }
  }

  suspend fun saveCurrentPlayIndex(context: Context, info: CurrentPlayVideoEntity) = withContext(Dispatchers.IO) {
    context.dataStore.edit { prefs ->
      prefs[KEY_CURRENT_PLAY_INFO] = json.encodeToString(info)
    }
  }

  suspend fun loadCurrentPlayIndex(context: Context): CurrentPlayVideoEntity? =
    withContext(Dispatchers.IO) {
      val jsonString = context.dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_PLAY_INFO]
      }.firstOrNull()

      if (jsonString.isNullOrEmpty()) return@withContext null

      return@withContext try {
        val info = json.decodeFromString<CurrentPlayVideoEntity>(jsonString)
        info
      } catch (e: Exception) {
        e.printStackTrace()
        null
      }
    }
}