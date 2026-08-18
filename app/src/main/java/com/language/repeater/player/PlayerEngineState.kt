package com.language.repeater.player

/**
 * 播放状态 — 纯业务层定义，不依赖任何播放器 SDK
 */
sealed interface PlaybackState {
  data object Idle : PlaybackState
  data object Buffering : PlaybackState
  data object Ready : PlaybackState
  data object Ended : PlaybackState
  data class Error(val message: String?) : PlaybackState
}

/**
 * 播放器引擎状态 — 业务层唯一关心的播放状态
 */
data class PlayerEngineState(
  val playbackState: PlaybackState = PlaybackState.Idle,
  val isPlaying: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val bufferedPositionMs: Long = 0L,
  val speed: Float = 1f,
  val currentMediaTitle: String = "",
)
