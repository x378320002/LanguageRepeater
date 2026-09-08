package com.language.repeater.playback

import com.language.repeater.db.HistoryDao
import com.language.repeater.player.PlaybackEvent
import com.language.repeater.player.PlaybackPlayer
import com.language.repeater.playvideo.model.VideoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 播放历史记录器。构造后自行监听媒体切换，每换一个媒体就落一条记录，无需外部驱动。
 */
class PlaybackHistoryController(
  private val player: PlaybackPlayer,
  private val historyDao: HistoryDao,
  private val scope: CoroutineScope
) {
  init {
    scope.launch(Dispatchers.IO) {
      player.events.collect { event ->
        if (event !is PlaybackEvent.MediaChanged) {
          return@collect
        }
        val media = event.media ?: return@collect

        // 历史只记录看过什么和什么时候看的，播放进度另存在 DataStore
        historyDao.saveHistory(
          VideoEntity(
            id = media.mediaId,
            uri = media.uri,
            name = media.title,
            subUri = media.subtitleUri
          )
        )
      }
    }
  }
}
