package com.language.repeater.playbacklist

import com.language.repeater.playvideo.model.VideoEntity
import kotlinx.coroutines.flow.Flow

interface PlaybackListRepository {
  val items: Flow<List<PlaybackListItem>>

  suspend fun replace(items: List<VideoEntity>)

  suspend fun addToFront(items: List<VideoEntity>)

  suspend fun addToEnd(items: List<VideoEntity>)

  suspend fun remove(mediaId: String)

  suspend fun moveToFirst(mediaId: String)

  suspend fun moveBefore(mediaId: String, targetMediaId: String)

  suspend fun get(mediaId: String): PlaybackListItem?

  suspend fun first(): PlaybackListItem?

  suspend fun nextOf(mediaId: String): PlaybackListItem?

  suspend fun previousOf(mediaId: String): PlaybackListItem?
}
