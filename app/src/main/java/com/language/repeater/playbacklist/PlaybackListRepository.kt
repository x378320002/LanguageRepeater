package com.language.repeater.playbacklist

import com.language.repeater.playvideo.model.VideoEntity
import kotlinx.coroutines.flow.Flow

/**
 * 播放列表的持久化出入口。只负责存取，条目怎么排序由调用方决定。
 *
 * [items] 是完整列表且随库变化自动更新，调用方的读取都基于它；
 * [get] 与 [first] 供 [items] 尚未加载时（进程启动）按需取单条。
 */
interface PlaybackListRepository {
  val items: Flow<List<PlaybackListItem>>

  suspend fun get(mediaId: String): PlaybackListItem?

  suspend fun first(): PlaybackListItem?

  /**
   * 按 [order] 给出的顺序整体落盘，同时把 [videos] 的视频信息写入库。
   *
   * [order] 为空表示只更新视频信息、不动现有顺序。
   */
  suspend fun saveOrder(order: List<String>, videos: List<VideoEntity> = emptyList())

  /**
   * 彻底删除这个视频，不是只把它移出列表。
   *
   * 删的是 video_info_table 的行，播放列表和历史记录都配了外键级联，会一起清掉。
   */
  suspend fun remove(mediaId: String)
}
