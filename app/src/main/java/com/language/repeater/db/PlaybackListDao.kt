package com.language.repeater.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.language.repeater.playbacklist.PlaybackListEntity
import com.language.repeater.playbacklist.PlaybackListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackListDao {
  /** 按播放顺序排列的完整列表。取相邻条目由调用方在这份列表上做下标运算。 */
  @Query(
    """
      SELECT video_info_table.id AS mediaId,
             video_info_table.name AS title,
             video_info_table.uri AS uri,
             video_info_table.subUri AS subtitleUri
      FROM playback_list_table
      INNER JOIN video_info_table
        ON playback_list_table.mediaId = video_info_table.id
      ORDER BY playback_list_table.sortOrder ASC, playback_list_table.mediaId ASC
    """
  )
  fun observeItems(): Flow<List<PlaybackListItem>>

  /** 按 id 取单条，供 [observeItems] 尚未加载完时使用。 */
  @Query(
    """
      SELECT video_info_table.id AS mediaId,
             video_info_table.name AS title,
             video_info_table.uri AS uri,
             video_info_table.subUri AS subtitleUri
      FROM playback_list_table
      INNER JOIN video_info_table
        ON playback_list_table.mediaId = video_info_table.id
      WHERE playback_list_table.mediaId = :mediaId
      LIMIT 1
    """
  )
  suspend fun get(mediaId: String): PlaybackListItem?

  /** 取播放顺序的首条，供 [observeItems] 尚未加载完时使用。 */
  @Query(
    """
      SELECT video_info_table.id AS mediaId,
             video_info_table.name AS title,
             video_info_table.uri AS uri,
             video_info_table.subUri AS subtitleUri
      FROM playback_list_table
      INNER JOIN video_info_table
        ON playback_list_table.mediaId = video_info_table.id
      ORDER BY playback_list_table.sortOrder ASC, playback_list_table.mediaId ASC
      LIMIT 1
    """
  )
  suspend fun first(): PlaybackListItem?

  /**
   * 新增条目与更新 sortOrder 都走这里。
   *
   * Upsert 在主键冲突时执行 UPDATE。若换成 INSERT OR REPLACE，冲突行会先被
   * DELETE 再插入，从而触发本表上的删除级联与触发器。
   */
  @Upsert
  suspend fun upsert(items: List<PlaybackListEntity>)
}
