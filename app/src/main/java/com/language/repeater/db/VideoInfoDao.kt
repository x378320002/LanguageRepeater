package com.language.repeater.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.language.repeater.playvideo.model.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoInfoDao {
  // 插入视频基础信息，如果已存在则忽略(IGNORE)或替换(REPLACE)
  @Upsert
  suspend fun insertOrUpdateInfo(video: VideoEntity)

  @Query("SELECT * FROM video_info_table WHERE id = :id")
  suspend fun getVideoById(id: String): VideoEntity?

  @Delete
  suspend fun delete(video: VideoEntity)

  @Query("DELETE FROM video_info_table WHERE id = :id")
  suspend fun deleteById(id: String): Int

  @Delete
  suspend fun deleteAll(videos: List<VideoEntity>)
}