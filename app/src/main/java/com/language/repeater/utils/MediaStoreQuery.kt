package com.language.repeater.utils

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 直接查询 MediaStore 得到的媒体条目。
 *
 * [uri] 形如 content://media/external/video/media/123，读取权限来自 app 自身的媒体权限，
 * 不需要也不应该申请持久化 Uri 授权。
 */
data class MediaStoreItem(
  val uri: Uri,
  val name: String,
  val size: Long,
  val durationMs: Long,
  /** 入库时间，单位秒（MediaStore 的 DATE_ADDED 即秒），用于音视频混排 */
  val dateAddedSeconds: Long,
  /** 所在文件夹名（MediaStore 的 BUCKET_DISPLAY_NAME），为空表示未分类 */
  val folderName: String,
  val isVideo: Boolean
)

/**
 * 读取设备媒体库中的音视频。
 *
 * 加载媒体前检查完整音视频访问权限，授权结果以当前系统状态为准。
 * Android 12 及以下使用存储读取权限。
 */
object MediaStoreQuery {

  /** Android 14 显式参与选定视频授权流程，完整访问仍须检查 READ_MEDIA_VIDEO。 */
  val REQUIRED_PERMISSIONS: Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
      arrayOf(
        android.Manifest.permission.READ_MEDIA_VIDEO,
        android.Manifest.permission.READ_MEDIA_AUDIO
      )
    }
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
      arrayOf(
        android.Manifest.permission.READ_MEDIA_VIDEO,
        android.Manifest.permission.READ_MEDIA_AUDIO
      )
    }
    else -> {
      arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
  }

  /** 媒体库要求完整的音频和视频访问，选定视频授权不满足全库读取。 */
  fun hasPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      return isGranted(context, android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return isGranted(context, android.Manifest.permission.READ_MEDIA_AUDIO) &&
      isGranted(context, android.Manifest.permission.READ_MEDIA_VIDEO)
  }

  private fun isGranted(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
  }

  /**
   * 完整授权后查询音视频，按添加时间倒序；未授权时返回空列表。
   */
  suspend fun loadAll(context: Context): List<MediaStoreItem> = withContext(Dispatchers.IO) {
    if (!hasPermission(context)) {
      return@withContext emptyList()
    }
    val videos = queryCollection(
      context = context,
      collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
      isVideo = true
    )
    val audios = queryCollection(
      context = context,
      collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
      isVideo = false
    )
    (videos + audios).sortedByDescending { item -> item.dateAddedSeconds }
  }

  private fun queryCollection(
    context: Context,
    collection: Uri,
    isVideo: Boolean
  ): List<MediaStoreItem> {
    // 时长列对音频是毫秒、对视频也按毫秒返回，两类共用同一投影
    val projection = arrayOf(
      MediaStore.MediaColumns._ID,
      MediaStore.MediaColumns.DISPLAY_NAME,
      MediaStore.MediaColumns.SIZE,
      MediaStore.MediaColumns.DATE_ADDED,
      MediaStore.MediaColumns.DURATION,
      MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
    )
    val items = mutableListOf<MediaStoreItem>()

    try {
      context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.MediaColumns.DATE_ADDED} DESC"
      )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
        val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val folderIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)

        while (cursor.moveToNext()) {
          val name = cursor.getString(nameIndex) ?: continue
          val id = cursor.getLong(idIndex)
          items += MediaStoreItem(
            uri = ContentUris.withAppendedId(collection, id),
            name = name,
            size = cursor.getLong(sizeIndex),
            durationMs = cursor.getLong(durationIndex),
            dateAddedSeconds = cursor.getLong(dateAddedIndex),
            folderName = cursor.getString(folderIndex)?.trim().orEmpty(),
            isVideo = isVideo
          )
        }
      }
    } catch (e: Exception) {
      // 单个集合查询失败不应让另一类也拿不到，交给调用方按空列表处理
      e.printStackTrace()
    }
    return items
  }
}
