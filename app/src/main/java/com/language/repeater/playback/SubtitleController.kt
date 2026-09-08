package com.language.repeater.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.language.repeater.datastore.DsManager
import com.language.repeater.datastore.subtitleStore
import com.language.repeater.db.VideoInfoDao
import com.language.repeater.player.PlaybackPlayer
import com.language.repeater.playvideo.model.VideoEntity
import com.language.repeater.utils.SubtitleFolderScanner
import com.language.repeater.utils.ToastUtil
import com.language.repeater.utils.UriAccessUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 字幕协调器。
 *
 * 负责「哪个媒体用哪个字幕」这件事的全部：用户主动换字幕、导入新文件时自动配字幕、
 * 以及选择的持久化。句子怎么来（解析字幕还是跑静音检测）不归这里管，
 * 由 CurrentMediaSentenceController 决定。
 */
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
        saveSubtitle(mediaId, subtitle)
        // 保存期间用户可能已经切歌，此时不该把字幕挂到新媒体上
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
          ToastUtil.toast("加载字幕失败")
        }
      }
    }
  }

  /**
   * 为刚导入的媒体配字幕：先沿用上次为它选过的字幕，失效或从未选过就去字幕文件夹按文件名找。
   *
   * 直接写入传入条目的 subUri，调用方随后把它们存库即可。
   */
  suspend fun resolveSubtitles(items: List<VideoEntity>) {
    val preferences = applicationContext.subtitleStore.data.firstOrNull()
    var needScanFolder = false
    items.forEach { item ->
      val saved = preferences?.get(stringPreferencesKey(item.id))
      if (saved.isNullOrEmpty()) {
        needScanFolder = true
      } else if (UriAccessUtil.canRead(applicationContext, saved.toUri())) {
        item.subUri = saved
      } else {
        // 授权已失效，清掉映射，改去文件夹里重新找一份
        clearSubtitle(item.id)
        item.subUri = null
        needScanFolder = true
      }
    }

    if (!needScanFolder) {
      return
    }
    scanFolderForSubtitles(items)
  }

  /** 按「与媒体同名」的约定在用户指定的字幕文件夹里匹配。 */
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
      saveSubtitle(item.id, subtitleUri.toString())
    }
  }

  /**
   * 选择要记在两处：DataStore 供重新导入同一文件时查表复用，
   * VideoInfoDao 供播放列表恢复时随条目一起带出。
   *
   * 导入场景下媒体可能尚未入库，此时 updateSubUri 是空操作，
   * subUri 会随后续入库的条目一起写进去。
   */
  private suspend fun saveSubtitle(mediaId: String, subtitleUri: String) {
    applicationContext.subtitleStore.edit { preferences ->
      preferences[stringPreferencesKey(mediaId)] = subtitleUri
    }
    videoInfoDao.updateSubUri(mediaId, subtitleUri)
  }

  private suspend fun clearSubtitle(mediaId: String) {
    applicationContext.subtitleStore.edit { preferences ->
      preferences.remove(stringPreferencesKey(mediaId))
    }
  }

  private companion object {
    const val TAG = "SubtitleController"
  }
}
