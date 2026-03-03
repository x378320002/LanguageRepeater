package com.language.repeater.playvideo.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Date: 2026-01-23
 * Time: 14:44
 * Description:
 */

//用来存储用户当前添加到列表里的视, 当前播放的列表
@Entity(
  tableName = "current_list_table",
  foreignKeys = [
    ForeignKey(
      entity = VideoEntity::class,
      parentColumns = ["id"],
      childColumns = ["videoId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [Index(value = ["videoId"], unique = true)] // 一个视频可以在列表出现多次吗？如果不行，加上 unique=true
)
data class CurPlayListEntity(
  @PrimaryKey(autoGenerate = true)
  val playlistId: Long = 0,

  val videoId: String, // 对应 VideoEntity 的 id
  val sortOrder: Int = 0 // 排序字段，用户拖拽排序时更新这个值
)

data class CurPlayListItemWithInfo(
  @Embedded val playlistItem: CurPlayListEntity,

  @Relation(
    parentColumn = "videoId",
    entityColumn = "id"
  )
  val videoInfo: VideoEntity
)