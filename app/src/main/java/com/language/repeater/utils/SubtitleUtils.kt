package com.language.repeater.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MimeTypes

object SubtitleUtils {

  /**
   * 根据 Uri 智能获取字幕的 MimeType
   * 解决 SAF Uri 无法直接通过后缀名判断的问题
   */
  fun getSubtitleMimeType(context: Context, uri: Uri): String {
    val fileName = getFileName(context, uri) ?: ""

    return when {
      fileName.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
      fileName.endsWith(".ssa", ignoreCase = true) ||
          fileName.endsWith(".ass", ignoreCase = true) -> MimeTypes.TEXT_SSA
      fileName.endsWith(".ttml", ignoreCase = true) ||
          fileName.endsWith(".xml", ignoreCase = true) ||
          fileName.endsWith(".dfxp", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
      // 默认兜底为 SRT，因为这是最常见的格式
      else -> MimeTypes.APPLICATION_SUBRIP
    }
  }

  /**
   * 从 ContentResolver 查询真实文件名
   */
  private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null

    // 1. 尝试从 ContentProvider 查询
    if (uri.scheme == "content") {
      try {
        // 优化：只查询 DISPLAY_NAME 列，提高效率
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
          if (cursor.moveToFirst()) {
            // 尝试获取 DISPLAY_NAME
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index != -1) {
              result = cursor.getString(index)
            }
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }

    // 2. 如果查不到，或者是 file:// 协议，尝试从路径截取
    if (result == null) {
      result = uri.path
      val cut = result?.lastIndexOf('/')
      if (cut != null && cut != -1) {
        result = result?.substring(cut + 1)
      }
    }

    return result
  }
}