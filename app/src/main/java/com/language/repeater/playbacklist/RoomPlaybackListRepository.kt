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

  override val items: Flow<List<VideoEntity>> = playbackListDao.observeItems()

  override suspend fun get(mediaId: Long): VideoEntity? {
    return playbackListDao.get(mediaId)
  }

  override suspend fun first(): VideoEntity? {
    return playbackListDao.first()
  }

  override suspend fun insertAndResolveIds(videos: List<VideoEntity>): List<VideoEntity> {
    if (videos.isEmpty()) {
      return emptyList()
    }

    return database.withTransaction {
      val resolved = mutableListOf<VideoEntity>()
      val seenUris = mutableSetOf<String>()
      videos.forEach { video ->
        // 同一批里的重复 uri 只保留第一条
        if (!seenUris.add(video.uri)) {
          return@forEach
        }
        val saved = videoInfoDao.getVideoByUri(video.uri)
        val entity = if (saved != null) {
          // 已在库中：沿用库存记录，避免重复导入覆盖掉已保存的字幕等字段
          saved
        } else {
          // 新文件：插入后拿自增 id，构造完整实体返回
          val id = videoInfoDao.insertOrUpdateInfo(video)
          video.copy(id = id)
        }
        resolved += entity
      }
      resolved
    }
  }

  override suspend fun saveOrder(order: List<Long>) {
    if (order.isEmpty()) {
      return
    }

    database.withTransaction {
      playbackListDao.upsert(
        order.mapIndexed { index, mediaId ->
          PlaybackListEntity(mediaId = mediaId, sortOrder = index.toLong())
        }
      )
    }
  }

  override suspend fun remove(mediaId: Long) {
    videoInfoDao.deleteById(mediaId)
  }
}
