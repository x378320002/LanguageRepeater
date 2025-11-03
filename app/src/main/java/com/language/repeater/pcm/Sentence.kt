package com.language.repeater.pcm

import kotlinx.serialization.Serializable

@Serializable
data class Sentence(
  var start: Float,
  var end: Float
)