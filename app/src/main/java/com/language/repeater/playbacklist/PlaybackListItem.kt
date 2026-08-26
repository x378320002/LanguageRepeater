package com.language.repeater.playbacklist

data class PlaybackListItem(
  val mediaId: String,
  val title: String,
  val uri: String,
  val subtitleUri: String?,
  val savedPositionMs: Long,
  val sortOrder: Long
)
