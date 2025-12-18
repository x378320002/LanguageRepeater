package com.language.repeater.playvideo.history

import kotlinx.coroutines.Dispatchers
import android.content.Context
import com.language.repeater.db.AppDatabase
import com.language.repeater.playvideo.model.VideoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

object HistoryManager {

  // 获取数据库实例的 DAO
  private fun getDao(context: Context) = AppDatabase.instance(context).historyDao()

  /**
   * 添加进历史记录 (自动更新时间)
   */
  suspend fun addHistory(context: Context, video: VideoEntity) = withContext(Dispatchers.IO) {
    // 覆盖原本的时间，更新为当前时间，这样它就会排到第一位
    val newRecord = video.copy(lastPlayedTime = System.currentTimeMillis())
    getDao(context).insertOrUpdate(newRecord)
  }

  /**
   * 获取历史记录列表 (Flow 流式数据)
   * 在 UI 层使用 lifecycleScope.launch { flow.collect { list -> ... } } 监听
   */
  fun observeHistory(context: Context): Flow<List<VideoEntity>> {
    return getDao(context).getAllHistory()
  }

  /**
   * 删除某一条
   */
  suspend fun deleteHistory(context: Context, video: VideoEntity) {
    getDao(context).delete(video)
  }
}

//---
//
//### 第六步：在你的 App 中调用
//
//**场景 1：当视频开始播放或暂停时，保存到历史记录**
//
//在你的 `MainActivity` 或 `PlaylistSheetFragment` 的 `playerListener` 中：
//
//```kotlin
//// 假设这是在 onMediaItemTransition 或者 onPause 时
//// 构建你的 VideoEntity
//val currentItem = player.currentMediaItem
//if (currentItem != null) {
//  val entity = VideoEntity(
//    id = currentItem.mediaId, // 确保这个 ID 是唯一的
//    uri = currentItem.localConfiguration?.uri.toString(),
//    name = currentItem.mediaMetadata.title.toString(),
//    positionMs = player.currentPosition,
//    subUri = null // 如果有字幕这里填字幕
//  )
//
//  // 保存到数据库 (必须在协程中调用)
//  lifecycleScope.launch(Dispatchers.IO) {
//    HistoryManager.addHistory(this@MainActivity, entity)
//  }
//}
//```
//
//**场景 2：在“历史记录”页面展示列表**
//
//```kotlin
//// 在历史记录 Activity/Fragment 的 onCreate 中
//lifecycleScope.launch {
//  // 监听数据库变化，只要数据库变了，这里会自动收到最新的 list
//  HistoryManager.observeHistory(context).collect { historyList ->
//    // 更新你的 RecyclerView Adapter
//    historyAdapter.submitList(historyList)
//
//    if (historyList.isEmpty()) {
//      // 显示"暂无历史"的空视图
//    }
//  }
//}