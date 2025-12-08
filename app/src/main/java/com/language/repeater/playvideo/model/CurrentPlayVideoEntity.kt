package com.language.repeater.playvideo.model

import kotlinx.serialization.Serializable

@Serializable
data class CurrentPlayVideoEntity(
  val index: Int, //当前播放列表中的第几个
  val positionMs: Long //当前播放进度
)