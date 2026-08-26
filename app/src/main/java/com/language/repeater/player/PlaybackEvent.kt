package com.language.repeater.player

sealed interface PlaybackEvent {
  data class MediaChanged(
    val media: PlaybackMedia
  ) : PlaybackEvent

  data class MediaEnded(
    val mediaId: String
  ) : PlaybackEvent

  data class SeekCompleted(
    val mediaId: String,
    val positionMs: Long
  ) : PlaybackEvent
}
