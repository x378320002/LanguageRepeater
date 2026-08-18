package com.language.repeater.player

import kotlinx.coroutines.flow.StateFlow

/**
 * 播放列表项 — 业务层定义的播放列表条目
 */
data class PlaylistItem(
  val id: String,
  val title: String,
  val uri: String,
  val durationMs: Long = 0L,
  val isPlaying: Boolean = false,
  val isCurrent: Boolean = false,
)

/**
 * 播放器引擎抽象接口 — 业务层唯一依赖的播放能力契约
 *
 * 实现方：
 * - Media3PlayerEngine (当前)
 * - TencentPlayerEngine (未来)
 * - AVPlayerEngine (iOS, KMP)
 */
interface PlayerEngine {

  /**
   * 播放状态流 — 业务层通过订阅它获取所有播放状态变化
   */
  val state: StateFlow<PlayerEngineState>

  /**
   * 播放列表流
   */
  val playlist: StateFlow<List<PlaylistItem>>

  /**
   * 当前播放列表项索引
   */
  val currentIndex: StateFlow<Int>

  /**
   * 设置媒体源
   */
  fun setSource(source: MediaSource)

  /**
   * 播放
   */
  fun play()

  /**
   * 暂停
   */
  fun pause()

  /**
   * 跳转到指定位置
   */
  fun seekTo(positionMs: Long)

  /**
   * 设置播放速度
   */
  fun setSpeed(speed: Float)

  /**
   * 切换到播放列表指定项
   */
  fun seekToItem(index: Int)

  /**
   * 移动播放列表项位置
   */
  fun moveItem(from: Int, to: Int)

  /**
   * 删除播放列表项
   */
  fun removeItem(index: Int)

  /**
   * 释放资源
   */
  fun release()
}
