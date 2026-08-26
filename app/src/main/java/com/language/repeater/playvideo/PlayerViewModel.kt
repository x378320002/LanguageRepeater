package com.language.repeater.playvideo

import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.language.repeater.app.MainApp
import com.language.repeater.datastore.DsManager
import com.language.repeater.datastore.subtitleStore
import com.language.repeater.playvideo.model.VideoEntity
import com.language.repeater.utils.FileUtil
import com.language.repeater.utils.SubtitleFolderScanner
import com.language.repeater.utils.UriAccessUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel : ViewModel() {
  val editMode = DsManager.observe(DsManager.KEY_EDIT_SEN_MODE, false)
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  private val playbackGraph = MainApp.instance.playbackGraph
  private val mediaAnalysisController = playbackGraph.sentenceController

  // 播放事实、列表和当前媒体分析均来自应用级对象，ViewModel 只转发 UI 意图。
  val player = playbackGraph.player
  val playbackListController = playbackGraph.playbackListController
  val videoOutput = playbackGraph.videoOutput

  val playbackMode = playbackListController.playbackMode

  private val progressController = playbackGraph.progressController

  val pcmLoaderStateFlow = mediaAnalysisController.pcmLoader
  val sentencesFlow = mediaAnalysisController.sentences
  val repeatable = mediaAnalysisController.repeatAb
  val curAbSentenceFlow = mediaAnalysisController.currentSentence

  // 复读与句子控制由独立协调器处理，只通过 PlaybackPlayer 执行 seek。
  val sleepTimerRemaining = com.language.repeater.playcore.SleepTimerManager.remainingSeconds

  fun startSleepTimer(minutes: Int) {
    if (minutes <= 0) {
      com.language.repeater.playcore.SleepTimerManager.stopTimer()
    } else {
      com.language.repeater.playcore.SleepTimerManager.startTimerMinutes(minutes)
    }
  }

  fun startSleepTimerByCurrentItem() {
    val state = player.state.value
    val remainingMs = state.durationMs - state.positionMs
    if (state.durationMs > 0L && remainingMs > 0L) {
      com.language.repeater.playcore.SleepTimerManager.startTimer(
        seconds = remainingMs / 1000L + 1L
      )
    }
  }

  fun togglePlayPause() {
    if (player.state.value.isPlaying) {
      player.pause()
    } else {
      player.play()
    }
  }

  fun play() {
    player.play()
  }

  fun pause() {
    player.pause()
  }

  fun saveCurrentPos() {
    viewModelScope.launch {
      progressController.saveCurrentPosition()
    }
  }

  fun cyclePlaybackMode() {
    playbackListController.cyclePlaybackMode()
  }

  fun deleteItem(mediaId: String) {
    viewModelScope.launch {
      playbackListController.remove(mediaId)
    }
  }

  fun moveItemToFirst(mediaId: String) {
    viewModelScope.launch {
      playbackListController.moveToFirst(mediaId)
    }
  }

  fun selectItem(mediaId: String) {
    viewModelScope.launch {
      playbackListController.select(mediaId)
    }
  }

  // 复读与句子控制由独立协调器处理，只通过 PlaybackPlayer 执行 seek。
  fun toggleRepeat() = mediaAnalysisController.toggleRepeat()
  fun seekToNextSentence() = mediaAnalysisController.seekToNextSentence()
  fun seekToPreviousSentence() = mediaAnalysisController.seekToPreviousSentence()
  fun backToSentenceHead() = mediaAnalysisController.backToSentenceHead()

  fun onSubtitleSelected(uri: Uri) {
    mediaAnalysisController.updateSubtitle(uri)
  }

  fun updatePosition(position: Float) {
    mediaAnalysisController.previewPosition((position * 1000f).toLong())
  }

  fun updatePosition(position: Long) {
    mediaAnalysisController.previewPosition(position)
  }

  fun loadSentenceData(forceUseVad: Boolean = false) {
    mediaAnalysisController.reloadSentences(forceUseVad)
  }

  fun splitCurrentSentence() {
    mediaAnalysisController.splitCurrentSentence()
  }

  fun saveSentenceData() {
    mediaAnalysisController.mergeAndSaveSentences()
  }

  fun deleteCurrentSentence() {
    mediaAnalysisController.deleteCurrentSentence()
  }

  fun getHistoryFlow() = mediaAnalysisController.history

  fun addPlayList(
    list: List<VideoEntity>,
    isReplace: Boolean
  ) {
    viewModelScope.launch {
      if (isReplace) {
        playbackListController.replace(list)
      } else {
        playbackListController.addToFront(list)
      }
    }
  }

  // 历史项加入列表后按稳定 mediaId 选择播放。
  fun playHistoryItem(item: VideoEntity) {
    viewModelScope.launch {
      playbackListController.addToFront(listOf(item))
      playbackListController.select(item.id)
    }
  }

  fun playItem(mediaId: String) {
    selectItem(mediaId)
  }

  fun deleteHistory(item: VideoEntity) {
    mediaAnalysisController.deleteHistory(item.id)
  }

  /**
   * 处理选择的视频文件
   * 包括：获取文件信息、申请权限、查找字幕、添加到播放列表
   */
  fun handleSelectedVideos(uris: List<Uri>) {
    if (uris.isEmpty()) {
      return
    }

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

      if (infos.isEmpty()) {
        return@launch
      }

      // 去文件夹寻找字幕
      if (needToCheckSubFolder) {
        val folder = DsManager.get(DsManager.KEY_SUBTITLE_FOLDER, "")
          .takeIf { it.isNotEmpty() }
          ?.toUri()
        val map = SubtitleFolderScanner.scan(context, folder)
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
    mediaAnalysisController.mergePreviousSentence()
  }

  fun mergeNextSentence() {
    mediaAnalysisController.mergeNextSentence()
  }

  fun insertSentence() {
    mediaAnalysisController.insertSentence()
  }

  fun editMode(edit: Boolean) {
    viewModelScope.launch {
      DsManager.set(DsManager.KEY_EDIT_SEN_MODE, edit)
    }
  }
}
