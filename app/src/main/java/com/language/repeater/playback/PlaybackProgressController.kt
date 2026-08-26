package com.language.repeater.playback

import com.language.repeater.db.VideoInfoDao
import com.language.repeater.player.PlaybackPlayer
import com.language.repeater.player.PlaybackEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 位置持久化协调器。监听播放器状态和事件，在暂停、seek 完成、播放失败
 * 以及周期性时机将当前播放位置写入 VideoInfoDao；同时提供手动保存入口。
 */
class PlaybackProgressController(
  private val player: PlaybackPlayer,
  private val videoInfoDao: VideoInfoDao,
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
    val state = player.state.value
    val mediaId = state.currentMedia?.mediaId ?: return
    videoInfoDao.updatePosition(mediaId, state.positionMs)
  }

  /** 非协程上下文的手动保存入口（如 Service.onDestroy），立即派发到 IO 线程。 */
  fun saveCurrentPositionNow(positionMs: Long? = null) {
    val state = player.state.value
    val mediaId = state.currentMedia?.mediaId ?: return
    val snapshotPositionMs = positionMs ?: state.positionMs
    scope.launch(Dispatchers.IO) {
      videoInfoDao.updatePosition(mediaId, snapshotPositionMs)
    }
  }

  private suspend fun savePositionIfCurrent(mediaId: String, positionMs: Long) {
    if (player.state.value.currentMedia?.mediaId != mediaId) {
      return
    }
    videoInfoDao.updatePosition(mediaId, positionMs)
  }

  private companion object {
    const val POSITION_SAVE_INTERVAL_MS = 5_000L
  }
}
