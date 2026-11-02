package com.language.repeater.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.language.repeater.R
import com.language.repeater.datastore.DsManager
import com.language.repeater.db.VideoEntity
import com.language.repeater.db.VideoInfoDao
import com.language.repeater.player.PlaybackPlayer
import com.language.repeater.utils.SubtitleFolderScanner
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubtitleController(
  context: Context,
  private val player: PlaybackPlayer,
  private val videoInfoDao: VideoInfoDao,
  private val scope: CoroutineScope
) {
  private val applicationContext = context.applicationContext

  fun updateSubtitle(subtitleUri: Uri) {
    val mediaId = player.state.value.currentMedia?.mediaId ?: return
    val subtitle = subtitleUri.toString()
    scope.launch(Dispatchers.IO) {
      try {
        videoInfoDao.updateSubUri(mediaId, subtitle)
        if (player.state.value.currentMedia?.mediaId != mediaId) {
          return@launch
        }

        withContext(Dispatchers.Main) {
          player.setSubtitle(subtitle)
        }
      } catch (throwable: Throwable) {
        if (throwable is CancellationException) {
          throw throwable
        }
        Log.e(TAG, "Failed to update subtitle: $mediaId", throwable)
        withContext(Dispatchers.Main) {
          ToastUtil.toast(R.string.subtitle_load_failed)
        }
      }
    }
  }

  /**
   * 为即将入库的媒体解析字幕。字幕跟着 VideoEntity.subUri 走：
   * uri 已在库中的条目沿用库里已保存的字幕；未入库的新文件按文件名去字幕文件夹找。
   * 结果直接写回 item.subUri，由仓库入库时一并落盘。
   */
  suspend fun resolveSubtitles(items: List<VideoEntity>) {
    if (items.isEmpty()) {
      return
    }
    val existingByUri = videoInfoDao.getVideosByUris(items.map { item -> item.uri })
      .associateBy({ entry -> entry.uri })
    var needScanFolder = false
    items.forEach { item ->
      val existing = existingByUri[item.uri]
      if (existing == null) {
        needScanFolder = true
      } else {
        item.subUri = existing.subUri
      }
    }

    if (!needScanFolder) {
      return
    }
    scanFolderForSubtitles(items)
  }

  private suspend fun scanFolderForSubtitles(items: List<VideoEntity>) {
    val folder = DsManager.get(DsManager.KEY_SUBTITLE_FOLDER, "")
      .takeIf { path -> path.isNotEmpty() }
      ?.toUri()
    val found = SubtitleFolderScanner.scan(applicationContext, folder)
    if (found.isEmpty()) {
      return
    }

    items.forEach { item ->
      if (item.subUri != null) {
        return@forEach
      }
      val key = item.name.substringBeforeLast(".")
      val subtitleUri = found[key] ?: return@forEach
      item.subUri = subtitleUri.toString()
    }
  }

  private companion object {
    const val TAG = "SubtitleController"
  }
}
