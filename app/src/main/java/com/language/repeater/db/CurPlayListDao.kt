@file:Suppress("FunctionName")

package com.language.repeater.db
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.language.repeater.playvideo.model.CurPlayListEntity
import com.language.repeater.playvideo.model.CurPlayListItemWithInfo
import com.language.repeater.playvideo.model.VideoEntity
import kotlinx.coroutines.flow.Flow
import kotlin.collections.mapIndexed

@Dao
interface CurPlayListDao {

  // --- 内部基础方法 (Internal) ---

  // 批量插入视频信息（忽略已存在的）
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun _insertVideoInfos(videos: List<VideoEntity>)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun _insertVideoInfo(video: VideoEntity)

  @Upsert
  suspend fun _insertPlaylistItem(item: CurPlayListEntity)

  // 批量插入播放列表项 (极速)
  @Upsert
  suspend fun _insertPlaylistItems(items: List<CurPlayListEntity>)

  // 获取当前最大的序号 (用于插尾部)
  // COALESCE(..., 0) 保证空表时返回 0
  @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM current_list_table")
  suspend fun _getMaxSortOrder(): Int

  // 获取当前最小的序号 (用于插头部)
  // COALESCE(..., 0) 保证空表时返回 0
  @Query("SELECT COALESCE(MIN(sortOrder), 0) FROM current_list_table")
  suspend fun _getMinSortOrder(): Int

  // 清空列表
  @Query("DELETE FROM current_list_table")
  suspend fun _clearAll()

  // --- 对外公开的高级方法 (Public) ---

  /**
   * 1. 添加单个视频 (支持指定 头部 或 尾部)
   * @param addToHead true=加到开头; false=加到末尾(默认)
   */
  @Transaction
  suspend fun addToPlaylist(videoInfo: VideoEntity, addToHead: Boolean = false) {
    // 1. 确保视频元数据存在
    _insertVideoInfo(videoInfo)

    // 2. 计算新序号 (核心逻辑)
    val newOrder = if (addToHead) {
      // 加到头部：找最小的，减 1。
      // 比如当前是 0, 1, 2 -> 新增就是 -1。排序 -1, 0, 1, 2 依然正确。
      val minOrder = _getMinSortOrder()
      // 如果表是空的，min返回0，我们希望第一个也是0，所以需要特殊处理一下空表逻辑
      // 但为了代码简单，直接 min - 1 也没问题，只是第一个元素可能是 -1，不影响排序
      // 优化：如果表为空，设为 0；否则 min - 1
      val count = getCount()
      if (count == 0) 0 else minOrder - 1
    } else {
      // 加到尾部：找最大的，加 1
      val maxOrder = _getMaxSortOrder()
      val count = getCount()
      if (count == 0) 0 else maxOrder + 1
    }

    // 3. 插入
    val item = CurPlayListEntity(
      videoId = videoInfo.id,
      sortOrder = newOrder
    )
    _insertPlaylistItem(item)
  }

  /**
   * 2. 整体替换列表 (高性能版)
   * 场景：用户从“历史记录”或者“文件夹”里点击了“播放全部”
   */
  @Transaction
  suspend fun replacePlaylist(newVideos: List<VideoEntity>) {
    // 1. 先清空旧列表 (此时视频元数据 VideoInfoEntity 还在，只是删除了关系)
    _clearAll()

    if (newVideos.isEmpty()) return

    // 2. 批量保存视频元数据 (防止外键报错)
    // 这是一个 Batch 操作，比 for 循环快得多
    _insertVideoInfos(newVideos)

    // 3. 在内存中构建好 PlaylistEntity 列表，并分配序号
    // 既然是全新替换，我们可以把序号重置为 0, 1, 2, 3...
    val newPlaylistItems = newVideos.mapIndexed { index, video ->
      CurPlayListEntity(
        videoId = video.id,
        sortOrder = index // 重置序号，整整齐齐
      )
    }

    // 4. 批量插入播放列表
    _insertPlaylistItems(newPlaylistItems)
  }

  /**
   * 获取当前播放列表 (UI 监听用)
   */
  @Transaction
  @Query("SELECT * FROM current_list_table ORDER BY sortOrder ASC")
  fun getCurrentPlaylistFlow(): Flow<List<CurPlayListItemWithInfo>>

  @Transaction // 因为涉及 @Relation 多表查询，必须加 Transaction
  @Query("SELECT * FROM current_list_table ORDER BY sortOrder ASC")
  suspend fun getCurrentPlaylist(): List<CurPlayListItemWithInfo>

  @Transaction
  @Query("SELECT * FROM current_list_table ORDER BY sortOrder ASC")
  fun observePlaylist(): Flow<List<CurPlayListItemWithInfo>>

  // 辅助查询数量
  @Query("SELECT COUNT(*) FROM current_list_table")
  suspend fun getCount(): Int

  // 删除单个
  @Query("DELETE FROM current_list_table WHERE videoId = :videoId")
  suspend fun removeByVideoId(videoId: String)
}