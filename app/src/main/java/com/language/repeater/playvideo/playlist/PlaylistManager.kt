package com.language.repeater.playvideo.playlist

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.language.repeater.dataStore
import com.language.repeater.json
import com.language.repeater.playvideo.model.CurrentPlayVideoEntity
import com.language.repeater.playvideo.model.VideoEntity
import com.language.repeater.subtitleStore
import com.language.repeater.utils.DataStoreKey.KEY_CURRENT_PLAYLIST
import com.language.repeater.utils.DataStoreKey.KEY_CURRENT_PLAY_INFO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.text.trim

/**
 * Date: 2025-10-27
 * Time: 20:23
 * Description:
 */
object PlaylistManager {
  const val TAG = "wangzixu"

  /**
   * 快速获取文件的元数据
   * @param context Context
   * @param uri 文件的 Uri
   * @return 包含文件名、大小的数据类，如果查询失败则返回 null
   */
  fun getFileInfo(context: Context, uri: Uri): VideoEntity {
    var name: String? = null
    var size: Long = 0
    try {
      val projection = arrayOf(
        OpenableColumns.DISPLAY_NAME,
        OpenableColumns.SIZE
      )

      context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (displayNameIndex != -1) {
            name = cursor.getString(displayNameIndex)
          }

          val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (sizeIndex != -1) {
            size = cursor.getLong(sizeIndex)
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }

    if (name.isNullOrEmpty()) {
      name = uri.lastPathSegment
      Log.i(TAG, "getFileInfo, contentResolver null, use lastPathSegment:$name")
    }

    if (name.isNullOrEmpty()) {
      name = uri.path
      Log.i(TAG, "getFileInfo, contentResolver null, use path:$name")
    }

    if (name.isNullOrEmpty()) {
      name = uri.toString()
      Log.i(TAG, "getFileInfo, contentResolver null, use uri.toString:$name")
    }

    val trimName = name.trim()
      .replace(' ', '-') //后续ffmpeg命令里, 文件名不能带空格
      .replace('\'', '-') //不能带'符号
    val id = "$trimName-$size"

    return VideoEntity(id, uri.toString(), name, 0L, null)
  }

  /**
   * 保存当前播放器的列表到本地
   */
  suspend fun saveCurrentPlaylist(context: Context, items: List<MediaItem>) =
    withContext(Dispatchers.IO) {
      val list = items.map { item ->
        // 确保 uri 不为空，MediaItem 里的 uri 一般不会空
        val uri = item.localConfiguration?.uri.toString()
        val title = item.mediaMetadata.title?.toString() ?: "未知视频"

        // 提取字幕 Uri
        val subUri: String? = item.localConfiguration?.subtitleConfigurations?.firstOrNull()?.uri?.toString()
        val id = item.mediaId

        VideoEntity(id, uri, title, 0L, subUri)
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