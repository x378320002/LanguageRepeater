package com.language.repeater.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface VideoInfoDao {
  // 插入视频基础信息，如果已存在则忽略(IGNORE)或替换(REPLACE)
  @Upsert
  suspend fun insertOrUpdateInfo(video: VideoEntity)

  @Upsert
  suspend fun insertOrUpdateInfo(videos: List<VideoEntity>)

  @Query("SELECT * FROM video_info_table WHERE id = :id")
  suspend fun getVideoById(id: String): VideoEntity?

  @Delete
  suspend fun delete(video: VideoEntity)

  @Query("DELETE FROM video_info_table WHERE id = :id")
  suspend fun deleteById(id: String): Int

  @Delete
  suspend fun deleteAll(videos: List<VideoEntity>)

  @Query("UPDATE video_info_table SET subUri = :subUri WHERE id = :videoId")
  suspend fun updateSubUri(videoId: String, subUri: String?)

  @Query("UPDATE video_info_table SET uri = :uri WHERE id = :videoId")
  suspend fun updateUri(videoId: String, uri: String)
}