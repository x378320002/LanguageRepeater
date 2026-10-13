package com.language.repeater.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackListDao {
  @Query(
    """
      SELECT video_info_table.id,
             video_info_table.uri,
             video_info_table.name,
             video_info_table.subUri,
             video_info_table.isVideo,
             video_info_table.size,
             video_info_table.durationMs
      FROM playback_list_table
      INNER JOIN video_info_table
        ON playback_list_table.mediaId = video_info_table.id
      ORDER BY playback_list_table.sortOrder ASC, playback_list_table.mediaId ASC
    """
  )
  fun observeItems(): Flow<List<VideoEntity>>

  @Query(
    """
      SELECT video_info_table.id,
             video_info_table.uri,
             video_info_table.name,
             video_info_table.subUri,
             video_info_table.isVideo,
             video_info_table.size,
             video_info_table.durationMs
      FROM playback_list_table
      INNER JOIN video_info_table
        ON playback_list_table.mediaId = video_info_table.id
      WHERE playback_list_table.mediaId = :mediaId
      LIMIT 1
    """
  )
  suspend fun get(mediaId: Long): VideoEntity?

  @Query(
    """
      SELECT video_info_table.id,
             video_info_table.uri,
             video_info_table.name,
             video_info_table.subUri,
             video_info_table.isVideo,
             video_info_table.size,
             video_info_table.durationMs
      FROM playback_list_table
      INNER JOIN video_info_table
        ON playback_list_table.mediaId = video_info_table.id
      ORDER BY playback_list_table.sortOrder ASC, playback_list_table.mediaId ASC
      LIMIT 1
    """
  )
  suspend fun first(): VideoEntity?

  @Upsert
  suspend fun upsert(items: List<PlaybackListEntity>)
}
