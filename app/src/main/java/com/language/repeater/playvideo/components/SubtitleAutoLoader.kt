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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.language.repeater.dataStore
import com.language.repeater.subtitleStore
import com.language.repeater.utils.DataStoreKey.SUBTITLE_FOLDER_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 自动字幕加载器
 * 监听播放器状态，自动从指定文件夹加载同名字幕
 */
class SubtitleAutoLoader(
  private val context: Context,
  private val player: Player,
  private val scope: CoroutineScope, // 传入 lifecycleScope 或 viewModelScope
) : Player.Listener {
  companion object {
    const val TAG = "wangzixu-SubtitleAutoLoader"

    fun loadAllSub(context: Context, fUri: Uri?): MutableMap<String, Uri>? {
      if (fUri == null) return null

      val folder = DocumentFile.fromTreeUri(context, fUri)
      if (folder == null || !folder.exists()) return null

      val map = mutableMapOf<String, Uri>()
      fun traversalFolder(f: DocumentFile) {
        val files = f.listFiles()
        for (file in files) {
          if (file.isDirectory) {
            //traversalFolder(file)
          } else {
            val fileName = file.name ?: continue
            if (fileName.endsWith(".srt", ignoreCase = true)) {
              val name = fileName.removeSuffix(".srt")
              map[name] = file.uri
            }
          }
        }
      }

      traversalFolder(folder)

      map.forEach {n, v->
        Log.d(TAG, "loadAllSub 当前寻找到的字幕列表 name:$n, value:$v")
      }

      return map
    }
  }

  var subtitleFold: String? = null
  init {
    // 初始化时就把自己注册进去
    player.addListener(this)
    context.dataStore.data.map { it[SUBTITLE_FOLDER_KEY] }.onEach {
      //Log.i(TAG, "$TAG 字幕文件夹更新 $it")
      subtitleFold = it

      if (it != null && it.isNotEmpty() && player.mediaItemCount > 0) {
        scope.launch(Dispatchers.IO) {
          tryLoadAllSubtitle()
        }
      }
    }.launchIn(scope)
  }

  // 销毁时记得移除监听
  fun release() {
    player.removeListener(this)
  }

  private fun checkHasSubtitle(mediaItem: MediaItem): Boolean {
    // 1. 检查当前 Item 是否已经配置了字幕
    return mediaItem.localConfiguration?.subtitleConfigurations?.isNotEmpty() == true
  }

  override fun onPlayerError(error: PlaybackException) {
    Log.i(TAG, "播放器报错: ${error.message}", error)
  }

  // 2. 监听轨道变化：看看播放器到底有没有识别出“Text”轨道
//  override fun onTracksChanged(tracks: Tracks) {
//    var hasTextTrack = false
//    for (group in tracks.groups) {
//      if (group.type == C.TRACK_TYPE_TEXT) {
//        hasTextTrack = true
//        val isSupported = group.isSupported
//        val isSelected = group.isSelected
//        Log.i(TAG, "发现字幕轨道! 支持状态: $isSupported, 选中状态: $isSelected")
//      }
//    }
//    if (!hasTextTrack) {
//      Log.i(TAG, "当前没有发现任何字幕轨道 (CC按钮变灰的原因)")
//    }
//  }


  private suspend fun tryLoadAllSubtitle() {
    // 只有当 mediaItem 不为空，且没有字幕时, 才我们预期的切换时才处理
    if (subtitleFold.isNullOrEmpty()) {
      return
    }

    // 2. 获取用户之前设置的字幕文件夹
    val folderUri = subtitleFold?.toUri() ?: return
    val map = SubtitleAutoLoader.loadAllSub(context, folderUri)
    if(map.isNullOrEmpty()) return
    //热替换 (Hot Swap) - 优化版：精准定位并替换，不浪费查询结果
    withContext(Dispatchers.Main) {
      // 1. 记录关键状态 (如果是当前播放项)
      val currentIndex = player.currentMediaItemIndex
      val isPlaying = player.isPlaying
      val currentPos = player.currentPosition

      // 2. 在内存中重建列表 (速度极快，不耗时)
      val newPlaylist = ArrayList<MediaItem>()
      var changed = false
      for (i in 0 until player.mediaItemCount) {
        val item = player.getMediaItemAt(i)
        val name = item.mediaMetadata.title ?: ""
        val subUri = map[name]
        if (checkHasSubtitle(item) || subUri == null) {
          newPlaylist.add(item)
        } else {
          // 5. 构建新的带字幕的 MediaItem
          val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subUri)
            .setMimeType(MimeTypes.APPLICATION_SUBRIP) //.srt
            .setLanguage("en")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

          val newMediaItem = item.buildUpon()
            .setSubtitleConfigurations(listOf(subtitleConfig))
            .build()
          newPlaylist.add(newMediaItem)
          changed = true

          //存入sp
          val prefKey = stringPreferencesKey(item.mediaId)
          context.subtitleStore.edit { sp-> sp[prefKey] = subUri.toString() }
        }
      }

      if (changed) {
        player.setMediaItems(newPlaylist, currentIndex, currentPos)
        // 4. 强制重建管道
        player.prepare()
        if (isPlaying) {
          player.play()
        }
      }
    }
  }

  fun updateCurrentItemSubtitle(uri: Uri) {
    val mediaItem = player.currentMediaItem ?: return
    val index = player.currentMediaItemIndex

    val has = mediaItem.localConfiguration?.subtitleConfigurations?.firstOrNull {
      it.uri == uri
    }
    if (has != null) return

    //热替换 (Hot Swap) - 优化版：精准定位并替换，不浪费查询结果
    if (index != C.INDEX_UNSET) {
      val currentPos = player.currentPosition
      val isPlaying = player.isPlaying

      val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(uri)
        .setMimeType(MimeTypes.APPLICATION_SUBRIP) //.srt
        .setLanguage("en")
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()

      val newMediaItem = mediaItem.buildUpon()
        .setSubtitleConfigurations(listOf(subtitleConfig))
        .build()

      // 举例：[A(旧), B, C]，index=0
      // 第一步 add(1, A新): [A(旧), A(新), B, C]
      player.addMediaItem(index + 1, newMediaItem)
      // 第二步 remove(0): [A(新), B, C]
      player.removeMediaItem(index)

      // 因为旧的被删了，播放器可能会自动跳到下一个或者停止
      // 我们需要立即把进度拉回到原来的位置，并强制 prepare
      if (currentPos != C.TIME_UNSET) {
        player.seekTo(index, currentPos)
      }
      // 这一步是必须的！告诉播放器：“别复用了，给我重新加载！”
      player.prepare()
      if (isPlaying) {
        player.play()
      }

      val prefKey = stringPreferencesKey(mediaItem.mediaId)
      scope.launch {
        context.subtitleStore.edit { sp-> sp[prefKey] = uri.toString() }
      }

      Log.i(TAG, "设置字幕当前字幕成功 id:${mediaItem.mediaMetadata.title}")
    }
  }

  /**
   * 在播放列表中查找特定 MediaItem 的索引
   */
  private fun findMediaItemIndex(player: Player, targetItem: MediaItem): Int {
    val count = player.mediaItemCount
    for (i in 0 until count) {
      val item = player.getMediaItemAt(i)
      // 比较逻辑：
      // 1. 优先比较对象引用 (最快)
      // 2. 其次比较 Uri (防止列表刷新导致对象变了但内容没变)
      if (item == targetItem ||
        item.localConfiguration?.uri == targetItem.localConfiguration?.uri
      ) {
        return i
      }
    }
    return C.INDEX_UNSET
  }

  /**
   * 在文件夹里遍历查找匹配的文件
   */
  private fun findSubtitleFile(folder: DocumentFile, baseName: String): Uri? {
    val files = folder.listFiles()
    for (file in files) {
      val fileName = file.name ?: continue
      if (fileName.startsWith(baseName, ignoreCase = true) &&
        fileName.endsWith(".srt", ignoreCase = true)
      ) {
        return file.uri
      }
    }
    return null
  }

  private fun extractFileNameFromUri(uri: Uri?): String? {
    return uri?.lastPathSegment
  }

//  private fun getSavedSubtitleFolderUri(context: Context): Uri? {
//    val sp = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
//    val uriString = sp.getString("subtitle_folder_uri", null) ?: return null
//    return Uri.parse(uriString)
//  }

  // 使用 DataStore 读取配置 (挂起函数)
//  private suspend fun getSavedSubtitleFolderUri(context: Context): Uri? {
//    // dataStore.data 是一个 Flow，使用 first() 获取当前最新值
//    val preferences = context.dataStore.data.first()
//    val uriString = preferences[SUBTITLE_FOLDER_KEY] ?: return null
//    return uriString.toUri()
//  }
}