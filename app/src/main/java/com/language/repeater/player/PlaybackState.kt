package com.language.repeater.player

data class PlaybackError(
  val code: String? = null,
  val message: String? = null
)

/**
 * 播放器的低频状态：媒体、播放态、倍速、错误。
 *
 * 播放位置不在这里，见 [PlaybackProgress]。两者分开是因为位置每 50ms 刷新一次，
 * 混在一起会让只关心播放态的观察者跟着高频失效。
 */
data class PlaybackState(
  val currentMedia: PlaybackMedia? = null,
  // 播放意图：正在出声或正在缓冲都为 true，缓冲期间 UI 不应回到暂停态
  val isPlaying: Boolean = false,
  val speed: Float = 1f,
  /**
   * 画面宽高比（宽 / 高），已计入非方形像素与旋转元数据。
   *
   * 0 表示尚未解析出画面，纯音频也恒为 0，此时不应据它决定布局。
   * 解码器出首帧后才有值，因此切换媒体到画面就绪之间会短暂为 0。
   */
  val videoAspectRatio: Float = 0f,
  /**
   * 当前字幕是否渲染。与 Media3 的 TEXT 轨禁用状态互为镜像，
   * 切媒体、清列表时原样保留，用户选择不随媒体重置。
   */
  val subtitleVisible: Boolean = true,
  val error: PlaybackError? = null
)

/**
 * 播放位置与时长。播放中每 50ms 刷新一次。
 *
 * 时长和位置放在一起，因为消费方（进度条、波形）总是同时需要两者，
 * 合成一个流可以少一次订阅。
 */
data class PlaybackProgress(
  val positionMs: Long = 0L,
  val durationMs: Long = 0L
)
