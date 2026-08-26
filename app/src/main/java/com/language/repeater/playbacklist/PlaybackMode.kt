package com.language.repeater.playbacklist

/**
 * 业务播放模式，不直接依赖 Media3 的 repeatMode。
 * 持久化值沿用历史 Media3 常量：0=顺序、1=单曲循环、2=列表循环。
 */
enum class PlaybackMode(val persistedValue: Int) {
  Sequential(0),
  RepeatOne(1),
  RepeatAll(2);

  fun next(): PlaybackMode {
    return when (this) {
      Sequential -> RepeatOne
      RepeatOne -> RepeatAll
      RepeatAll -> Sequential
    }
  }

  companion object {
    fun fromPersistedValue(value: Int): PlaybackMode {
      return when (value) {
        1 -> RepeatOne
        2 -> RepeatAll
        else -> Sequential
      }
    }
  }
}
