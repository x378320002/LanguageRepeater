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

/**
 * 在播放与暂停之间切换。
 *
 * 判定依据是 [PlaybackState.isPlaying]（含缓冲态），与 UI 上显示的图标保持一致，
 * 避免出现「图标是播放中、点一下却又调 play()」的错位。
 */
fun PlaybackPlayer.togglePlayPause() {
  if (state.value.isPlaying) {
    pause()
  } else {
    play()
  }
}
