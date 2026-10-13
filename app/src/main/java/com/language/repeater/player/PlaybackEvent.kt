package com.language.repeater.player

sealed interface PlaybackEvent {
  /**
   * 当前媒体换成了另一个，只在媒体 id 确实变化时发射。
   *
   * [media] 为 null 表示播放器被清空，此时 [positionMs] 无意义。
   * [positionMs] 是新媒体的起播位置，恢复上次播放时不为 0。
   *
   * 同一媒体调整播放位置不属于媒体变化，那种情况发射的是 [SeekCompleted]。
   */
  data class MediaChanged(
    val media: PlaybackMedia?,
    val positionMs: Long
  ) : PlaybackEvent

  data class MediaEnded(
    val mediaId: Long
  ) : PlaybackEvent

  data class SeekCompleted(
    val mediaId: Long,
    val positionMs: Long
  ) : PlaybackEvent
}
