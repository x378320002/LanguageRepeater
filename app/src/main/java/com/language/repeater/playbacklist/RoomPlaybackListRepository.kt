package com.language.repeater.playbacklist

import androidx.room.withTransaction
import com.language.repeater.db.AppDb
import com.language.repeater.playvideo.model.VideoEntity
import kotlinx.coroutines.flow.Flow

class RoomPlaybackListRepository(
  private val database: AppDb
) : PlaybackListRepository {
  private val playbackListDao = database.playbackListDao()
  private val videoInfoDao = database.videoInfoDao()

  override val items: Flow<List<PlaybackListItem>> = playbackListDao.observeItems()

  override suspend fun replace(items: List<VideoEntity>) {
    val uniqueItems = items.distinctBy { item -> item.id }
    val entities = uniqueItems.mapIndexed { index, item ->
      PlaybackListEntity(
        mediaId = item.id,
        sortOrder = index.toLong() * SORT_ORDER_STEP
      )
    }

    database.withTransaction {
      if (uniqueItems.isNotEmpty()) {
        videoInfoDao.insertOrUpdateInfo(uniqueItems)
      }
      playbackListDao.clear()
      if (entities.isNotEmpty()) {
        playbackListDao.insert(entities)
      }
    }
  }

  override suspend fun addToFront(items: List<VideoEntity>) {
    val uniqueItems = items.distinctBy { item -> item.id }
    if (uniqueItems.isEmpty()) {
      return
    }

    database.withTransaction {
      videoInfoDao.insertOrUpdateInfo(uniqueItems)
      val existingIds = playbackListDao.existingMediaIds(
        uniqueItems.map { item -> item.id }
      ).toHashSet()
      val newItems = uniqueItems.filterNot { item -> item.id in existingIds }
      if (newItems.isEmpty()) {
        return@withTransaction
      }

      val minimumSortOrder = ensureFrontCapacity(newItems.size)
      val firstSortOrder = if (minimumSortOrder == null) {
        0L
      } else {
        minimumSortOrder - newItems.size.toLong() * SORT_ORDER_STEP
      }
      playbackListDao.insert(
        newItems.mapIndexed { index, item ->
          PlaybackListEntity(
            mediaId = item.id,
            sortOrder = firstSortOrder + index.toLong() * SORT_ORDER_STEP
          )
        }
      )
    }
  }

  override suspend fun addToEnd(items: List<VideoEntity>) {
    val uniqueItems = items.distinctBy { item -> item.id }
    if (uniqueItems.isEmpty()) {
      return
    }

    database.withTransaction {
      videoInfoDao.insertOrUpdateInfo(uniqueItems)
      val existingIds = playbackListDao.existingMediaIds(
        uniqueItems.map { item -> item.id }
      ).toHashSet()
      val newItems = uniqueItems.filterNot { item -> item.id in existingIds }
      if (newItems.isEmpty()) {
        return@withTransaction
      }

      val maximumSortOrder = ensureEndCapacity(newItems.size)
      val firstSortOrder = if (maximumSortOrder == null) {
        0L
      } else {
        maximumSortOrder + SORT_ORDER_STEP
      }
      playbackListDao.insert(
        newItems.mapIndexed { index, item ->
          PlaybackListEntity(
            mediaId = item.id,
            sortOrder = firstSortOrder + index.toLong() * SORT_ORDER_STEP
          )
        }
      )
    }
  }

  override suspend fun remove(mediaId: String) {
    playbackListDao.remove(mediaId)
  }

  override suspend fun moveToFirst(mediaId: String) {
    database.withTransaction {
      val entities = playbackListDao.entities()
      val moving = entities.firstOrNull { item -> item.mediaId == mediaId }
        ?: return@withTransaction
      if (entities.firstOrNull()?.mediaId == mediaId) {
        return@withTransaction
      }

      // 移动操作统一重排，避免反复插值导致相邻项之间没有可用序号。
      rebalance(listOf(moving) + entities.filterNot { item -> item.mediaId == mediaId })
    }
  }

  override suspend fun moveBefore(mediaId: String, targetMediaId: String) {
    if (mediaId == targetMediaId) {
      return
    }

    database.withTransaction {
      val entities = playbackListDao.entities()
      val moving = entities.firstOrNull { item -> item.mediaId == mediaId }
        ?: return@withTransaction
      val reordered = entities
        .filterNot { item -> item.mediaId == mediaId }
        .toMutableList()
      val targetIndex = reordered.indexOfFirst { item -> item.mediaId == targetMediaId }
      if (targetIndex < 0) {
        return@withTransaction
      }

      reordered.add(targetIndex, moving)
      // 移动操作统一重排，保证复合游标的顺序稳定。
      rebalance(reordered)
    }
  }

  override suspend fun get(mediaId: String): PlaybackListItem? {
    return playbackListDao.get(mediaId)
  }

  override suspend fun first(): PlaybackListItem? {
    return playbackListDao.first()
  }

  override suspend fun nextOf(mediaId: String): PlaybackListItem? {
    val current = playbackListDao.get(mediaId) ?: return null
    return playbackListDao.next(current.sortOrder, current.mediaId)
  }

  override suspend fun previousOf(mediaId: String): PlaybackListItem? {
    val current = playbackListDao.get(mediaId) ?: return null
    return playbackListDao.previous(current.sortOrder, current.mediaId)
  }

  private suspend fun ensureFrontCapacity(newItemCount: Int): Long? {
    val minimumSortOrder = playbackListDao.minSortOrder() ?: return null
    val requiredCapacity = newItemCount.toLong() * SORT_ORDER_STEP
    if (minimumSortOrder >= Long.MIN_VALUE + requiredCapacity) {
      return minimumSortOrder
    }

    // 仅在 Long 下界确实耗尽时重排，日常追加不会读取或更新全量列表。
    val entities = playbackListDao.entities()
    rebalance(entities)
    return if (entities.isEmpty()) {
      null
    } else {
      0L
    }
  }

  private suspend fun ensureEndCapacity(newItemCount: Int): Long? {
    val maximumSortOrder = playbackListDao.maxSortOrder() ?: return null
    val requiredCapacity = newItemCount.toLong() * SORT_ORDER_STEP
    if (maximumSortOrder <= Long.MAX_VALUE - requiredCapacity) {
      return maximumSortOrder
    }

    // 仅在 Long 上界确实耗尽时重排，避免新增项的序号发生溢出。
    val entities = playbackListDao.entities()
    rebalance(entities)
    return if (entities.isEmpty()) {
      null
    } else {
      (entities.size - 1).toLong() * SORT_ORDER_STEP
    }
  }

  private suspend fun rebalance(items: List<PlaybackListEntity>) {
    items.forEachIndexed { index, item ->
      playbackListDao.update(
        item.copy(sortOrder = index.toLong() * SORT_ORDER_STEP)
      )
    }
  }

  private companion object {
    const val SORT_ORDER_STEP = 1024L
  }
}
