package com.language.repeater.playbacklist

import com.language.repeater.datastore.DsManager
import com.language.repeater.player.PlaybackMedia
import com.language.repeater.player.PlaybackPlayer
import com.language.repeater.player.PlaybackEvent
import com.language.repeater.playvideo.model.VideoEntity
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

  /** 历史默认值为 Media3 REPEAT_MODE_ONE，因此初始值保持单曲循环语义。 */
  val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

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

  suspend fun restore() {
    operationMutex.withLock {
      val currentMediaId = player.state.value.currentMedia?.mediaId
      if (currentMediaId != null) {
        // Service 重建时播放器会自行重新挂载业务媒体，仅校正可能过期的持久化 ID。
        val savedMediaId = DsManager.get(DsManager.KEY_CURRENT_PLAY_ID, "")
        if (savedMediaId != currentMediaId) {
          saveCurrentMediaId(currentMediaId)
        }
        return
      }

      val savedMediaId = DsManager.get(DsManager.KEY_CURRENT_PLAY_ID, "")
      val savedItem = savedMediaId.takeIf { mediaId -> mediaId.isNotEmpty() }
        ?.let { mediaId -> repository.get(mediaId) }
      val item = savedItem ?: repository.first()
      if (item == null) {
        if (savedMediaId.isNotEmpty()) {
          clearCurrentMediaId()
        }
        return
      }
      // 仅恢复时使用上次保存的播放位置
      prepare(item, playWhenReady = false, startPositionMs = item.savedPositionMs)
    }
  }

  suspend fun replace(
    items: List<VideoEntity>,
    selectedMediaId: String? = null
  ) {
    operationMutex.withLock {
      val uniqueItems = items.distinctBy { item -> item.id }
      val resolvedSelectedMediaId = selectedMediaId ?: uniqueItems.firstOrNull()?.id
      val shouldPlay = player.state.value.isPlaying
      repository.replace(uniqueItems)
      val item = resolvedSelectedMediaId?.let { mediaId -> repository.get(mediaId) }
        ?: repository.first()
      if (item == null) {
        clearCurrentMediaId()
        player.clear()
      } else {
        prepare(item, shouldPlay)
      }
    }
  }

  suspend fun addToFront(items: List<VideoEntity>) {
    operationMutex.withLock {
      repository.addToFront(items)
      if (player.state.value.currentMedia == null) {
        repository.first()?.let { item -> prepare(item, true) }
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
      repository.get(mediaId)?.let { item -> prepare(item, true) }
    }
  }

  suspend fun playNext() {
    operationMutex.withLock {
      val mediaId = player.state.value.currentMedia?.mediaId ?: return
      val next = repository.nextOf(mediaId) ?: repository.first()
      if (next != null && next.mediaId != mediaId) {
        prepare(next, true)
      }
    }
  }

  suspend fun playPrevious() {
    operationMutex.withLock {
      val mediaId = player.state.value.currentMedia?.mediaId ?: return
      val previous = repository.previousOf(mediaId) ?: return
      prepare(previous, true)
    }
  }

  suspend fun remove(mediaId: String) {
    operationMutex.withLock {
      val wasPlaying = player.state.value.isPlaying
      val isCurrent = player.state.value.currentMedia?.mediaId == mediaId
      val replacement = if (isCurrent) {
        repository.nextOf(mediaId) ?: repository.previousOf(mediaId)
      } else {
        null
      }
      repository.remove(mediaId)
      if (!isCurrent) {
        return
      }
      if (replacement == null) {
        clearCurrentMediaId()
        player.clear()
      } else {
        prepare(replacement, wasPlaying)
      }
    }
  }

  suspend fun moveToFirst(mediaId: String) {
    repository.moveToFirst(mediaId)
  }

  suspend fun moveBefore(mediaId: String, targetMediaId: String) {
    repository.moveBefore(mediaId, targetMediaId)
  }

  suspend fun handleMediaEnded(mediaId: String) {
    operationMutex.withLock {
      if (player.state.value.currentMedia?.mediaId != mediaId) {
        return
      }
      when (_playbackMode.value) {
        PlaybackMode.Sequential -> {
          val next = repository.nextOf(mediaId)
          if (next == null) {
            player.seekTo(0L)
            player.pause()
          } else {
            prepare(next, true)
          }
        }

        PlaybackMode.RepeatAll -> {
          val next = repository.nextOf(mediaId) ?: repository.first()
          if (next == null) {
            player.seekTo(0L)
            player.pause()
          } else if (next.mediaId == mediaId) {
            player.seekTo(0L)
            player.play()
          } else {
            prepare(next, true)
          }
        }

        PlaybackMode.RepeatOne -> {
          player.seekTo(0L)
          player.play()
        }
      }
    }
  }

  private suspend fun clearCurrentMediaId() {
    saveCurrentMediaId("")
  }

  private suspend fun saveCurrentMediaId(mediaId: String) {
    DsManager.set(DsManager.KEY_CURRENT_PLAY_ID, mediaId)
  }

  private suspend fun prepare(
    item: PlaybackListItem,
    playWhenReady: Boolean,
    startPositionMs: Long = 0L
  ) {
    saveCurrentMediaId(item.mediaId)
    player.prepare(
      media = PlaybackMedia(
        mediaId = item.mediaId,
        uri = item.uri,
        title = item.title,
        subtitleUri = item.subtitleUri
      ),
      startPositionMs = startPositionMs,
      playWhenReady = playWhenReady
    )
  }
}
