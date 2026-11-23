package com.language.repeater.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.language.repeater.R
import com.language.repeater.db.VideoInfoDao
import com.language.repeater.player.PlaybackPlayer
import com.language.repeater.utils.FileUtil
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class SubtitleController(
  context: Context,
  private val player: PlaybackPlayer,
  private val videoInfoDao: VideoInfoDao,
  private val scope: CoroutineScope
) {
  private val applicationContext = context.applicationContext

  /**
   * 更新当前媒体的字幕。
   *
   * 优先保留原始 Uri：能从 provider 拿到持久读权限（或位于已授权的字幕目录树下）时，
   * 不需要复制。provider 不支持持久授权时复制到应用私有目录，否则重启后字幕会失效。
   */
  fun updateSubtitle(subtitleUri: Uri) {
    val currentMedia = player.state.value.currentMedia ?: return
    val mediaId = currentMedia.mediaId
    val previousSubtitleUri = currentMedia.subtitleUri
    scope.launch(Dispatchers.IO) {
      try {
        val storedUri = prepareSubtitleUri(mediaId, subtitleUri)
        videoInfoDao.updateSubUri(mediaId, storedUri)
        if (player.state.value.currentMedia?.mediaId != mediaId) {
          return@launch
        }

        withContext(Dispatchers.Main) {
          player.setSubtitle(storedUri)
        }
        releaseUnusedSubtitlePermission(previousSubtitleUri, storedUri)
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

  /** 校验来源可读，并返回可以长期保存的字幕 Uri 字符串。 */
  private fun prepareSubtitleUri(mediaId: Long, source: Uri): String {
    FileUtil.takePersistablePermission(applicationContext, source)
    if (!canRead(source)) {
      throw IOException("Subtitle is not readable: $source")
    }

    val durable = source.scheme == "file" ||
      FileUtil.hasPersistedReadPermission(applicationContext, source)
    if (durable) {
      // 之前可能为同一媒体复制过私有副本，改用外部 uri 后不再需要。
      deletePrivateSubtitleCopies(mediaId, keep = null)
      return source.toString()
    }

    return copySubtitleToPrivateStorage(mediaId, source)
  }

  private fun canRead(uri: Uri): Boolean {
    return try {
      applicationContext.contentResolver.openInputStream(uri)?.use { true } ?: false
    } catch (_: Exception) {
      false
    }
  }

  /** provider 不支持持久授权时复制到私有目录，文件名按 mediaId 固定，换字幕时覆盖。 */
  private fun copySubtitleToPrivateStorage(mediaId: Long, source: Uri): String {
    val displayName = FileUtil.getFileUriName(applicationContext, source)
    val extension = displayName.substringAfterLast('.', "").lowercase()
      .takeIf { it == "srt" || it == "vtt" }
      ?: "srt"

    val directory = File(applicationContext.filesDir, SUBTITLE_DIRECTORY).apply { mkdirs() }
    val target = File(directory, "$mediaId.$extension")
    deletePrivateSubtitleCopies(mediaId, keep = target)

    val input = applicationContext.contentResolver.openInputStream(source)
      ?: throw IOException("Cannot open subtitle: $source")
    input.use { sourceStream ->
      target.outputStream().use { targetStream ->
        sourceStream.copyTo(targetStream)
      }
    }
    return Uri.fromFile(target).toString()
  }

  private fun deletePrivateSubtitleCopies(mediaId: Long, keep: File?) {
    val directory = File(applicationContext.filesDir, SUBTITLE_DIRECTORY)
    directory.listFiles()?.forEach { file ->
      if (file.nameWithoutExtension == mediaId.toString() && file != keep) {
        file.delete()
      }
    }
  }

  /** 旧字幕不再被任何媒体引用时释放它的持久授权，避免授权名额持续累积。 */
  private suspend fun releaseUnusedSubtitlePermission(previousUri: String?, currentUri: String) {
    if (previousUri.isNullOrBlank() || previousUri == currentUri) {
      return
    }
    val previous = previousUri.toUri()
    if (previous.scheme != "content") {
      return
    }
    if (videoInfoDao.countBySubUri(previousUri) > 0) {
      return
    }
    val persistedDirectly = applicationContext.contentResolver.persistedUriPermissions.any {
      it.isReadPermission && it.uri == previous
    }
    if (!persistedDirectly) {
      return
    }
    FileUtil.releasePersistablePermission(applicationContext, previous)
  }

  private companion object {
    const val TAG = "SubtitleController"
    const val SUBTITLE_DIRECTORY = "subtitles"
  }
}
