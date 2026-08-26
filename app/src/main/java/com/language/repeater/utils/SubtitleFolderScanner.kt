package com.language.repeater.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** 扫描字幕目录的直属文件，并按不含扩展名的文件名建立索引。 */
object SubtitleFolderScanner {
  fun scan(
    context: Context,
    folderUri: Uri?
  ): Map<String, Uri> {
    if (folderUri == null) {
      return emptyMap()
    }

    val folder = DocumentFile.fromTreeUri(context, folderUri)
      ?: return emptyMap()
    val subtitles = mutableMapOf<String, Uri>()

    // 只扫描一层目录，避免大型目录树造成不可控的遍历开销。
    folder.listFiles().forEach { file ->
      val name = file.name ?: return@forEach
      val isSubtitle = name.endsWith(".srt", ignoreCase = true) ||
        name.endsWith(".vtt", ignoreCase = true)
      if (isSubtitle) {
        subtitles[name.substringBeforeLast(".")] = file.uri
      }
    }
    return subtitles
  }
}
