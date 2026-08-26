package com.language.repeater.playback

import android.content.Context
import com.language.repeater.db.AppDb
import com.language.repeater.player.PlaybackPlayer
import com.language.repeater.player.media3.AndroidVideoOutput
import com.language.repeater.player.media3.Media3PlaybackPlayer
import com.language.repeater.playbacklist.PlaybackListController
import com.language.repeater.playbacklist.PlaybackListRepository
import com.language.repeater.playbacklist.RoomPlaybackListRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 应用级播放依赖图。只负责构造和暴露实例，不包含业务逻辑。
 *
 * - PlaybackListController：列表管理、切歌、播放模式
 * - PlaybackProgressController：位置持久化（暂停保存 / seek 保存 / 定时保存）
 * - CurrentMediaAnalysisController：PCM、句子、AB、字幕、历史
 */
class PlaybackGraph(context: Context) {
  private val applicationContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val database = AppDb.instance(applicationContext)
  private val media3Player = Media3PlaybackPlayer(applicationContext)

  val player: PlaybackPlayer = media3Player
  val videoOutput: AndroidVideoOutput = media3Player
  val playbackListRepository: PlaybackListRepository =
    RoomPlaybackListRepository(database)
  val playbackListController = PlaybackListController(
    repository = playbackListRepository,
    player = player,
    scope = scope
  )
  val progressController = PlaybackProgressController(
    player = player,
    videoInfoDao = database.videoInfoDao(),
    scope = scope
  )
  val sentenceController = CurrentMediaSentenceController(
    context = applicationContext,
    player = player,
    historyDao = database.historyDao(),
    videoInfoDao = database.videoInfoDao(),
    scope = scope
  )

  /** 将平台 Player 挂载到 PlaybackPlayer，由 PlaybackService 调用。 */
  fun attachPlatformPlayer(player: androidx.media3.common.Player) {
    media3Player.attachPlayer(player)
  }

  /** 将平台 Player 从 PlaybackPlayer 卸载，由 PlaybackService 调用。 */
  fun detachPlatformPlayer(player: androidx.media3.common.Player) {
    media3Player.detachPlayer(player)
  }
}
