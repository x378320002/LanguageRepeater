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
   * 获取文件对应的名字。
   *
   * SAF 的 document uri 走 DocumentFile 查询；file://（如复制到应用私有目录的字幕）
   * 无法构造 DocumentFile，退回路径最后一段。
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
    if (name.isEmpty()) {
      name = uri.lastPathSegment.orEmpty()
    }
    return name
  }

  /**
   * 申请持久权限，用于字幕文件与字幕文件夹这类 Storage Access Framework 返回的 uri。
   *
   * 只申请读权限：ACTION_OPEN_DOCUMENT 系统只授予读权限，如果同时申请写权限会抛出 SecurityException，
   * 导致读权限也无法被持久化，App 重启后所有文件都访问不到。
   *
   * @return true 表示该 uri 的读权限已持久化；false 表示 provider 不支持持久授权，调用方需自行兜底。
   */
  fun takePersistablePermission(context: Context, uri: Uri): Boolean {
    try {
      val contentResolver = context.contentResolver
      val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
      contentResolver.takePersistableUriPermission(uri, takeFlags)
      Log.d(TAG, "takePersistablePermission success, uri: $uri")
      return true
    } catch (e: SecurityException) {
      e.printStackTrace()
      // 某些特殊云端文件可能不支持持久权限，这里要做异常处理
      Log.d(TAG, "takePersistablePermission failed:${e.message}, uri: $uri")
      return false
    }
  }

  /**
   * 判断 uri 是否已有跨重启的读权限：要么单文件本身被持久授权，
   * 要么位于一个已持久授权的目录树下（DocumentFile 子文件不需要单独授权）。
   */
  fun hasPersistedReadPermission(context: Context, uri: Uri): Boolean {
    return try {
      val target = uri.toString()
      context.contentResolver.persistedUriPermissions.any { permission ->
        if (!permission.isReadPermission) {
          return@any false
        }
        if (permission.uri == uri) {
          return@any true
        }
        // 目录树授权覆盖树下的子 document uri
        val isTreePermission = permission.uri.pathSegments.firstOrNull() == "tree"
        isTreePermission && target.startsWith("${permission.uri}/")
      }
    } catch (e: Exception) {
      Log.d(TAG, "hasPersistedReadPermission error:${e.message}, uri: $uri")
      false
    }
  }

  /** 释放单个 uri 的持久读权限。 */
  fun releasePersistablePermission(context: Context, uri: Uri) {
    try {
      val contentResolver = context.contentResolver
      val releaseFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
      contentResolver.releasePersistableUriPermission(uri, releaseFlags)
      Log.d(TAG, "releasePersistablePermission success, uri: $uri")
    } catch (e: SecurityException) {
      Log.d(TAG, "releasePersistablePermission failed:${e.message}, uri: $uri")
    }
  }
}
