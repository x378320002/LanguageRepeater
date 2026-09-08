package com.language.repeater.playvideo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.language.repeater.app.MainApp
import com.language.repeater.datastore.DsManager
import com.language.repeater.playcore.SleepTimerManager
import com.language.repeater.playvideo.model.VideoEntity
import com.language.repeater.utils.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放页 ViewModel。
 *
 * 播放器、播放列表、句子分析都是应用级单例，这里直接外露供 UI 使用，
 * 不再逐方法转发——转发层既不加工数据也不承载状态，只会让调用链多一跳。
 * 本类只保留三类真正属于自己的东西：页面级 UI 状态、需要 viewModelScope 的挂起编排、
 * 以及跨组件的业务流程（文件导入、定时关闭）。
 */
class PlayerViewModel : ViewModel() {
  private val playbackGraph = MainApp.instance.playbackGraph

  val player = playbackGraph.player
  val videoOutput = playbackGraph.videoOutput
  val playbackListController = playbackGraph.playbackListController
  val sentenceController = playbackGraph.sentenceController
  val subtitleController = playbackGraph.subtitleController

  /** 句子编辑模式，仅本页面使用，持久化在 DataStore 以便重建后恢复。 */
  val editMode = DsManager.observe(DsManager.KEY_EDIT_SEN_MODE, false)
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  fun editMode(edit: Boolean) {
    viewModelScope.launch {
      DsManager.set(DsManager.KEY_EDIT_SEN_MODE, edit)
    }
  }

  // ---------- 播放列表：controller 侧是 suspend，需要 scope 承接 ----------

  fun selectItem(mediaId: String) {
    viewModelScope.launch {
      playbackListController.select(mediaId)
    }
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

  // ---------- 定时关闭 ----------

  val sleepTimerRemaining = SleepTimerManager.remainingSeconds

  fun startSleepTimer(minutes: Int) {
    if (minutes <= 0) {
      SleepTimerManager.stopTimer()
    } else {
      SleepTimerManager.startTimerMinutes(minutes)
    }
  }

  /** 播完当前条目即停：按剩余时长设定倒计时，多给 1 秒避免临界提前掐断。 */
  fun startSleepTimerByCurrentItem() {
    val state = player.state.value
    val remainingMs = state.durationMs - state.positionMs
    if (state.durationMs > 0L && remainingMs > 0L) {
      SleepTimerManager.startTimer(seconds = remainingMs / 1000L + 1L)
    }
  }

  // ---------- 文件导入 ----------

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
      val infos = uris.map { uri ->
        FileUtil.takePersistablePermission(context, uri)
        FileUtil.getFileInfo(context, uri)
      }
      if (infos.isEmpty()) {
        return@launch
      }

      subtitleController.resolveSubtitles(infos)

      withContext(Dispatchers.Main) {
        addPlayList(infos)
      }
    }
  }

  private fun addPlayList(list: List<VideoEntity>) {
    viewModelScope.launch {
      playbackListController.addToFront(list)
    }
  }
}
