package com.language.repeater.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.language.repeater.playvideo.model.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

  /**
   * 插入或更新一条历史记录。
   * OnConflictStrategy.REPLACE 的意思是：
   * 如果数据库里已经有了这个 ID (同一个视频)，就用新的覆盖旧的。
   * 这完美实现了"更新播放进度"和"更新最后播放时间"的功能。
   */
  @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
  suspend fun insertOrUpdate(video: VideoEntity)

  /**
   * 查询所有历史记录。
   * ORDER BY lastPlayedTime DESC: 按时间倒序（最近播放的在最上面）。
   * 返回 Flow: 这是一个"活"的数据流。只要数据库有变化，UI 会自动收到最新列表。
   */
  @Query("SELECT * FROM history_table ORDER BY lastPlayedTime DESC")
  fun getAllHistory(): Flow<List<VideoEntity>>

  /**
   * 删除单条记录
   */
  @Delete
  suspend fun delete(video: VideoEntity)

  /**
   * 清空所有历史记录
   */
  @Query("DELETE FROM history_table")
  suspend fun clearAll()

  /**
   * 获取历史记录总数
   */
  @Query("SELECT COUNT(*) FROM history_table")
  suspend fun getCount(): Int

  /**
   * 删除最旧的记录 (按 lastPlayedTime 升序，删除第一条)
   */
  @Query("DELETE FROM history_table WHERE id = (SELECT id FROM history_table ORDER BY lastPlayedTime ASC LIMIT 1)")
  suspend fun deleteOldest()
}