package com.language.repeater.playback

import com.language.repeater.datastore.DsManager
import com.language.repeater.datastore.PlayProgress
import com.language.repeater.player.PlaybackPlayer
import com.language.repeater.player.PlaybackEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlaybackProgressController(
  private val player: PlaybackPlayer,
  private val scope: CoroutineScope
) {
  init {
    scope.launch {
      var wasPlaying = false
      player.state.collect { state ->
        if (wasPlaying && !state.isPlaying) {
          saveCurrentPosition()
        }
        wasPlaying = state.isPlaying
      }
    }

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

    scope.launch {
      while (true) {
        delay(POSITION_SAVE_INTERVAL_MS)
        if (player.state.value.isPlaying) {
          saveCurrentPosition()
        }
      }
    }
  }

  suspend fun saveCurrentPosition() {
    val mediaId = player.state.value.currentMedia?.mediaId ?: return
    savePosition(mediaId, player.progress.value.positionMs)
  }

  fun saveCurrentPositionNow() {
    val mediaId = player.state.value.currentMedia?.mediaId ?: return
    val snapshotPositionMs = player.progress.value.positionMs
    scope.launch(Dispatchers.IO) {
      savePosition(mediaId, snapshotPositionMs)
    }
  }

  private suspend fun savePositionIfCurrent(mediaId: Long, positionMs: Long) {
    if (player.state.value.currentMedia?.mediaId != mediaId) {
      return
    }
    savePosition(mediaId, positionMs)
  }

  private suspend fun savePosition(mediaId: Long, positionMs: Long) {
    DsManager.setPlayProgress(PlayProgress(mediaId, positionMs))
  }

  private companion object {
    const val POSITION_SAVE_INTERVAL_MS = 5_000L
  }
}
