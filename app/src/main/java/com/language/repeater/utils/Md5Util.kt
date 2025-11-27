package com.language.repeater.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Date: 2025-10-27
 * Time: 20:23
 * Description:
 */
object Md5Util {
  /**
   * 计算给定 Uri 指向的文件内容的 MD5 值
   * 这是一个挂起函数，必须在协程中调用
   * @param context Context a
   * @param uri 文件的 Uri
   * @return 文件的 MD5 字符串，如果失败则返回 null
   */
  suspend fun getFileMD5(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
      // 1. 通过 ContentResolver 获取文件的输入流
      context.contentResolver.openInputStream(uri)?.use { inputStream ->
        // 2. 创建 MD5 摘要实例
        val md5Digest = MessageDigest.getInstance("MD5")

        // 3. 创建缓冲区并循环读取文件内容
        val buffer = ByteArray(8192) // 8KB buffer
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
          // 4. 更新摘要
          md5Digest.update(buffer, 0, bytesRead)
        }

        // 5. 完成计算并转换为 16 进制字符串
        val md5Bytes = md5Digest.digest()
        // 将字节数组转换为十六进制字符串
        return@withContext md5Bytes.joinToString("") { "%02x".format(it) }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      // 发生异常，返回 null
      return@withContext null
    }
    // 如果 inputStream 为 null，也返回 null
    return@withContext null
  }

// --- 如何使用 ---
// 在你的 ViewModel 或 Activity/Fragment 的 CoroutineScope 中调用
// viewModelScope.launch {
//     val fileUri = ... // 你通过文件选择器获取的 Uri
//     val md5 = getFileMD5(applicationContext, fileUri)
//     if (md5 != null) {
//         println("文件 MD5: $md5")
//     } else {
//         println("计算 MD5 失败")
//     }
// }


  data class FileMetadata(val displayName: String?, val size: Long, val lastModified: Long?)

  /**
   * 快速获取文件的元数据
   * @param context Context
   * @param uri 文件的 Uri
   * @return 包含文件名、大小和修改时间的数据类，如果查询失败则返回 null
   */
  fun getFileMetadata(context: Context, uri: Uri): FileMetadata? {
    // MediaStore.MediaColumns.DATE_MODIFIED 在某些设备上可能不存在于 OpenableColumns 查询中
    // 但我们可以尝试获取，不行就忽略
    val projection = arrayOf(
      OpenableColumns.DISPLAY_NAME,
      OpenableColumns.SIZE
       //MediaStore.MediaColumns.DATE_MODIFIED // 通常不在这里查询，但可以试试
    )

    try {
      context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

          val displayName = if (displayNameIndex != -1) cursor.getString(displayNameIndex) else "unknown"
          val size = if (sizeIndex != -1) cursor.getLong(sizeIndex) else -1

          // 获取修改时间通常需要额外的逻辑，但我们可以简化处理
          // 一个简单但有效的唯一键可以不包含修改时间
          return FileMetadata(displayName, size, null) // 简化版，通常也够用
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return null
  }

  private const val RANDOM_KEY_PREFIX = "random_key_"
  /**
   * 根据文件元数据生成一个快速、唯一的 Key
   * @param context Context
   * @param uri 文件的 Uri
   * @return 一个唯一标识字符串，如 "IMG_2023.jpg-1234567"
   */
  fun generateFastUniqueKey(context: Context, uri: Uri): String {
    val metadata = getFileMetadata(context, uri)
    val name = metadata?.displayName
      ?.trim()
      ?.replace(' ', '-')
    val size = metadata?.size ?: 0
    if (name == null) {
      Log.e("wangzixu", "Warn! Md5Util.generateFastUniqueKey is null!!")
      return (RANDOM_KEY_PREFIX + System.currentTimeMillis())
    }
    return "$name-$size"
  }

  fun isRandomKey(key: String): Boolean{
    return key.isEmpty() || key.startsWith(RANDOM_KEY_PREFIX)
  }

// --- 如何使用 ---
// 这个操作很快，可以直接在主线程调用
// val fileUri = ... // 你通过文件选择器获取的 Uri
// val uniqueKey = generateFastUniqueKey(applicationContext, fileUri)
// if (uniqueKey != null) {
//     println("快速唯一 Key: $uniqueKey")
// } else {
//     println("生成 Key 失败")
// }
}