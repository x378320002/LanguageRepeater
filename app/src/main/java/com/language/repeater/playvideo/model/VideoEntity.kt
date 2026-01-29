package com.language.repeater.playvideo.model

import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.language.repeater.MyApp
import com.language.repeater.utils.SubtitleUtils
import kotlinx.serialization.Serializable

@Entity(tableName = "video_info_table")
@Serializable
data class VideoEntity(
  @PrimaryKey
  val id: String, //主键, 根据文件的md5生成的唯一key

  val uri: String, //对应的saf地址
  val name: String, //文件名
  var subUri: String? = null, //字幕文件地址
)

//把当前条目转成一个占位的条目
fun MediaItem.toPlaceHold(): MediaItem {
  return this.buildUpon().setMediaMetadata(
    MediaMetadata
      .Builder()
      .setTitle(this.mediaMetadata.title)
      .setArtworkUri(this.mediaMetadata.artworkUri)
      .setExtras(Bundle().apply {
        putBoolean("MediaPlaceHold", true)
      })
      .build()
  ).build()
}

fun MediaItem.isPlaceHold(): Boolean {
  return this.mediaMetadata.extras?.getBoolean("MediaPlaceHold") == true
}

fun MediaItem.toEntity(): VideoEntity {
  val item = this
  //用artworkUri寻找原始的uri, 当前播放的uri可能是解析后的wav文件, 这个文件可能被删除
  val oriUri = item.mediaMetadata.artworkUri?.toString() ?: item.localConfiguration?.uri.toString()
  return VideoEntity(
    id = item.mediaId,
    uri = oriUri,
    name = item.mediaMetadata.title.toString(),
    subUri = item.localConfiguration?.subtitleConfigurations?.firstOrNull()?.uri?.toString()
  )
}

fun VideoEntity.toMediaItem(): MediaItem {
  //val f = FFmpegUtil.getWavFile(MyApp.instance, id)
  //val wavUri = if (f.exists() && f.length() > 0) {
  //  f.absolutePath
  //} else {
  //  null
  //}
  val it = this
  val uri = it.uri
  //Log.i("wangzixu_VideoEntity", "toMediaItem uri:$uri")
  val builder = MediaItem
    .Builder()
    .setUri(uri)
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