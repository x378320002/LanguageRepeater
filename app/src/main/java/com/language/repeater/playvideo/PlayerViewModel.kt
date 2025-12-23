package com.language.repeater.playvideo

import kotlin.collections.map
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import com.language.repeater.playcore.PlaybackConnection
import com.language.repeater.playvideo.history.HistoryManager
import com.language.repeater.playvideo.model.VideoEntity
import com.language.repeater.playvideo.model.toMediaItem
import com.language.repeater.playcore.SleepTimerManager
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
  companion object {
    private const val TAG = "wangzixu_PlayerViewModel"
  }

  private val connection = PlaybackConnection.getInstance(application)

  // --- 1. 直接透传 Repository 的数据流 ---
  val playerState = connection.playerState
  val currentPositionSeconds = connection.currentPositionSeconds
  val currentPosition = connection.currentPosition
  val currentMediaItem = connection.currentMediaItem
  val mediaItemCount = connection.mediaItemCount
  val playlistRefreshEvent = connection.playlistRefreshEvent
  val playerRepeatMode = connection.playerRepeatMode

  // 业务数据流
  val pcmLoaderStateFlow = connection.pcmLoaderStateFlow
  val allWaveDataFlow = connection.allWaveDataFlow
  val sentencesFlow = connection.sentencesFlow
  val repeatable = connection.repeatable
  val curAbSentenceFlow = connection.curAbSentenceFlow

  // --- 2. UI 逻辑封装 (防抖状态) ---
  // 启动模式必须用SharingStarted.Eagerly, 否则.value取值会不对
  val isUiPlaying = combine(
    connection.isPlaying,
    connection.playbackState,
    connection.playWhenReady
  ) { isPlaying, state, playWhenReady ->
    if (isPlaying) return@combine true
    if (state == Player.STATE_BUFFERING && playWhenReady) return@combine true
    false
  }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  init {
    // ViewModel 初始化时，尝试连接/启动服务
    connection.connect()
  }

  /**
   * 检查连接状态，未连接则尝试重连
   * @return true 表示当前已连接，false 表示正在重连中(操作需稍后重试)
   */
  private fun ensureConnected(): Boolean {
    if (getPlayer() == null) {
      connection.connect()
      // 这里可以根据需求决定是否弹 Toast
      // ToastUtil.toast("服务重启中...")
      return false
    }
    return true
  }

  fun getPlayer(): Player? = connection.playerState.value

  fun togglePlayPause() {
    if (!ensureConnected()) return

    // 1. 从 Repository 获取最底层的真实数据
    // 注意：这里读取的是 connection.isPlaying.value，它是热的，绝对准确
    val isRealPlaying = connection.isPlaying.value
    val isBuffering = connection.playbackState.value == Player.STATE_BUFFERING
    val playWhenReady = connection.playWhenReady.value

    // 2. 复用判断逻辑 (与 isUiPlaying 的计算逻辑保持一致，但数据源不同)
    // 含义：系统认为"应该播放"的状态
    val shouldBePlaying = isRealPlaying || (isBuffering && playWhenReady)

    // 3. 执行操作
    if (shouldBePlaying) {
      connection.pause()
    } else {
      connection.play()
    }
  }

  fun play() {
    connection.play()
  }

  fun pause() {
    connection.pause()
  }

  fun playItem(index: Int) {
    connection.seekToDefaultPosition(index)
    connection.play()
  }

  fun deleteItem(index: Int) {
    connection.removeMediaItem(index)
  }

  fun addPlayList(list: List<VideoEntity>, isReplace: Boolean) {
    val player = getPlayer() ?: return
    val items = list.map { it.toMediaItem() }
    if (isReplace) {
      player.setMediaItems(items)
      player.prepare()
      player.play()
    } else {
      player.addMediaItems(0, items)
      player.seekTo(0, C.TIME_UNSET)
      player.prepare()
      player.play()
    }
  }

  // 复读控制直接调 Repository
  fun toggleRepeat() = connection.toggleRepeat()
  fun seekToNextSentence() = connection.seekToNextSentence()
  fun seekToPreviousSentence() = connection.seekToPreviousSentence()

  // 手动选字幕
  fun onSubtitleSelected(uri: Uri) = connection.updateSubtitle(uri)

  fun updateAbSentence(position: Float) {
    connection.updateAbSentenceByTime(position)
  }

  fun loadSentenceData(forceUseVad: Boolean = false) {
    connection.forceLoadCurrentSentences(forceUseVad)
  }

  /**
   * 分割当前播放位置的句子
   * UI 层的"拆分"按钮点击时调用此方法
   */
  fun splitCurrentSentence() {
    val result = connection.splitCurrentSentence()

    // 根据返回的枚举做具体提示
    when (result) {
      PlaybackConnection.SplitResult.SUCCESS -> {
        ToastUtil.toast("分割成功")
      }
      PlaybackConnection.SplitResult.NO_SENTENCE -> {
        ToastUtil.toast("当前位置没有可分割的句子")
      }
      PlaybackConnection.SplitResult.TOO_SHORT -> {
        ToastUtil.toast("当前句子太短，无法继续分割")
      }
      PlaybackConnection.SplitResult.TOO_CLOSE_TO_EDGE -> {
        ToastUtil.toast("距离句子边缘太近，无法分割")
      }
    }
  }

  /**
   * 用户拖动句子边界结束，触发保存和合并逻辑
   */
  fun onSentenceDragEnd() {
    connection.saveAndMergeSentences()
  }

  // 2. 切换逻辑
  fun togglePlayerRepeatMode() {
    val currentMode = playerRepeatMode.value
    // 定义切换顺序: 列表不循环 -> 列表循环 -> 单曲循环 -> 列表不循环
    val nextMode = when (currentMode) {
      Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
      Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
      Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
      else -> Player.REPEAT_MODE_OFF
    }
    connection.setPlayerRepeatMode(nextMode)
  }

  fun deleteCurrentSentence() {
    connection.deleteCurSentence()
  }

  // --- 历史记录 ---
  // 直接暴露 Flow 给 UI 监听
  fun getHistoryFlow() = HistoryManager.observeHistory(getApplication())

  // 播放历史记录中的某一项
  fun playHistoryItem(item: VideoEntity) {
    val player = getPlayer() ?: return
    val currentId = connection.currentMediaItem.value?.mediaId

    // 1. 如果就是当前播放的，直接跳转进度并播放
    if (item.id == currentId) {
      return
    }

    // 2. 检查播放列表中是否已存在该视频
    var existingIndex = -1
    for (i in 0 until player.mediaItemCount) {
      if (player.getMediaItemAt(i).mediaId == item.id) {
        existingIndex = i
        break
      }
    }

    if (existingIndex != -1) {
      // 3. 如果存在，直接切过去
      player.seekTo(existingIndex, item.positionMs)
      player.play()
    } else {
      // 4. 如果不存在，添加到当前播放位置的下一首，并播放
      // 这样既保留了原列表，又让用户立刻看到了点击的历史记录
      val mediaItem = item.toMediaItem()
      val nextIndex = if (player.currentMediaItemIndex == C.INDEX_UNSET) 0 else player.currentMediaItemIndex + 1
      player.addMediaItem(nextIndex, mediaItem)
      player.seekTo(nextIndex, item.positionMs)
      player.prepare()
      player.play()
    }
  }

  // 下一首播放
  fun addNext(item: VideoEntity) {
    val player = getPlayer() ?: return
    val mediaItem = item.toMediaItem()
    if (player.currentMediaItemIndex == C.INDEX_UNSET) {
      player.setMediaItem(mediaItem)
      player.prepare()
      player.play()
    } else {
      val nextIndex = player.currentMediaItemIndex + 1
      player.addMediaItem(nextIndex, mediaItem)
    }
    ToastUtil.toast("已添加到下一首播放")
  }

  // 添加到末尾
  fun addToEnd(item: VideoEntity) {
    val player = getPlayer() ?: return
    val mediaItem = item.toMediaItem()
    if (player.currentMediaItemIndex == C.INDEX_UNSET) {
      player.setMediaItem(mediaItem)
      player.prepare()
      player.play()
    } else {
      val nextIndex = player.currentMediaItemIndex + 1
      player.addMediaItem(nextIndex, mediaItem)
    }
    ToastUtil.toast("已添加到播放列表末尾")
  }

  // 删除历史记录
  fun deleteHistory(item: VideoEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      HistoryManager.deleteHistory(getApplication(), item)
    }
  }

  fun startSleepTimer(seconds: Long) {
    SleepTimerManager.startTimer(seconds) {
      // 时间到了执行的操作：调用 Repository 暂停播放
      // 注意：这里是在主线程回调的，且 connection.pause() 内部安全
      connection.pause()
      ToastUtil.toast("睡眠定时已结束，停止播放")
    }
  }

  fun stopSleepTimer() {
    SleepTimerManager.stopTimer()
  }
}