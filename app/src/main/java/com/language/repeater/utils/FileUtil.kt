package com.language.repeater.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile

/**
 * Date: 2026-01-13
 * Time: 19:12
 * Description:
 */
object FileUtil {
  private const val TAG = "wangzixu_FileUtil"

  /**
   * 获取文件对应的名字
   */
  fun getFileUriName(context: Context, uri: Uri): String {
    var name = ""
    try {
      name = DocumentFile.fromSingleUri(context, uri)?.name ?: ""
      Log.i(TAG, "getFileUriName : $name")
    } catch (e: Exception) {
      Log.i(TAG, "getFileUriName error : ${e.message}")
      e.printStackTrace()
    }
    return name
  }

  /**
   * 申请持久权限，用于字幕文件与字幕文件夹这类 Storage Access Framework 返回的 uri。
   *
   * 只申请读权限：ACTION_OPEN_DOCUMENT 系统只授予读权限，如果同时申请写权限会抛出 SecurityException，
   * 导致读权限也无法被持久化，App 重启后所有文件都访问不到
   */
  fun takePersistablePermission(context: Context, uri: Uri) {
    try {
      val contentResolver = context.contentResolver
      val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
      contentResolver.takePersistableUriPermission(uri, takeFlags)
      Log.d(TAG, "takePersistablePermission success, uri: $uri")
    } catch (e: SecurityException) {
      e.printStackTrace()
      // 某些特殊云端文件可能不支持持久权限，这里要做异常处理
      Log.d(TAG, "takePersistablePermission failed:${e.message}, uri: $uri")
    }
  }
}
