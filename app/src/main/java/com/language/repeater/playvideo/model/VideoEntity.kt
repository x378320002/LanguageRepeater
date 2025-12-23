package com.language.repeater.playvideo.model

import android.R.attr.mimeType
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.language.repeater.MyApp
import com.language.repeater.utils.SubtitleUtils
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

fun VideoEntity.toMediaItem(): MediaItem {
  val it = this
  val builder = MediaItem
    .Builder()
    .setUri(it.uri)
    .setMediaId(it.id)
    .setMediaMetadata(MediaMetadata.Builder()
      .setTitle(it.name)
      .setArtworkUri(it.uri.toUri())
      .build())

  val subU = it.subUri
  if (subU != null && !subU.equals("null", true)) {
    val subtitleUri = subU.toUri()
    val mimeType = SubtitleUtils.getSubtitleMimeType(MyApp.instance, subtitleUri)
    Log.i("wangzixu_VideoEntity", "hasSubTitle mimeType:$mimeType")
    val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
      .setMimeType(mimeType) //.srt
      .setLanguage("en")
      .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
      .build()
    builder.setSubtitleConfigurations(listOf(subtitleConfig))
  }
  return builder.build()
}