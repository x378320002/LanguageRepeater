package com.language.repeater.utils

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 与 Uri 可访问性相关的工具方法。
 *
 * 注意：subtitle Uri 多来自 Storage Access Framework (DocumentProvider)。
 * 仅凭字符串无法保证文件仍存在/仍有权限，因此在使用前应做一次可读性校验。
 */
object UriAccessUtil {

  /**
   * 判断 Uri 是否仍可读取。
   *
   * @return true 表示可以通过 ContentResolver 打开；false 表示已失效/无权限。
   */
  suspend fun canRead(context: Context, uri: Uri?): Boolean = withContext(Dispatchers.IO) {
    if (uri == null) return@withContext false
    return@withContext try {
      context.contentResolver.openInputStream(uri)?.use {
        true
      } ?: false
    } catch (_: Exception) {
      false
    }
  }
}
