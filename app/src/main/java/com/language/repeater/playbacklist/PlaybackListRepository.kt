package com.language.repeater.playbacklist

import com.language.repeater.db.VideoEntity
import kotlinx.coroutines.flow.Flow

interface PlaybackListRepository {
  val items: Flow<List<VideoEntity>>

  suspend fun get(mediaId: Long): VideoEntity?

  suspend fun first(): VideoEntity?

  /**
   * 落库并返回每个条目在库中的完整记录（已含自增 id）。
   *
   * 库中已有相同 uri 的条目直接沿用库存记录，不重复插入；不存在的才插入并回填自增 id。
   * 返回顺序与 [videos] 一一对应，重复 uri 只保留首个。
   */
  suspend fun insertAndResolveIds(videos: List<VideoEntity>): List<VideoEntity>

  suspend fun saveOrder(order: List<Long>)

  suspend fun remove(mediaId: Long)
}
