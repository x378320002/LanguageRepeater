package com.language.repeater.playbacklist

import androidx.core.net.toUri
import com.language.repeater.datastore.DsManager
import com.language.repeater.player.PlaybackMedia
import com.language.repeater.player.PlaybackPlayer
import com.language.repeater.player.PlaybackEvent
import com.language.repeater.playvideo.model.VideoEntity
import com.language.repeater.playvideo.model.toPlaybackMedia
import com.language.repeater.utils.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlaybackListController(
  private val repository: PlaybackListRepository,
  private val player: PlaybackPlayer,
  private val scope: CoroutineScope
) {
  private val operationMutex = Mutex()
  private val _playbackMode = MutableStateFlow(PlaybackMode.RepeatOne)

  /** 当前播放模式，初始值与 Media3 的默认值 REPEAT_MODE_ONE 对齐。 */
  val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

  /** 完整列表，随数据库变化自动更新。取相邻条目、判断顺序都基于它。 */
  val items: StateFlow<List<PlaybackListItem>> = repository.items.stateIn(
    scope,
    SharingStarted.Eagerly,
    emptyList()
  )

  init {
    scope.launch(Dispatchers.IO) {
      val persistedMode = DsManager.get(
        DsManager.KEY_PLAYER_PLAY_MODE,
        PlaybackMode.RepeatOne.persistedValue
      )
      _playbackMode.value = PlaybackMode.fromPersistedValue(persistedMode)
    }

    // 监听播放结束事件，按播放模式决定切歌或单曲循环
    scope.launch(Dispatchers.IO) {
      player.events.collect { event ->
        if (event is PlaybackEvent.MediaEnded) {
          handleMediaEnded(event.mediaId)
        }
      }
    }
  }

  fun cyclePlaybackMode() {
    val nextMode = _playbackMode.value.next()
    _playbackMode.value = nextMode
    scope.launch(Dispatchers.IO) {
      DsManager.set(DsManager.KEY_PLAYER_PLAY_MODE, nextMode.persistedValue)
    }
  }

  /**
   * 按 DataStore 里的进度加载上次播放的条目，加载后保持暂停。
   *
   * 只在进程启动时调用一次，此时 [items] 尚未加载，因此按 id 直接查库。
   * 记录的条目已不在列表时退化到首条，并从头开始播。
   */
  suspend fun restore() {
    operationMutex.withLock {
      val progress = DsManager.getPlayProgress()
      val savedItem = progress.mediaId
        .takeIf { mediaId -> mediaId.isNotEmpty() }
        ?.let { mediaId -> repository.get(mediaId) }
      val item = savedItem ?: repository.first() ?: return
      // 记录的位置只属于 savedItem，退化到首条时从 0 开始
      val startPositionMs = if (savedItem == null) {
        0L
      } else {
        progress.positionMs
      }
      player.prepare(
        media = item.toPlaybackMedia(),
        startPositionMs = startPositionMs,
        playWhenReady = false
      )
    }
  }

  suspend fun addToFront(videos: List<VideoEntity>) {
    operationMutex.withLock {
      if (videos.isEmpty()) {
        return
      }

      // 已在列表里的保持原位，只把没见过的排到队首
      val currentOrder = items.value.map { item -> item.mediaId }
      val newMediaIds = videos
        .map { video -> video.id }
        .filterNot { mediaId -> mediaId in currentOrder }
      repository.saveOrder(
        order = if (newMediaIds.isEmpty()) {
          emptyList()
        } else {
          newMediaIds + currentOrder
        },
        videos = videos
      )

      // 播放器空闲时自动起播队首，也就是这批视频里的第一个
      videos.firstOrNull()?.let { video ->
        if (player.state.value.currentMedia?.mediaId != video.id) {
          player.prepare(video.toPlaybackMedia(), playWhenReady = true)
        }
      }
    }
  }

  suspend fun select(mediaId: String) {
    operationMutex.withLock {
      if (player.state.value.currentMedia?.mediaId == mediaId) {
        if (player.state.value.isPlaying) {
          player.pause()
        } else {
          player.play()
        }
        return
      }
      items.value.itemOf(mediaId)?.let { item ->
        player.prepare(item.toPlaybackMedia(), playWhenReady = true)
      }
    }
  }

  suspend fun remove(mediaId: String) {
    operationMutex.withLock {
      val wasPlaying = player.state.value.isPlaying
      val isCurrent = player.state.value.currentMedia?.mediaId == mediaId
      // 顶替者必须在删除之前算好，删掉之后就找不到相邻关系了
      val replacement = if (isCurrent) {
        val list = items.value
        list.nextOf(mediaId) ?: list.previousOf(mediaId)
      } else {
        null
      }
      val item = items.value.find { it.mediaId == mediaId }
      if (item != null) {
        FileUtil.releasePersistablePermission(item.uri.toUri())
      }
      repository.remove(mediaId)
      if (!isCurrent) {
        return
      }
      if (replacement == null) {
        player.clear()
      } else {
        player.prepare(replacement.toPlaybackMedia(), playWhenReady = wasPlaying)
      }
    }
  }

  suspend fun moveToFirst(mediaId: String) {
    operationMutex.withLock {
      val currentOrder = items.value.map { item -> item.mediaId }
      if (currentOrder.firstOrNull() == mediaId || mediaId !in currentOrder) {
        return
      }
      repository.saveOrder(
        listOf(mediaId) + currentOrder.filterNot { item -> item == mediaId }
      )
    }
  }

  suspend fun handleMediaEnded(mediaId: String) {
    operationMutex.withLock {
      if (player.state.value.currentMedia?.mediaId != mediaId) {
        return
      }
      when (_playbackMode.value) {
        PlaybackMode.Sequential -> {
          val next = items.value.nextOf(mediaId)
          if (next == null) {
            player.seekTo(0L)
            player.pause()
          } else {
            player.prepare(next.toPlaybackMedia(), playWhenReady = true)
          }
        }

        PlaybackMode.RepeatAll -> {
          val list = items.value
          val next = list.nextOf(mediaId) ?: list.firstOrNull()
          if (next == null) {
            player.seekTo(0L)
            player.pause()
          } else if (next.mediaId == mediaId) {
            player.seekTo(0L)
            player.play()
          } else {
            player.prepare(next.toPlaybackMedia(), playWhenReady = true)
          }
        }

        PlaybackMode.RepeatOne -> {
          player.seekTo(0L)
          player.play()
        }
      }
    }
  }

}

/** 按 id 定位一条，id 为空或不在列表里都返回 null。 */
private fun List<PlaybackListItem>.itemOf(mediaId: String?): PlaybackListItem? {
  if (mediaId.isNullOrEmpty()) {
    return null
  }
  return firstOrNull { item -> item.mediaId == mediaId }
}

/** 后一条，已是末尾或不在列表里都返回 null。 */
private fun List<PlaybackListItem>.nextOf(mediaId: String): PlaybackListItem? {
  val index = indexOfFirst { item -> item.mediaId == mediaId }
  if (index < 0) {
    return null
  }
  return getOrNull(index + 1)
}

/** 前一条，已是首条或不在列表里都返回 null。 */
private fun List<PlaybackListItem>.previousOf(mediaId: String): PlaybackListItem? {
  val index = indexOfFirst { item -> item.mediaId == mediaId }
  if (index <= 0) {
    return null
  }
  return get(index - 1)
}
