package com.language.repeater.playvideo.playlist

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.MediaItem
import com.language.repeater.db.curPlayListDao
import com.language.repeater.db.videoInfoDao
import com.language.repeater.playvideo.model.VideoEntity
import com.language.repeater.playvideo.model.isPlaceHold
import com.language.repeater.playvideo.model.toEntity
import com.language.repeater.subtitleStore
import com.language.repeater.utils.UriAccessUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Date: 2025-10-27
 * Time: 20:23
 * Description:
 */
object PlaylistManager {
  const val TAG = "wangzixu_PlaylistManager"

  /**
   * 保存当前播放器的列表到本地
   */
  suspend fun saveCurrentPlaylist(context: Context, items: List<MediaItem>) = withContext(Dispatchers.IO) {
      val list = items.mapNotNull { item ->
        if (item.isPlaceHold()) {
          null
        } else {
          item.toEntity()
        }
      }

      context.curPlayListDao.replacePlaylist(list)
    }

  /**
   * 读取本地列表并转换为 List<MediaItem>
   */
  suspend fun loadLastPlaylist(context: Context): List<VideoEntity> = withContext(Dispatchers.IO) {
    val toDelete = mutableListOf<VideoEntity>()
    val list = context.curPlayListDao.getCurrentPlaylist().mapNotNull {
      //如果原文件没了, 删除本条记录
      val available = UriAccessUtil.canRead(context, it.videoInfo.uri.toUri())
      if (available) {
        //检查字幕文件是否还有效
        val subU = it.videoInfo.subUri
        if (subU != null && !subU.equals("null", true)) {
          val subAvailable = UriAccessUtil.canRead(context, subU.toUri())
          if (!subAvailable) {
            it.videoInfo.subUri = null
            launch {
              Log.i(TAG, "loadLastPlaylist 字幕文件找不到了: $subU")
              val prefKey = stringPreferencesKey(it.videoInfo.id)
              context.subtitleStore.edit { sp -> sp.remove(prefKey) }
              context.videoInfoDao.updateSubUri(it.videoInfo.id, null)
            }
          }
        }
        it.videoInfo
      } else {
        Log.i(TAG, "loadLastPlaylist 视频原文件找不到了: ${it.videoInfo.uri}")
        toDelete.add(it.videoInfo)
        null
      }
    }

    if (toDelete.isNotEmpty()) {
      launch(Dispatchers.IO) {
        context.videoInfoDao.deleteAll(toDelete)
      }
    }
    return@withContext list
  }
}