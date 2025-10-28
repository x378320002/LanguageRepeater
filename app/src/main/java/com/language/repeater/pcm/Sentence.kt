package com.language.repeater.pcm

import kotlinx.serialization.Serializable

@Serializable
data class Sentence(
  val start: Float,
  val end: Float
)