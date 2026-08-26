package com.language.repeater.player

data class PlaybackError(
  val code: String? = null,
  val message: String? = null
)

data class PlaybackState(
  val currentMedia: PlaybackMedia? = null,
  // 播放意图：正在出声或正在缓冲都为 true，缓冲期间 UI 不应回到暂停态
  val isPlaying: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val speed: Float = 1f,
  val error: PlaybackError? = null
)
