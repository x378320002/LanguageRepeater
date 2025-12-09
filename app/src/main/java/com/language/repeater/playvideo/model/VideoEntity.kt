package com.language.repeater.playvideo.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "history_table")
@Serializable
data class VideoEntity(
  @PrimaryKey
  val id: String,

  val uri: String,
  val name: String,
  val positionMs: Long,
  var subUri: String? = null,
  // defaultValue 设为当前时间，方便插入
  val lastPlayedTime: Long = System.currentTimeMillis(),
)