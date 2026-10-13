package com.language.repeater.playbacklist

import com.language.repeater.datastore.DsManager
import com.language.repeater.player.PlaybackPlayer
import com.language.repeater.player.PlaybackEvent
import com.language.repeater.db.VideoEntity
import com.language.repeater.db.toPlaybackMedia
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

  val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

  val items: StateFlow<List<VideoEntity>> = repository.items.stateIn(
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
        .takeIf { mediaId -> mediaId != 0L }
        ?.let { mediaId -> repository.get(mediaId) }
      val item = savedItem ?: repository.first() ?: return
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

      // 先落库拿到自增 id 与完整记录，再据此重写顺序：order 里的 id 必须已存在于 video_info_table
      val inserted = repository.insertAndResolveIds(videos)
      val currentOrder = items.value.map { item -> item.id }
      val addedIds = inserted
        .map { entity -> entity.id }
        .filterNot { mediaId -> mediaId in currentOrder }
      repository.saveOrder(
        order = if (addedIds.isEmpty()) {
          emptyList()
        } else {
          addedIds + currentOrder
        }
      )

      val firstMedia = inserted.firstOrNull()
      if (firstMedia != null && player.state.value.currentMedia?.mediaId != firstMedia.id) {
        player.prepare(firstMedia.toPlaybackMedia(), playWhenReady = true)
      }
    }
  }

  suspend fun select(mediaId: Long) {
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

  suspend fun remove(mediaId: Long) {
    operationMutex.withLock {
      val wasPlaying = player.state.value.isPlaying
      val isCurrent = player.state.value.currentMedia?.mediaId == mediaId
      val replacement = if (isCurrent) {
        val list = items.value
        list.nextOf(mediaId) ?: list.previousOf(mediaId)
      } else {
        null
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

  suspend fun moveToFirst(mediaId: Long) {
    operationMutex.withLock {
      val currentOrder = items.value.map { item -> item.id }
      if (currentOrder.firstOrNull() == mediaId || mediaId !in currentOrder) {
        return
      }
      repository.saveOrder(
        listOf(mediaId) + currentOrder.filterNot { item -> item == mediaId }
      )
    }
  }

  suspend fun handleMediaEnded(mediaId: Long) {
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
          } else if (next.id == mediaId) {
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

private fun List<VideoEntity>.itemOf(mediaId: Long?): VideoEntity? {
  if (mediaId == null) {
    return null
  }
  return firstOrNull { item -> item.id == mediaId }
}

private fun List<VideoEntity>.nextOf(mediaId: Long): VideoEntity? {
  val index = indexOfFirst { item -> item.id == mediaId }
  if (index < 0) {
    return null
  }
  return getOrNull(index + 1)
}

private fun List<VideoEntity>.previousOf(mediaId: Long): VideoEntity? {
  val index = indexOfFirst { item -> item.id == mediaId }
  if (index <= 0) {
    return null
  }
  return get(index - 1)
}
