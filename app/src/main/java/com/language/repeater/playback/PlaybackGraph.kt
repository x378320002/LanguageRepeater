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
 * 播放器在此常驻创建，与进程同生共死，PlaybackService 借用它的 exoPlayer 建 MediaSession。
 *
 * - PlaybackListController：列表管理、切歌、播放模式
 * - PlaybackProgressController：播放位置持久化
 * - CurrentMediaSentenceController：PCM 波形、句子切分、AB 循环
 * - SubtitleController：媒体与字幕的对应关系
 * - PlaybackHistoryController：播放历史
 */
class PlaybackGraph(context: Context) {
  private val applicationContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val database = AppDb.instance(applicationContext)

  /**
   * 全应用唯一的播放器实例。
   *
   * 下面三个字段指向同一个对象，区别只在于把类型窄化成各调用方真正需要的能力，
   * 避免它们拿到用不着的方法：
   * - [player]：业务播放控制，UI 与各 controller 使用
   * - [videoOutput]：PlayerView 绑定与解绑，只有视频画面组件使用
   * - [media3Player]：Media3 平台细节（借 exoPlayer 建 MediaSession、IDLE 自愈），
   *   只有 Activity 与 Service 这类平台组件使用
   */
  val media3Player = Media3PlaybackPlayer(applicationContext)
  val player: PlaybackPlayer = media3Player
  val videoOutput: AndroidVideoOutput = media3Player

  val playbackListRepository: PlaybackListRepository = RoomPlaybackListRepository(database)
  val playbackListController = PlaybackListController(
    repository = playbackListRepository,
    player = player,
    scope = scope
  )
  val progressController = PlaybackProgressController(
    player = player,
    scope = scope
  )
  val sentenceController = CurrentMediaSentenceController(
    context = applicationContext,
    player = player,
    scope = scope
  )
  val subtitleController = SubtitleController(
    context = applicationContext,
    player = player,
    videoInfoDao = database.videoInfoDao(),
    scope = scope
  )

  /**
   * 加载上次播放的媒体，加载后保持暂停。
   *
   * 必须由外部在本对象构造完成后调用（见 MainApp.onCreate），不能移进 init：
   * 那样协程会在对象尚未构造完成时就拿到 this。
   */
  fun restoreLastMedia() {
    scope.launch {
      playbackListController.restore()
    }
  }

  /** 任务卡被移除 / 服务销毁前落盘一次精确进度。 */
  fun saveProgressSnapshot() {
    progressController.saveCurrentPositionNow()
  }
}
