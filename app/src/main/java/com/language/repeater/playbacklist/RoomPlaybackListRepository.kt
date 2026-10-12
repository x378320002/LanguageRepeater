package com.language.repeater.playbacklist

import androidx.room.withTransaction
import com.language.repeater.db.AppDb
import com.language.repeater.db.PlaybackListEntity
import com.language.repeater.db.VideoEntity
import kotlinx.coroutines.flow.Flow

class RoomPlaybackListRepository(
  private val database: AppDb
) : PlaybackListRepository {
  private val playbackListDao = database.playbackListDao()
  private val videoInfoDao = database.videoInfoDao()

  override val items: Flow<List<PlaybackListItem>> = playbackListDao.observeItems()

  override suspend fun get(mediaId: String): PlaybackListItem? {
    return playbackListDao.get(mediaId)
  }

  override suspend fun first(): PlaybackListItem? {
    return playbackListDao.first()
  }

  /**
   * sortOrder 取条目在 [order] 中的下标，每次整体重写。
   *
   * 删除条目会在序号中留下空洞，不影响排序结果，下次重写时自动消除。
   */
  override suspend fun saveOrder(order: List<String>, videos: List<VideoEntity>) {
    if (order.isEmpty() && videos.isEmpty()) {
      return
    }

    database.withTransaction {
      if (videos.isNotEmpty()) {
        videoInfoDao.insertOrUpdateInfo(videos)
      }
      if (order.isNotEmpty()) {
        playbackListDao.upsert(
          order.mapIndexed { index, mediaId ->
            PlaybackListEntity(mediaId = mediaId, sortOrder = index.toLong())
          }
        )
      }
    }
  }

  override suspend fun remove(mediaId: String) {
    videoInfoDao.deleteById(mediaId)
  }
}
