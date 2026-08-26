package com.language.repeater.player

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface PlaybackPlayer {
  val state: StateFlow<PlaybackState>
  val events: SharedFlow<PlaybackEvent>

  fun prepare(
    media: PlaybackMedia,
    startPositionMs: Long = 0L,
    playWhenReady: Boolean = false
  )

  fun play()

  fun pause()

  fun seekTo(positionMs: Long)

  fun setSpeed(speed: Float)

  fun setSubtitle(subtitleUri: String?)

  fun clear()
}
