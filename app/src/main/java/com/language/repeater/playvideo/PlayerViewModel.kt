package com.language.repeater.playvideo

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.language.repeater.app.MainApp
import com.language.repeater.datastore.DsManager
import com.language.repeater.playcore.SleepTimerManager
import com.language.repeater.playvideo.model.VideoEntity
import com.language.repeater.utils.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
  private val playbackGraph = MainApp.playbackGraph

  val player = playbackGraph.player
  val videoOutput = playbackGraph.videoOutput
  val playbackListController = playbackGraph.playbackListController
  val sentenceController = playbackGraph.sentenceController
  val subtitleController = playbackGraph.subtitleController

  /**
   * 播放页全屏状态：true = 全屏、false = 默认布局。
   *
   * 只表达「要不要铺满」这一业务意图，真正用哪套布局（横向全屏 / 竖向全屏）
   * 由 UI 层读实际窗口方向判定，避免方向转过来之前出现中间态畸形帧。
   *
   * 屏幕方向请求与系统栏隐藏由 UI 层观察此状态后执行，ViewModel 不持有 Activity。
   */
  var isFullScreen by mutableStateOf(false)
    private set

  /** 在默认布局与全屏之间切换。全屏朝向由 UI 层按画面宽高比决定。 */
  fun changeScreenMode(toFull: Boolean) {
    if (playbackListController.items.value.isEmpty()) {
      return
    }
    isFullScreen = toFull
  }

  /**
   * 画面宽高比。单独拉出一条流，让只关心朝向的 UI 不跟着播放态、倍速重组。
   *
   * 值为 0 表示画面尚未就绪或是纯音频。
   */
  val videoAspectRatio: StateFlow<Float> = player.state
    .map { it.videoAspectRatio }
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

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
    val progress = player.progress.value
    val remainingMs = progress.durationMs - progress.positionMs
    if (progress.durationMs > 0L && remainingMs > 0L) {
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
