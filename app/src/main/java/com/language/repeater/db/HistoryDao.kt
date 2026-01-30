@file:Suppress("FunctionName")

package com.language.repeater.db

import android.util.Log
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.language.repeater.foundation.BasePlaySheetFragment.Companion.TAG
import com.language.repeater.playvideo.model.HistoryEntity
import com.language.repeater.playvideo.model.HistoryWithInfo
import com.language.repeater.playvideo.model.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

  // --- 内部方法，不要直接调用 ---
  @Upsert
  suspend fun _insertOrUpdate(history: HistoryEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun _insertOrIgnoreVideoInfo(video: VideoEntity)

  // --- 对外公开的方法 ---

  // 新增：根据 videoId 查找历史记录
  @Query("SELECT * FROM history_table WHERE videoId = :videoId LIMIT 1")
  suspend fun getHistoryByVideoId(videoId: String): HistoryEntity?

  /**
   * 【核心方法】保存播放进度。
   * 这是一个事务 (@Transaction)：
   * 1. 先尝试保存视频基础信息（如果在这个表里没有的话）。
   * 2. 再保存历史记录。
   * 这样保证了 foreign key 约束永远满足。
   */
  @Transaction
  suspend fun saveHistory(videoInfo: VideoEntity) {
    // 1. 确保基础表里有这个视频
    _insertOrIgnoreVideoInfo(videoInfo)

// 2. 关键步骤：先去查一下有没有旧记录！
    val existingHistory = getHistoryByVideoId(videoInfo.id)
    val now = System.currentTimeMillis()
    // 【核心逻辑】：如果有旧记录，必须使用 copy 并保留 existingHistory.historyId
    // 这样 Room 才知道你要更新的是这一行，而不是插新行
    val history = existingHistory?.copy(lastPlayedTime = now)
      ?: HistoryEntity(videoId = videoInfo.id, lastPlayedTime = now)

    Log.i(TAG, "history: saveHistory, time: ${history.lastPlayedTime}, title:${videoInfo.name}")
    _insertOrUpdate(history)
  }

  /**
   * 获取所有历史记录（带视频详情）
   */
  @Transaction // 多表查询必须加这个注解
  @Query("SELECT * FROM history_table ORDER BY lastPlayedTime DESC")
  fun getAllHistory(): Flow<List<HistoryWithInfo>>


  @Query("DELETE FROM history_table WHERE videoId = :videoId")
  suspend fun deleteByVideoId(videoId: String)

  /**
   * 删除单条记录
   */
  @Delete
  suspend fun delete(history: HistoryEntity)

  @Query("DELETE FROM history_table")
  suspend fun clearAll()

  /**
   * 获取历史记录总数
   */
  @Query("SELECT COUNT(*) FROM history_table")
  suspend fun getCount(): Int
}

//@Dao
//interface HistoryDao {
//
//  /**
//   * 插入或更新一条历史记录。
//   * OnConflictStrategy.REPLACE 的意思是：
//   * 如果数据库里已经有了这个 ID (同一个视频)，就用新的覆盖旧的。
//   * 这完美实现了"更新播放进度"和"更新最后播放时间"的功能。
//   */
//  @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
//  suspend fun insertOrUpdate(video: VideoEntity)
//
//  /**
//   * 查询所有历史记录。
//   * ORDER BY lastPlayedTime DESC: 按时间倒序（最近播放的在最上面）。
//   * 返回 Flow: 这是一个"活"的数据流。只要数据库有变化，UI 会自动收到最新列表。
//   */
//  @Query("SELECT * FROM history_table ORDER BY lastPlayedTime DESC")
//  fun getAllHistory(): Flow<List<VideoEntity>>
//
//  /**
//   * 删除单条记录
//   */
//  @Delete
//  suspend fun delete(video: VideoEntity)
//
//  /**
//   * 清空所有历史记录
//   */
//  @Query("DELETE FROM history_table")
//  suspend fun clearAll()
//
//  /**
//   * 获取历史记录总数
//   */
//  @Query("SELECT COUNT(*) FROM history_table")
//  suspend fun getCount(): Int
//
//  /**
//   * 删除最旧的记录 (按 lastPlayedTime 升序，删除第一条)
//   */
//  @Query("DELETE FROM history_table WHERE id = (SELECT id FROM history_table ORDER BY lastPlayedTime ASC LIMIT 1)")
//  suspend fun deleteOldest()
//}