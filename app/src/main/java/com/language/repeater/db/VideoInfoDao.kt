package com.language.repeater.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface VideoInfoDao {
  @Upsert
  suspend fun insertOrUpdateInfo(video: VideoEntity): Long

  @Query("SELECT * FROM video_info_table WHERE id = :id")
  suspend fun getVideoById(id: Long): VideoEntity?

  @Query("SELECT * FROM video_info_table WHERE uri = :uri LIMIT 1")
  suspend fun getVideoByUri(uri: String): VideoEntity?

  @Delete
  suspend fun delete(video: VideoEntity)

  @Query("DELETE FROM video_info_table WHERE id = :id")
  suspend fun deleteById(id: Long): Int

  @Delete
  suspend fun deleteAll(videos: List<VideoEntity>)

  @Query("UPDATE video_info_table SET subUri = :subUri WHERE id = :videoId")
  suspend fun updateSubUri(videoId: Long, subUri: String?)

  @Query("SELECT COUNT(*) FROM video_info_table WHERE subUri = :subUri")
  suspend fun countBySubUri(subUri: String): Int
}
