package com.language.repeater.utils

import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coil Fetcher，从 MediaStore 加载视频缩略图。
 *
 * Android 10+ 用 [android.content.ContentResolver.loadThumbnail]，走系统预生成的缩略图缓存，
 * 比解码视频抽帧开销低；更早版本退回 [MediaStore.Video.Thumbnails]。
 *
 * 音频没有缩略图，直接返回 null，由调用方下层的图标兜底。
 * 取不到缩略图时同样返回 null，Coil 不绘制任何内容。
 */
class MediaStoreThumbnailFetcher(
    private val data: MediaStoreItem,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        if (!data.isVideo) {
            return@withContext null
        }

        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                options.context.contentResolver.loadThumbnail(
                    data.uri,
                    Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE),
                    null
                )
            } catch (e: Exception) {
                null
            }
        } else {
            loadLegacyThumbnail()
        }

        if (bitmap == null) {
            return@withContext null
        }

        ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    @Suppress("DEPRECATION")
    private fun loadLegacyThumbnail(): android.graphics.Bitmap? {
        return MediaStore.Video.Thumbnails.getThumbnail(
            options.context.contentResolver,
            ContentUris.parseId(data.uri),
            MediaStore.Video.Thumbnails.MINI_KIND,
            null
        )
    }

    class Factory : Fetcher.Factory<MediaStoreItem> {
        override fun create(
            data: MediaStoreItem,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return MediaStoreThumbnailFetcher(data, options)
        }
    }

    companion object {
        private const val THUMBNAIL_SIZE = 96
    }
}
