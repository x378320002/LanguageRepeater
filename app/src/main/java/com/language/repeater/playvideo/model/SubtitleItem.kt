package com.language.repeater.playvideo.model

/**
 * Date: 2025-12-15
 * Time: 14:22
 * Description:
 */
data class SubtitleItem(
  val index: Int,
  var startTime: Long,
  var endTime: Long,
  var content: String
)