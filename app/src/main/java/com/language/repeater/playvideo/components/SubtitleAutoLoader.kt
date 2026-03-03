package com.language.repeater.playvideo.components

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import com.language.repeater.dataStore
import com.language.repeater.db.videoInfoDao
import com.language.repeater.playvideo.model.toEntity
import com.language.repeater.subtitleStore
import com.language.repeater.utils.DataStoreUtil.KEY_SUBTITLE_FOLDER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SubtitleAutoLoader(
  private val context: Context,
  private val player: Player,
  private val scope: CoroutineScope,
) {
  companion object {
    const val TAG = "wangzixu_SubtitleAutoLoader"

    fun scanSubtitleFolder(context: Context, folderUri: Uri?): Map<String, Uri> {
      val map = mutableMapOf<String, Uri>()
      if (folderUri == null) return map

      val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return map

      // 简单的一层遍历，不递归，防止性能爆炸
      folder.listFiles().forEach { file ->
        val name = file.name
        if (name != null
          && (name.endsWith(".srt", ignoreCase = true)
              || name.endsWith(".vtt", ignoreCase = true))) {
          // key = 文件名 (不含后缀)
          map[name.substringBeforeLast(".")] = file.uri
        }
      }
      return map
    }

    //fun loadAllSub(context: Context, fUri: Uri?): MutableMap<String, Uri>? {
    //  if (fUri == null) return null
    //
    //  val folder = DocumentFile.fromTreeUri(context, fUri)
    //  if (folder == null || !folder.exists()) return null
    //
    //  val map = mutableMapOf<String, Uri>()
    //  fun traversalFolder(f: DocumentFile) {
    //    val files = f.listFiles()
    //    for (file in files) {
    //      if (file.isDirectory) {
    //        //traversalFolder(file)
    //      } else {
    //        val fileName = file.name ?: continue
    //        if (fileName.endsWith(".srt", ignoreCase = true)) {
    //          val name = fileName.removeSuffix(".srt")
    //          map[name] = file.uri
    //        }
    //      }
    //    }
    //  }
    //
    //  traversalFolder(folder)
    //
    //  map.forEach {n, v->
    //    Log.d(TAG, "loadAllSub 当前寻找到的字幕列表 name:$n, value:$v")
    //  }
    //
    //  return map
    //}
  }

  private var subtitleFolderUri: Uri? = null
  private var observeJob: Job? = null
  private var first = true

  init {
    startObserving()
  }

  private fun startObserving() {
    observeJob = context.dataStore.data
      .map { it[KEY_SUBTITLE_FOLDER] }
      .distinctUntilChanged()
      .onEach { path ->
        if (path != null) {
          subtitleFolderUri = path.toUri()
          // 文件夹变了，或者刚启动，尝试批量加载
          if (player.mediaItemCount > 0 && !first) {
            tryLoadAllSubtitle()
          }
        }
        first = false
      }
      .launchIn(scope)
  }

  fun release() {
    observeJob?.cancel()
  }

  // --- 核心功能 1: 批量扫描并替换 (用于播放列表刚加载时) ---
  private fun tryLoadAllSubtitle() {
    val folderUri = subtitleFolderUri ?: return

    scope.launch(Dispatchers.IO) {
      // 1. 耗时操作：扫描文件夹建立映射表
      val subtitleMap = scanSubtitleFolder(context, folderUri)
      if (subtitleMap.isEmpty()) return@launch

      // 2. 回到主线程操作播放器
      withContext(Dispatchers.Main) {
        applySubtitlesToPlaylist(subtitleMap)
      }
    }
  }

  // --- 核心功能 2: 手动更新单曲字幕 (用于用户手动选择) ---
  fun updateCurrentItemSubtitle(subtitleUri: Uri): MediaItem? {
    val currentItem = player.currentMediaItem ?: return null
    val index = player.currentMediaItemIndex
    if (index == C.INDEX_UNSET) return null

    // 检查是否已经有了，避免重复刷新
    val hasIt =
      currentItem.localConfiguration?.subtitleConfigurations?.any { it.uri == subtitleUri } == true
    if (hasIt) return null

    Log.i(TAG, "手动更新字幕: ${currentItem.mediaMetadata.title}")

    // 保存到 DataStore 记录 (以后自动加载)
    val prefKey = stringPreferencesKey(currentItem.mediaId)
    scope.launch(Dispatchers.IO) {
      context.subtitleStore.edit { it[prefKey] = subtitleUri.toString() }
      val newEntity = currentItem.toEntity()
      newEntity.subUri = subtitleUri.toString()
      context.videoInfoDao.insertOrUpdateInfo(newEntity)
    }

    // 执行热替换
    return replaceItemWithSubtitle(index, currentItem, subtitleUri)
  }

  // --- 内部逻辑 ---

  //private fun scanSubtitleFolder(folderUri: Uri): Map<String, Uri> {
  //  val map = mutableMapOf<String, Uri>()
  //  val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return map
  //
  //  // 简单的一层遍历，不递归，防止性能爆炸
  //  folder.listFiles().forEach { file ->
  //    val name = file.name
  //    if (name != null && name.endsWith(".srt", ignoreCase = true)) {
  //      // key = 文件名 (不含后缀)
  //      map[name.substringBeforeLast(".")] = file.uri
  //    }
  //  }
  //  return map
  //}

  // 批量应用逻辑
  private fun applySubtitlesToPlaylist(map: Map<String, Uri>) {
    val newItems = ArrayList<MediaItem>()
    var hasChange = false
    val count = player.mediaItemCount

    for (i in 0 until count) {
      val item = player.getMediaItemAt(i)
      val title = item.mediaMetadata.title.toString()

      val subtitleUri = map[title]
      val alreadyHas = item.localConfiguration?.subtitleConfigurations?.isNotEmpty() == true

      if (!alreadyHas && subtitleUri != null) {
        // 构建带字幕的新 Item
        val newItem = buildItemWithSubtitle(item, subtitleUri)
        newItems.add(newItem)
        hasChange = true

        scope.launch(Dispatchers.IO) {
          context.videoInfoDao.insertOrUpdateInfo(newItem.toEntity())
        }
      } else {
        newItems.add(item)
      }
    }

    if (hasChange) {
      // 保持当前进度
      val curIndex = player.currentMediaItemIndex
      val curPos = player.currentPosition

      player.setMediaItems(newItems, curIndex, curPos)
      player.prepare()
      if (player.isPlaying) player.play() // 恢复播放状态
    }
  }

  // 单个替换逻辑 (热替换)
  private fun replaceItemWithSubtitle(index: Int, item: MediaItem, subtitleUri: Uri): MediaItem {
    val newItem = buildItemWithSubtitle(item, subtitleUri)

    // 使用先加后删策略，或者直接 setMediaItems (针对单个)
    // 为了最稳妥，我们用 setMediaItems 更新整个列表的这一项（其实开销很小）
    // 或者使用你之前验证过的 "修改ID + replace"

    // 这里使用最稳的：重置列表法 (只改这一个)
    val playlist = ArrayList<MediaItem>()
    for (i in 0 until player.mediaItemCount) {
      if (i == index) playlist.add(newItem)
      else playlist.add(player.getMediaItemAt(i))
    }

    val isPlaying = player.isPlaying
    val curPos = player.currentPosition
    player.setMediaItems(playlist, index, curPos)
    player.prepare()
    if (isPlaying) {
      player.play()
    }
    return newItem
  }

  private fun buildItemWithSubtitle(item: MediaItem, subUri: Uri): MediaItem {
    val config = MediaItem.SubtitleConfiguration.Builder(subUri)
      .setMimeType(MimeTypes.APPLICATION_SUBRIP)
      .setLanguage("en") // 建议根据实际情况设置
      .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
      .build()

    return item.buildUpon()
      .setSubtitleConfigurations(listOf(config))
      .build()
  }
}