package com.language.repeater.player

data class PlaybackMedia(
  val mediaId: Long,
  val uri: String,
  val title: String,
  val subtitleUri: String? = null,
  /** 来源是否为视频：决定播放器是否渲染画面、是否取视频缩略图 */
  val isVideo: Boolean = false,
  /** 文件字节数，来自 MediaStore */
  val size: Long = 0,
  /** 时长（毫秒），来自 MediaStore */
  val durationMs: Long = 0,
  val mimeType: String? = null,
  val headers: Map<String, String> = emptyMap()
)
