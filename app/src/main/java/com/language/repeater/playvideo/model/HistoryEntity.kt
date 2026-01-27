package com.language.repeater.playvideo.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Date: 2026-01-23
 * Time: 14:42
 * Description:
 */

@Entity(
  tableName = "history_table",
  // 外键约束：如果总表里的视频删了，历史记录自动删 (CASCADE)
  foreignKeys = [
    ForeignKey(
      entity = VideoEntity::class,
      parentColumns = ["id"],
      childColumns = ["videoId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  // 加索引，加快查询速度
  indices = [Index(value = ["videoId"], unique = true)]
)
data class HistoryEntity(
  @PrimaryKey(autoGenerate = true)
  val historyId: Long = 0, // 历史记录流水号

  val videoId: String, // 对应 VideoEntity 的 id

  val lastPlayedTime: Long = System.currentTimeMillis() // 什么时候看的
)

// 这是一个纯粹的数据容器，不是数据库表
data class HistoryWithInfo(
  // 把 HistoryEntity 的字段平铺在这里
  @Embedded val history: HistoryEntity,

  // 自动去 video_info_table 查找 id 等于 history.videoId 的数据
  @Relation(
    parentColumn = "videoId",
    entityColumn = "id"
  )
  val videoInfo: VideoEntity
)