package com.language.repeater.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.language.repeater.app.MainActivity
import com.language.repeater.app.MainApp
import com.language.repeater.playvideo.model.VideoEntity
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * Date: 2026-01-13
 * Time: 19:12
 * Description:
 */
object FileUtil {
  private const val TAG = "wangzixu_FileUtil"

  /**
   * 快速获取文件的元数据
   * @param context Context
   * @param uri 文件的 Uri
   * @return 包含文件名、大小的数据类，如果查询失败则返回 null
   */
  fun getFileInfo(context: Context, uri: Uri): VideoEntity {
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
    return VideoEntity(id, uri.toString(), name)
  }

  /**
   * 获取文件对应的名字
   */
  fun getDirUriName(context: Context, uri: String): String {
    var name = ""
    try {
      name = DocumentFile.fromTreeUri(context, uri.toUri())?.name ?: ""
      Log.i(TAG, "getDirUriName : $name")
    } catch (e: Exception) {
      Log.i(TAG, "getDirUriName error : ${e.message}")
      e.printStackTrace()
    }
    return name
  }

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

  // 核心方法：申请持久权限
  fun takePersistablePermission(context: Context, uri: Uri) {
    try {
      val contentResolver = context.contentResolver
      // 只申请读权限：ACTION_OPEN_DOCUMENT 系统只授予读权限，如果同时申请写权限会抛出 SecurityException，
      // 导致读权限也无法被持久化，App 重启后所有文件都访问不到
      val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
      contentResolver.takePersistableUriPermission(uri, takeFlags)
      // 成功后，你就可以把 uri.toString() 存入 Room 或 SharedPreferences 了
      // 下次直接用 Uri.parse(string) 就能播放
      Log.d(TAG, "takePersistablePermission success, uri: $uri")
    } catch (e: SecurityException) {
      e.printStackTrace()
      // 某些特殊云端文件可能不支持持久权限，这里要做异常处理
      Log.d(TAG, "takePersistablePermission failed:${e.message}, uri: $uri")
    }
  }

  fun releasePersistablePermission(uri: Uri) {
    try {
      val contentResolver = MainApp.instance.contentResolver
      val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
      contentResolver.releasePersistableUriPermission(uri, takeFlags)
      Log.d(TAG, "releasePersistablePermission success, uri: $uri")
    } catch (e: SecurityException) {
      e.printStackTrace()
      Log.d(TAG, "releasePersistablePermission failed:${e.message}, uri: $uri")
    }
  }

  fun checkPersistPermissionList() {
    try {
      val contentResolver = MainApp.instance.contentResolver
      val list = contentResolver.persistedUriPermissions
      Log.d(TAG, "checkPersistPermissionList size = ${list.size}")
      list.forEach {
        Log.d(TAG, "checkPersistPermissionList uri:${it.uri}")
      }
    } catch (e: SecurityException) {
      e.printStackTrace()
      Log.d(TAG, "releasePersistablePermission failed:${e.message}, uri: $uri")
    }
  }
}

