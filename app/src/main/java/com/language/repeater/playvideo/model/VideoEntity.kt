package com.language.repeater.playvideo.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.language.repeater.player.PlaybackMedia
import kotlinx.serialization.Serializable

@Entity(tableName = "video_info_table")
@Serializable
data class VideoEntity(
  @PrimaryKey
  val id: String,
  val uri: String,
  val name: String,
  var subUri: String? = null,
  var position: Long = 0
)

/** 将持久化的视频信息转换为播放器使用的纯 Kotlin 模型。 */
fun VideoEntity.toPlaybackMedia(): PlaybackMedia {
  return PlaybackMedia(
    mediaId = id,
    uri = uri,
    title = name,
    subtitleUri = subUri
  )
}
