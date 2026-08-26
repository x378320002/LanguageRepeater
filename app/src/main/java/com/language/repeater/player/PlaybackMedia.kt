package com.language.repeater.player

data class PlaybackMedia(
  val mediaId: String,
  val uri: String,
  val title: String,
  val subtitleUri: String? = null,
  val mimeType: String? = null,
  val headers: Map<String, String> = emptyMap()
)
