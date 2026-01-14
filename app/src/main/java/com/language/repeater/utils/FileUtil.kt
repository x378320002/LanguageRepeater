package com.language.repeater.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.language.repeater.playvideo.model.VideoEntity
import com.language.repeater.playvideo.playlist.PlaylistManager
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
  private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
  private const val partSize = 4 * 1024L * 1024L

  private fun generateMediaKey(context: Context, uri: Uri): String? {
    try {
      val key = partialMd5ByFd(context, uri)
      Log.i(TAG, "generateMediaKey partialMd5ByFd: $key ")
      return key
    } catch (e: Exception) {
      e.printStackTrace()
      Log.i(TAG, "generateMediaKey partialMd5ByFd exception: ${e.message}")
    }

    try {
      val key = partialMd5ByStream(context, uri)
      Log.i(TAG, "generateMediaKey partialMd5ByStream: $key ")
      return key
    } catch (e: Exception) {
      e.printStackTrace()
      Log.i(TAG, "generateMediaKey partialMd5ByStream exception: ${e.message}")
    }

    return null
  }

  private fun partialMd5ByFd(context: Context, uri: Uri, ): String {
    val md = MessageDigest.getInstance("MD5")


    val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalStateException("Cannot open FD")

    pfd.use { fd ->
      FileInputStream(fd.fileDescriptor).use { fis ->
        val channel = fis.channel
        val fileSize = channel.size()

        // 小文件：直接 hash 全文件，避免重复
        if (fileSize <= partSize * 2) {
          readChannelFully(channel, md)
        } else {
          // head
          readChannelRange(channel, 0, partSize, md)
          // tail
          readChannelRange(channel, fileSize - partSize, partSize, md)
        }

        // 把 size 纳入 hash，增强区分度
        md.update(fileSize.toString().toByteArray())
      }
    }

    return "MD5_H4T4_${md.digest().toHex()}"
  }

  private fun readChannelRange(channel: FileChannel, start: Long, length: Long, md: MessageDigest) {
    val buffer = ByteArray(8 * 1024)
    var remaining = length

    channel.position(start)

    while (remaining > 0) {
      val toRead = minOf(buffer.size.toLong(), remaining).toInt()
      val read = channel.read(ByteBuffer.wrap(buffer, 0, toRead))
      if (read == -1) break

      md.update(buffer, 0, read)
      remaining -= read
    }
  }

  private fun readChannelFully(
    channel: FileChannel,
    md: MessageDigest,
  ) {
    channel.position(0)
    val buffer = ByteArray(8 * 1024)

    while (true) {
      val read = channel.read(ByteBuffer.wrap(buffer))
      if (read == -1) break
      md.update(buffer, 0, read)
    }
  }

  private fun partialMd5ByStream(context: Context, uri: Uri): String {
    val md = MessageDigest.getInstance("MD5")
    var totalRead = 0
    context.contentResolver.openInputStream(uri)?.use { input ->
      val buffer = ByteArray(8 * 1024)
      while (totalRead < partSize) {
        val toRead = minOf(buffer.size, partSize.toInt() - totalRead)
        val read = input.read(buffer, 0, toRead)
        if (read == -1) break

        md.update(buffer, 0, read)
        totalRead += read
      }
    } ?: throw IllegalStateException("Cannot open stream")

    val size = querySize(context, uri)
    if (size >= 0) {
      md.update(size.toString().toByteArray())
    }

    return "MD5_H4_${md.digest().toHex()}"
  }

  private fun querySize(context: Context, uri: Uri): Long {
    context.contentResolver.query(
      uri,
      arrayOf(OpenableColumns.SIZE),
      null,
      null,
      null
    )?.use { cursor ->
      if (cursor.moveToFirst()) {
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (index >= 0) return cursor.getLong(index)
      }
    }
    return -1L
  }


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
      Log.i(PlaylistManager.TAG, "getFileInfo, contentResolver null, use lastPathSegment:$name")
    }

    if (name.isNullOrEmpty()) {
      name = uri.path
      Log.i(PlaylistManager.TAG, "getFileInfo, contentResolver null, use path:$name")
    }

    if (name.isNullOrEmpty()) {
      name = uri.toString()
      Log.i(PlaylistManager.TAG, "getFileInfo, contentResolver null, use uri.toString:$name")
    }

    var id = generateMediaKey(context, uri)
    if (id.isNullOrEmpty()) {
      val trimName = name.trim()
        .replace(' ', '-') //后续ffmpeg命令里, 文件名不能带空格
        .replace('\'', '-') //不能带'符号
      id = "$trimName-$size"
    }

    return VideoEntity(id, uri.toString(), name, 0L, null)
  }
}

