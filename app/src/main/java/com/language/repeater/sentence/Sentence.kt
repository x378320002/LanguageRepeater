package com.language.repeater.sentence

import kotlinx.serialization.Serializable

@Serializable
data class Sentence(
  var start: Float,
  var end: Float
)