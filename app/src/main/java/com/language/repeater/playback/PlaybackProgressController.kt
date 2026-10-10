package com.language.repeater.playback

import com.language.repeater.datastore.DsManager
import com.language.repeater.datastore.PlayProgress
import com.language.repeater.player.PlaybackPlayer
import com.language.repeater.player.PlaybackEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 播放进度持久化。全应用只有这里写 [PlayProgress]。
 *
 * 落盘时机：媒体切换、暂停、seek 完成、每 5 秒周期，另提供非协程环境下的手动入口。
 * 播放器被清空时抹掉记录。
 *
 * 位置放在 DataStore 而非数据库，因为它是秒级高频写入，会让读同一张表的
 * 播放列表 Flow 不断失效重查。全局只保留当前条目的一份进度，
 * 切换条目一律从头播放。
 */
class PlaybackProgressController(
  private val player: PlaybackPlayer,
  private val scope: CoroutineScope
) {
  init {
    // 播放→暂停时保存当前位置
    scope.launch {
      var wasPlaying = false
      player.state.collect { state ->
        if (wasPlaying && !state.isPlaying) {
          saveCurrentPosition()
        }
        wasPlaying = state.isPlaying
      }
    }

    // 事件驱动保存
    scope.launch {
      player.events.collect { event ->
        when (event) {
          is PlaybackEvent.MediaChanged -> {
            val media = event.media
            if (media == null) {
              DsManager.setPlayProgress(PlayProgress.EMPTY)
            } else {
              savePosition(media.mediaId, event.positionMs)
            }
          }

          is PlaybackEvent.SeekCompleted -> {
            savePositionIfCurrent(event.mediaId, event.positionMs)
          }

          else -> Unit
        }
      }
    }

    // 周期性保存
    scope.launch {
      while (true) {
        delay(POSITION_SAVE_INTERVAL_MS)
        if (player.state.value.isPlaying) {
          saveCurrentPosition()
        }
      }
    }
  }

  /** 读取播放器当前状态并保存位置，适用于协程上下文。 */
  suspend fun saveCurrentPosition() {
    val mediaId = player.state.value.currentMedia?.mediaId ?: return
    savePosition(mediaId, player.progress.value.positionMs)
  }

  /** 非协程上下文的手动保存入口（如任务卡移除 / 服务销毁），立即派发到 IO 线程。 */
  fun saveCurrentPositionNow() {
    val mediaId = player.state.value.currentMedia?.mediaId ?: return
    val snapshotPositionMs = player.progress.value.positionMs
    scope.launch(Dispatchers.IO) {
      savePosition(mediaId, snapshotPositionMs)
    }
  }

  private suspend fun savePositionIfCurrent(mediaId: String, positionMs: Long) {
    if (player.state.value.currentMedia?.mediaId != mediaId) {
      return
    }
    savePosition(mediaId, positionMs)
  }

  /** 位置连同它所属的 mediaId 一起写，避免切歌瞬间的延迟写入挂到新条目上。 */
  private suspend fun savePosition(mediaId: String, positionMs: Long) {
    DsManager.setPlayProgress(PlayProgress(mediaId, positionMs))
  }

  private companion object {
    const val POSITION_SAVE_INTERVAL_MS = 5_000L
  }
}
