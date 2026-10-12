package com.language.repeater.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "playback_list_table",
  foreignKeys = [
    ForeignKey(
      entity = VideoEntity::class,
      parentColumns = ["id"],
      childColumns = ["mediaId"],
      onDelete = ForeignKey.Companion.CASCADE
    )
  ],
  indices = [Index(value = ["sortOrder"])]
)
data class PlaybackListEntity(
  @PrimaryKey
  val mediaId: String,
  val sortOrder: Long
)