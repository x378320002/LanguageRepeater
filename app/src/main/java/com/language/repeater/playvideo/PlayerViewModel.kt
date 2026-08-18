package com.language.repeater.playvideo

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.language.repeater.app.MainApp
import com.language.repeater.datastore.DsManager
import com.language.repeater.datastore.subtitleStore
import com.language.repeater.db.historyDao
import com.language.repeater.playcore.PlaybackCore
import com.language.repeater.player.Media3PlayerEngine
import com.language.repeater.player.PlayerEngine
import com.language.repeater.playvideo.model.VideoEntity
import com.language.repeater.utils.FileUtil
import com.language.repeater.utils.UriAccessUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel : ViewModel() {
  companion object {
    private const val TAG = "wangzixu_PlayerViewModel"
  }

  val editMode = DsManager.observe(DsManager.KEY_EDIT_SEN_MODE, false)
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  private val playbackCore = PlaybackCore.getInstance(MainApp.instance)

  /**
   * 播放器引擎抽象 — 业务层通过它访问播放能力
   * 未来替换为腾讯播放器时，只需替换这里的实现
   */
  val playerEngine: PlayerEngine = Media3PlayerEngine(MainApp.instance, playbackCore)

  /**
   * 暴露给旧 View 组件使用的底层 Core，阶段 3 删除旧 View 后移除
   */
  @Deprecated("仅旧 View 组件使用，阶段 3 删除", ReplaceWith("playerEngine"))
  val legacyPlaybackCore: PlaybackCore
    get() = playbackCore

  // 业务数据流（不暴露 Media3 类型）
  val pcmLoaderStateFlow = playbackCore.pcmLoaderStateFlow
  val allWaveDataFlow = playbackCore.allWaveDataFlow
  val sentencesFlow = playbackCore.sentencesFlow
  val repeatable = playbackCore.repeatAb
  val curAbSentenceFlow = playbackCore.curAbSentenceFlow

  // ========== 以下属性仅供旧 View 组件使用，阶段 3 删除 ==========
  @Deprecated("仅旧 View 使用", ReplaceWith("playerEngine.state"))
  val playerInstance = playbackCore.playerInstance

  @Deprecated("仅旧 View 使用", ReplaceWith("currentMediaTitle"))
  val currentMediaItem = playbackCore.currentMediaItem

  @Deprecated("仅旧 View 使用")
  val mediaItemCount = playbackCore.mediaItemCount

  @Deprecated("仅旧 View 使用")
  val playlistRefreshEvent = playbackCore.playlistRefreshEvent

  @Deprecated("仅旧 View 使用")
  val currentPosition = playbackCore.currentPosition

  @Deprecated("仅旧 View 使用")
  val currentPositionSeconds = playbackCore.currentPositionSeconds

  @Deprecated("仅旧 View 使用")
  val isUiPlaying = playbackCore.isPlaying

  @Deprecated("仅旧 View 使用")
  val playSpeedState = playbackCore.playSpeed

  @Deprecated("仅旧 View 使用", ReplaceWith("playerEngine"))
  fun getPlayer(): androidx.media3.common.Player? = playbackCore.playerInstance.value
  // ========== 旧 View 属性结束 ==========

  init {
    playbackCore.connect()
  }

  // 睡眠定时
  val sleepTimerRemaining = com.language.repeater.playcore.SleepTimerManager.remainingSeconds

  fun startSleepTimer(minutes: Int) {
    if (minutes <= 0) {
      com.language.repeater.playcore.SleepTimerManager.stopTimer()
    } else {
      com.language.repeater.playcore.SleepTimerManager.startTimerMinutes(minutes)
    }
  }

  fun startSleepTimerByCurrentItem() {
    val player = playbackCore.playerInstance.value
    if (player != null) {
      com.language.repeater.playcore.SleepTimerManager.startByCurrentItem(player)
    }
  }

  fun togglePlayPause() {
    val state = playerEngine.state.value
    if (state.isPlaying || state.playbackState == com.language.repeater.player.PlaybackState.Buffering) {
      playerEngine.pause()
    } else {
      playerEngine.play()
    }
  }

  fun play() {
    playerEngine.play()
  }

  fun pause() {
    playerEngine.pause()
  }

  fun saveCurrentPos() {
    playbackCore.saveCurrentPositionImmediate()
  }

  fun deleteItem(index: Int) {
    playbackCore.removeMediaItem(index)
  }

  fun moveItemToFirst(index: Int) {
    // 通过 PlayerEngine 移动
    playerEngine.moveItem(index, 0)
  }

  fun moveItem(from: Int, to: Int) {
    playbackCore.changeItemPositionSilent(from, to)
  }

  // 复读控制
  fun toggleRepeat() = playbackCore.toggleRepeat()
  fun seekToNextSentence() = playbackCore.seekToNextSentence()
  fun seekToPreviousSentence() = playbackCore.seekToPreviousSentence()
  fun backToSentenceHead() = playbackCore.backToSentenceHead()

  // 手动选字幕
  fun onSubtitleSelected(uri: Uri) = playbackCore.updateSubtitle(uri)

  fun updatePosition(position: Float) {
    playbackCore.updatePosition(false, (position * 1000).toLong())
  }

  fun updatePosition(position: Long) {
    playbackCore.updatePosition(false, position)
  }

  fun loadSentenceData(forceUseVad: Boolean = false) {
    playbackCore.forceLoadCurrentSentences(forceUseVad)
  }

  fun splitCurrentSentence() {
    playbackCore.splitCurrentSentence()
  }

  fun saveSentenceData() {
    viewModelScope.launch {
      playbackCore.mergeAndSaveSentences()
    }
  }

  fun setPlayerRepeatMode(mode: Int) {
    playbackCore.setPlayerRepeatMode(mode)
  }

  fun deleteCurrentSentence() {
    playbackCore.deleteCurSentence()
  }

  // 直接暴露 Flow 给 UI 监听
  fun getHistoryFlow() = MainApp.instance.historyDao.getAllHistory()

  fun addPlayList(
    list: List<VideoEntity>,
    isReplace: Boolean
  ) {
    playbackCore.addPlayList(list, isReplace)
  }

  // 播放历史记录中的某一项
  fun playHistoryItem(item: VideoEntity) {
    playbackCore.playItem(item)
  }

  fun playItem(index: Int) {
    playbackCore.seekToItem(index)
  }

  fun deleteHistory(item: VideoEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      MainApp.instance.historyDao.deleteByVideoId(item.id)
    }
  }

  /**
   * 处理选择的视频文件
   * 包括：获取文件信息、申请权限、查找字幕、添加到播放列表
   */
  fun handleSelectedVideos(uris: List<Uri>) {
    if (uris.isEmpty()) return

    viewModelScope.launch(Dispatchers.IO) {
      val context = MainApp.instance
      val preferences = context.subtitleStore.data.firstOrNull()

      // 转成 VideoEntity 列表
      var needToCheckSubFolder = false
      val infos = uris.map { uri ->
        FileUtil.takePersistablePermission(context, uri)
        val info = FileUtil.getFileInfo(context, uri)

        // 检查是否有保存的字幕映射
        val prefKey = stringPreferencesKey(info.id)
        val sub = preferences?.get(prefKey)
        if (!sub.isNullOrEmpty()) {
          val subUri = sub.toUri()
          if (UriAccessUtil.canRead(context, subUri)) {
            info.subUri = sub
          } else {
            // 字幕链接已失效，清除映射
            context.subtitleStore.edit { sp -> sp.remove(prefKey) }
            info.subUri = null
            needToCheckSubFolder = true
          }
        } else {
          needToCheckSubFolder = true
        }
        info
      }

      if (infos.isEmpty()) return@launch

      // 去文件夹寻找字幕
      if (needToCheckSubFolder) {
        val folder = DsManager.get(DsManager.KEY_SUBTITLE_FOLDER, "")
          .takeIf { it.isNotEmpty() }
          ?.toUri()
        val map = com.language.repeater.playvideo.components.SubtitleAutoLoader.scanSubtitleFolder(context, folder)
        if (map.isNotEmpty()) {
          infos.forEach { info ->
            if (info.subUri == null) {
              val key = info.name.substringBeforeLast(".")
              val tempUri = map[key]
              if (tempUri != null) {
                info.subUri = tempUri.toString()
                val prefKey = stringPreferencesKey(info.id)
                context.subtitleStore.edit { sp -> sp[prefKey] = tempUri.toString() }
              }
            }
          }
        }
      }

      // 添加到播放列表
      withContext(Dispatchers.Main) {
        addPlayList(infos, false)
      }
    }
  }

  fun mergePreSentence() {
    playbackCore.mergePre()
  }

  fun mergeNextSentence() {
    playbackCore.mergeNext()
  }

  fun insertSentence() {
    playbackCore.insertSentence()
  }

  fun editMode(edit: Boolean) {
    viewModelScope.launch {
      DsManager.set(DsManager.KEY_EDIT_SEN_MODE, edit)
    }
  }
}
