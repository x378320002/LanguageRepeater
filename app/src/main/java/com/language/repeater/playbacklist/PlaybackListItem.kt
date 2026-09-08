package com.language.repeater.playbacklist

import com.language.repeater.player.PlaybackMedia

/**
 * 播放列表里的一条，只含播放所需的信息。
 *
 * 不含播放位置：位置是秒级高频写入，放进来会让整个列表 Flow 反复重发，
 * 它单独存在 DataStore 里（见 PlayProgress）。
 * 不含排序序号：条目顺序就是列表下标，序号是数据库内部维持顺序的手段。
 */
data class PlaybackListItem(
  val mediaId: String,
  val title: String,
  val uri: String,
  val subtitleUri: String?
)

fun PlaybackListItem.toPlaybackMedia(): PlaybackMedia {
  return PlaybackMedia(
    mediaId = mediaId,
    uri = uri,
    title = title,
    subtitleUri = subtitleUri
  )
}
