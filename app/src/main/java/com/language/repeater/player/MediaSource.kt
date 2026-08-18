package com.language.repeater.player

/**
 * 媒体源 — 业务层定义的媒体描述，替代 Media3 的 MediaItem
 */
data class MediaSource(
  val uri: String,
  val mimeType: String? = null,
  val headers: Map<String, String> = emptyMap()
)
