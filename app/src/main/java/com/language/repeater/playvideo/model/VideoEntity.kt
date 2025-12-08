package com.language.repeater.playvideo.model

import kotlinx.serialization.Serializable

@Serializable
data class VideoEntity(
  val id: String,
  val uri: String,
  val name: String,
  val positionMs: Long,
  var subUri: String? = null
)