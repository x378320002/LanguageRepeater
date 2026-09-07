package com.language.repeater.playback

import android.content.Context
import androidx.media3.common.Player
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
import kotlinx.coroutines.launch

/**
 * 应用级播放依赖图。只负责构造和暴露实例，不包含业务逻辑。
 *
 * ExoPlayer 由 Media3PlaybackPlayer 在此常驻创建，与进程同生共死；
 * PlaybackService 只借用 exoPlayer 去建自己的 MediaSession。
 *
 * - PlaybackListController：列表管理、切歌、播放模式
 * - PlaybackProgressController：位置持久化（暂停保存 / seek 保存 / 定时保存）
 * - CurrentMediaSentenceController：PCM、句子、AB、字幕、历史
 */
class PlaybackGraph(context: Context) {
  private val applicationContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val database = AppDb.instance(applicationContext)
  val media3Player = Media3PlaybackPlayer(applicationContext)

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

  init {
    // 与 PlaybackService 解耦：应用启动即恢复上次播放的媒体
    scope.launch {
      playbackListController.restore()
    }
  }

  /** 任务卡被移除 / 服务销毁前落盘一次精确进度。 */
  fun saveProgressSnapshot() {
    progressController.saveCurrentPositionNow()
  }
}
