package com.language.repeater.playvideo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.language.repeater.app.MainApp
import com.language.repeater.datastore.DsManager
import com.language.repeater.pcm.FFmpegUtil
import com.language.repeater.playcore.SleepTimerManager
import com.language.repeater.db.VideoEntity
import com.language.repeater.db.toPlaybackMedia
import com.language.repeater.sentence.SentenceStoreUtil
import com.language.repeater.utils.MediaStoreItem
import com.language.repeater.utils.ToastUtil
import com.language.repeater.utils.UriAccessUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
   * 当前因文件读不到而无法播放的媒体 id。非 null 时 UI 应提示用户该文件已失效。
   * 用户删除条目、手动关闭提示，或该媒体恢复正常后清空。
   */
  private val _permissionErrorMediaId = MutableStateFlow<Long?>(null)
  val permissionErrorMediaId: StateFlow<Long?> = _permissionErrorMediaId.asStateFlow()

  init {
    // 播放出错的原因很多，按错误码区分并不可靠：授权被回收后 ContentResolver 抛的
    // SecurityException 不是 IOException，Media3 会把它归为 ERROR_CODE_UNSPECIFIED。
    // 因此直接试读一次 uri，以「文件到底还能不能打开」为判据。
    viewModelScope.launch {
      player.state
        .map { state ->
          if (state.error == null) {
            null
          } else {
            state.currentMedia
          }
        }
        .distinctUntilChanged()
        .collect { media ->
          if (media == null) {
            _permissionErrorMediaId.value = null
            return@collect
          }
          val readable = UriAccessUtil.canRead(MainApp.instance, media.uri.toUri())
          if (!readable) {
            _permissionErrorMediaId.value = media.mediaId
          }
        }
    }
  }

  /** 用户关闭失效提示后调用。 */
  fun dismissPermissionError() {
    _permissionErrorMediaId.value = null
  }

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

  fun selectItem(mediaId: Long) {
    viewModelScope.launch {
      playbackListController.select(mediaId)
    }
  }

  /**
   * 从播放列表移除条目，连同它的句子切分与 wav 缓存一起清掉。
   *
   * 两类缓存都以 mediaId 命名，条目删除后 id 不会被复用，残留文件无法再被命中，必须显式删除。
   */
  fun deleteItem(mediaId: Long) {
    viewModelScope.launch {
      playbackListController.remove(mediaId)
      _permissionErrorMediaId.value = null
      withContext(Dispatchers.IO) {
        val context = MainApp.instance
        SentenceStoreUtil.deleteData(context, mediaId.toString())
        FFmpegUtil.delete(context, mediaId.toString())
      }
    }
  }

  fun moveItemToFirst(mediaId: Long) {
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
   * 处理媒体库中选中的条目：查找字幕、添加到播放列表。
   *
   * 不申请持久化 Uri 授权：媒体库条目的读取权来自 app 自身的媒体权限，
   * 不占用持久授权配额，也就不会因配额被回收而失效。
   */
  fun handleSelectedMediaStoreItems(items: List<MediaStoreItem>) {
    if (items.isEmpty()) {
      return
    }

    viewModelScope.launch(Dispatchers.IO) {
      val infos = items.map { item ->
        VideoEntity(
          uri = item.uri.toString(),
          name = item.name,
          isVideo = item.isVideo,
          size = item.size,
          durationMs = item.durationMs
        )
      }

//      subtitleController.resolveSubtitles(infos)
      withContext(Dispatchers.Main) {
        playbackListController.addToFront(infos)
      }
    }
  }
}
