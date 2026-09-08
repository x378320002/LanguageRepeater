package com.language.repeater.sentence

import kotlinx.serialization.Serializable

@Serializable
data class Sentence(
  var start: Float,//单位是秒
  var end: Float
) {
  fun startMilSecond(): Long {
    return (start * 1000L).toLong() + 1
  }

}