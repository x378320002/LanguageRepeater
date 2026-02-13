package com.language.repeater.playvideo

import android.R.attr.mode
import android.app.Application
import android.net.Uri
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.language.repeater.MyApp
import com.language.repeater.dataStore
import com.language.repeater.db.historyDao
import com.language.repeater.playcore.PlaybackCore
import com.language.repeater.playvideo.model.VideoEntity
import com.language.repeater.utils.DataStoreKey.KEY_AB_REPEATED
import com.language.repeater.utils.DataStoreKey.KEY_EDIT_SEN_MODE
import com.language.repeater.utils.DataStoreKey.KEY_PLAYER_PLAY_MODE
import com.language.repeater.utils.observe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
  companion object {
    private const val TAG = "wangzixu_PlayerViewModel"
  }

  //private val _editMode = MutableStateFlow<Boolean>(false)
  //val editMode = _editMode.asStateFlow()
  val editMode = application.dataStore.data.map {
    it[KEY_EDIT_SEN_MODE] ?: false
  }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  private val playbackCore = PlaybackCore.getInstance(application)

  // --- 1. 直接透传 Repository 的数据流 ---
  val playerInstance = playbackCore.playerInstance
  val playSpeedState = playbackCore.playSpeed
  val currentPositionSeconds = playbackCore.currentPositionSeconds
  val currentPosition = playbackCore.currentPosition
  val currentMediaItem = playbackCore.currentMediaItem
  val mediaItemCount = playbackCore.mediaItemCount
  val playlistRefreshEvent = playbackCore.playlistRefreshEvent
  //val isPlayingState = playbackCore.isPlaying

  // 业务数据流
  val pcmLoaderStateFlow = playbackCore.pcmLoaderStateFlow
  val allWaveDataFlow = playbackCore.allWaveDataFlow
  val sentencesFlow = playbackCore.sentencesFlow
  val repeatable = playbackCore.repeatAb
  val curAbSentenceFlow = playbackCore.curAbSentenceFlow

  // --- 2. UI 逻辑封装 (防抖状态) ---
  // 启动模式必须用SharingStarted.Eagerly, 否则.value取值会不对
  val isUiPlaying = combine(
    playbackCore.isPlaying,
    playbackCore.playbackState,
    playbackCore.playWhenReady
  ) { isPlaying, state, playWhenReady ->
    if (isPlaying) return@combine true
    if (state == Player.STATE_BUFFERING && playWhenReady) return@combine true
    false
  }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  init {
    // ViewModel 初始化时，尝试连接/启动服务
    playbackCore.connect()
  }

  /**
   * 检查连接状态，未连接则尝试重连
   * @return true 表示当前已连接，false 表示正在重连中(操作需稍后重试)
   */
  private fun ensureConnected(): Boolean {
    if (getPlayer() == null) {
      playbackCore.connect()
      // 这里可以根据需求决定是否弹 Toast
      // ToastUtil.toast("服务重启中...")
      return false
    }
    return true
  }

  fun getPlayer(): Player? = playbackCore.playerInstance.value

  fun togglePlayPause() {
    if (!ensureConnected()) return

    // 1. 从 Repository 获取最底层的真实数据
    // 注意：这里读取的是 connection.isPlaying.value，它是热的，绝对准确
    val isRealPlaying = playbackCore.isPlaying.value
    val isBuffering = playbackCore.playbackState.value == Player.STATE_BUFFERING
    val playWhenReady = playbackCore.playWhenReady.value

    // 2. 复用判断逻辑 (与 isUiPlaying 的计算逻辑保持一致，但数据源不同)
    // 含义：系统认为"应该播放"的状态
    val shouldBePlaying = isRealPlaying || (isBuffering && playWhenReady)

    // 3. 执行操作
    if (shouldBePlaying) {
      playbackCore.pause()
    } else {
      playbackCore.play()
    }
  }

  fun play() {
    playbackCore.play()
  }

  fun pause() {
    playbackCore.pause()
  }

  fun playItem(index: Int) {
    playbackCore.seekToItem(index)
    playbackCore.play()
  }

  fun deleteItem(index: Int) {
    playbackCore.removeMediaItem(index)
  }

  // 复读控制直接调 Repository
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

  /**
   * 分割当前播放位置的句子
   * UI 层的"拆分"按钮点击时调用此方法
   */
  fun splitCurrentSentence() {
    playbackCore.splitCurrentSentence()
  }

  /**
   * 用户拖动句子边界结束，触发保存和合并逻辑
   */
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
  fun getHistoryFlow() = application.historyDao.getAllHistory()

  fun addPlayList(
    list: List<VideoEntity>,
    isReplace: Boolean
  ) {
    playbackCore.addPlayList(list, isReplace)
  }

  // 播放历史记录中的某一项
  fun playHistoryItem(item: VideoEntity) {
    playbackCore.addAndPlay(item)
  }

  // 下一首播放
  fun addNext(item: VideoEntity) {
    //val player = getPlayer() ?: return
    //val mediaItem = item.toMediaItem()
    //if (player.currentMediaItemIndex == C.INDEX_UNSET) {
    //  player.setMediaItem(mediaItem)
    //  player.prepare()
    //  player.play()
    //} else {
    //  val nextIndex = player.currentMediaItemIndex + 1
    //  player.addMediaItem(nextIndex, mediaItem)
    //}
    //ToastUtil.toast("已添加到下一首播放")
  }

  // 添加到末尾
  fun addToEnd(item: VideoEntity) {
    //val player = getPlayer() ?: return
    //ToastUtil.toast("已添加到播放列表末尾")
  }

  // 删除历史记录
  fun deleteHistory(item: VideoEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      //HistoryManager.deleteHistory(getApplication(), item)
      application.historyDao.deleteByVideoId(item.id)
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
    //_editMode.value = edit
    viewModelScope.launch {
      application.dataStore.edit {
        it[KEY_EDIT_SEN_MODE] = edit
      }
    }
  }
}