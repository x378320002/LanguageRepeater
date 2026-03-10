package com.language.repeater.utils

import android.R
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.language.repeater.MyApp

object SubtitleUtils {
  fun createSubtitleConfig(context: Context, subUri: Uri): MediaItem.SubtitleConfiguration {
    val fileName = FileUtil.getFileUriName(context, subUri)
    val mimeType = getSubtitleMimeType(fileName)
    val info = guessLanguageFromFile(fileName)
    Log.i("wangzixu_SubtitleUtils", "createSubtitleConfig mimeType:$mimeType")
    return MediaItem.SubtitleConfiguration.Builder(subUri)
      .setMimeType(mimeType)
      .setLanguage(info.first) // 建议根据实际情况设置
      .setLabel(info.second)
      .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
      .build()
  }

  /**
   * 根据 Uri 智能获取字幕的 MimeType
   * 解决 SAF Uri 无法直接通过后缀名判断的问题
   */
  fun getSubtitleMimeType(fileName: String): String {
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

  fun guessLanguageFromFile(fileName: String): Pair<String, String> {
    val lower = fileName.lowercase()
    return when {
      lower.contains(".zh") || lower.contains("chinese") -> "zh" to "Chinese"
      lower.contains(".en") || lower.contains("english") -> "en" to "English"
      lower.contains(".fr") || lower.contains("french")  -> "fr" to "French"
      else -> "en" to "English"
    }
  }
}