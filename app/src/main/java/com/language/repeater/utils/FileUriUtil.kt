package com.language.repeater.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.text.trim

data class MyFileInfo(val uri: Uri, val name: String, val size: Long, val id: String, var subUri: Uri? = null)

/**
 * Date: 2025-10-27
 * Time: 20:23
 * Description:
 */
object FileUriUtil {
  const val TAG = "wangzixu"
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

  private const val RANDOM_KEY_PREFIX = "random_key_"

  /**
   * 快速获取文件的元数据
   * @param context Context
   * @param uri 文件的 Uri
   * @return 包含文件名、大小的数据类，如果查询失败则返回 null
   */
  fun getFileInfo(context: Context, uri: Uri): MyFileInfo {
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

    return MyFileInfo(uri, name, size, id)
  }

  /**
   * 根据文件元数据生成一个快速、唯一的 Key
   * @param context Context
   * @param uri 文件的 Uri
   * @return 一个唯一标识字符串，如 "IMG_2023.jpg-1234567"
   */
  fun generateFastUniqueKey(context: Context, uri: Uri): String {
    val metadata = getFileInfo(context, uri)
    val name = metadata.name
      .trim()
      .replace(' ', '-') //后续ffmpeg命令里, 文件名不能带空格
      .replace('\'', '-') //不能带'符号
    val size = metadata?.size ?: 0
//    if (name == null) {
//      Log.e(TAG, "Warn! Md5Util.generateFastUniqueKey is null!!")
//      return (RANDOM_KEY_PREFIX + System.currentTimeMillis())
//    }
    return "$name-$size"
  }

  fun isRandomKey(key: String): Boolean{
    return key.isEmpty() || key.startsWith(RANDOM_KEY_PREFIX)
  }
}