package com.language.repeater.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.language.repeater.playbacklist.PlaybackListEntity
import com.language.repeater.playbacklist.PlaybackListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackListDao {
  @Query(
    """
      SELECT video_info_table.id AS mediaId,
             video_info_table.name AS title,
             video_info_table.uri AS uri,
             video_info_table.subUri AS subtitleUri,
             video_info_table.position AS savedPositionMs,
             playback_list_table.sortOrder AS sortOrder
      FROM playback_list_table
      INNER JOIN video_info_table
        ON playback_list_table.mediaId = video_info_table.id
      ORDER BY playback_list_table.sortOrder ASC, playback_list_table.mediaId ASC
    """
  )
  fun observeItems(): Flow<List<PlaybackListItem>>

  @Query(
    """
      SELECT video_info_table.id AS mediaId,
             video_info_table.name AS title,
             video_info_table.uri AS uri,
             video_info_table.subUri AS subtitleUri,
             video_info_table.position AS savedPositionMs,
             playback_list_table.sortOrder AS sortOrder
      FROM playback_list_table
      INNER JOIN video_info_table
        ON playback_list_table.mediaId = video_info_table.id
      WHERE playback_list_table.mediaId = :mediaId
      LIMIT 1
    """
  )
  suspend fun get(mediaId: String): PlaybackListItem?

  @Query(
    """
      SELECT video_info_table.id AS mediaId,
             video_info_table.name AS title,
             video_info_table.uri AS uri,
             video_info_table.subUri AS subtitleUri,
             video_info_table.position AS savedPositionMs,
             playback_list_table.sortOrder AS sortOrder
      FROM playback_list_table
      INNER JOIN video_info_table
        ON playback_list_table.mediaId = video_info_table.id
      ORDER BY playback_list_table.sortOrder ASC, playback_list_table.mediaId ASC
      LIMIT 1
    """
  )
  suspend fun first(): PlaybackListItem?

  @Query(
    """
      SELECT video_info_table.id AS mediaId,
             video_info_table.name AS title,
             video_info_table.uri AS uri,
             video_info_table.subUri AS subtitleUri,
             video_info_table.position AS savedPositionMs,
             playback_list_table.sortOrder AS sortOrder
      FROM playback_list_table
      INNER JOIN video_info_table
        ON playback_list_table.mediaId = video_info_table.id
      WHERE playback_list_table.sortOrder > :sortOrder
         OR (playback_list_table.sortOrder = :sortOrder AND playback_list_table.mediaId > :mediaId)
      ORDER BY playback_list_table.sortOrder ASC, playback_list_table.mediaId ASC
      LIMIT 1
    """
  )
  suspend fun next(sortOrder: Long, mediaId: String): PlaybackListItem?

  @Query(
    """
      SELECT video_info_table.id AS mediaId,
             video_info_table.name AS title,
             video_info_table.uri AS uri,
             video_info_table.subUri AS subtitleUri,
             video_info_table.position AS savedPositionMs,
             playback_list_table.sortOrder AS sortOrder
      FROM playback_list_table
      INNER JOIN video_info_table
        ON playback_list_table.mediaId = video_info_table.id
      WHERE playback_list_table.sortOrder < :sortOrder
         OR (playback_list_table.sortOrder = :sortOrder AND playback_list_table.mediaId < :mediaId)
      ORDER BY playback_list_table.sortOrder DESC, playback_list_table.mediaId DESC
      LIMIT 1
    """
  )
  suspend fun previous(sortOrder: Long, mediaId: String): PlaybackListItem?

  @Query("SELECT * FROM playback_list_table ORDER BY sortOrder ASC, mediaId ASC")
  suspend fun entities(): List<PlaybackListEntity>

  @Query("SELECT mediaId FROM playback_list_table WHERE mediaId IN (:mediaIds)")
  suspend fun existingMediaIds(mediaIds: List<String>): List<String>

  @Query("SELECT MIN(sortOrder) FROM playback_list_table")
  suspend fun minSortOrder(): Long?

  @Query("SELECT MAX(sortOrder) FROM playback_list_table")
  suspend fun maxSortOrder(): Long?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(items: List<PlaybackListEntity>)

  @Update
  suspend fun update(item: PlaybackListEntity)

  @Query("DELETE FROM playback_list_table WHERE mediaId = :mediaId")
  suspend fun remove(mediaId: String)

  @Query("DELETE FROM playback_list_table")
  suspend fun clear()
}
