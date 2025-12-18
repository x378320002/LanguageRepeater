package com.language.repeater.playcore

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 使用 Coil 实现的 BitmapLoader，用于 Media3 通知栏封面加载。
 * 它支持从视频 Uri 中提取封面。
 */
@UnstableApi
class CoilBitmapLoader(private val context: Context) : BitmapLoader {

  // 使用 IO 协程作用域来执行加载
  private val scope = CoroutineScope(Dispatchers.IO)

  override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
    val future = SettableFuture.create<Bitmap>()
    scope.launch {
      try {
        val request = ImageRequest.Builder(context)
          .data(uri)
          // 关键：通知栏的 Bitmap 不能使用 Hardware Config，必须是 Software
          .allowHardware(false)
          // 既然是封面，不需要太大，限制一下尺寸可以提升性能
          .size(512, 512)
          .build()

        val result = context.imageLoader.execute(request)

        if (result.image != null) {
          // 获取 Bitmap
          val bitmap = (result.image as BitmapImage).bitmap
          future.set(bitmap)
        } else {
          future.setException(Exception("Coil loaded image is null"))
        }
      } catch (e: Exception) {
        future.setException(e)
      }
    }

    return future
  }

  override fun supportsMimeType(mimeType: String): Boolean {
    // Coil 支持几乎所有类型，包括视频
    return true
  }

  override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
    val future = SettableFuture.create<Bitmap>()
    scope.launch {
      try {
        val request = ImageRequest.Builder(context)
          .data(data)
          // 关键：通知栏的 Bitmap 不能使用 Hardware Config，必须是 Software
          .allowHardware(false)
          // 既然是封面，不需要太大，限制一下尺寸可以提升性能
          .size(512, 512)
          .build()

        val result = context.imageLoader.execute(request)

        if (result.image != null) {
          // 获取 Bitmap
          val bitmap = (result.image as BitmapImage).bitmap
          future.set(bitmap)
        } else {
          future.setException(Exception("Coil loaded image is null"))
        }
      } catch (e: Exception) {
        future.setException(e)
      }
    }

    return future
  }
}