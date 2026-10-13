package com.language.repeater.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.language.repeater.player.PlaybackMedia
import kotlinx.serialization.Serializable

/**
 * uri 唯一索引确保同一文件不会重复插入，同时让 @Upsert 在冲突时走 UPDATE 而不是 INSERT。
 */
@Entity(
  tableName = "video_info_table",
  indices = [Index(value = ["uri"], unique = true)]
)
@Serializable
data class VideoEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val uri: String,
  val name: String,
  var subUri: String? = null,
  /** 来源是否为视频：决定播放器是否渲染画面、是否取视频缩略图 */
  val isVideo: Boolean = false,
  /** 文件字节数，来自 MediaStore */
  val size: Long = 0,
  /** 媒体时长（毫秒），来自 MediaStore */
  val durationMs: Long = 0
)

fun VideoEntity.toPlaybackMedia(): PlaybackMedia {
  return PlaybackMedia(
    mediaId = id,
    uri = uri,
    title = name,
    subtitleUri = subUri,
    isVideo = isVideo,
    size = size,
    durationMs = durationMs
  )
}
