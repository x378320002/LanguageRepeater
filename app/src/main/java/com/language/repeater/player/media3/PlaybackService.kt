package com.language.repeater.player.media3

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.language.repeater.R
import com.language.repeater.app.MainActivity
import com.language.repeater.app.MainApp

/**
 * Media3 通知栏宿主。
 *
 * 只负责为应用级播放器创建 MediaSession 并提供系统媒体通知：
 * 不创建、不持有、不释放 ExoPlayer —— 播放器由 PlaybackGraph 在应用层常驻。
 */
class PlaybackService : MediaSessionService() {
  private var mediaSession: MediaSession? = null

  private val playbackGraph
    get() = MainApp.playbackGraph

  @OptIn(UnstableApi::class)
  override fun onCreate() {
    super.onCreate()

    val sessionPlayer = SentenceAwarePlayer(
      player = playbackGraph.media3Player.exoPlayer,
      onNextSentence = {
        playbackGraph.sentenceController.seekToNextSentence()
      },
      onPreviousSentence = {
        playbackGraph.sentenceController.seekToPreviousSentence()
      }
    )

    mediaSession = MediaSession.Builder(this, sessionPlayer)
      .setSessionActivity(getSingleTopActivityIntent())
      .setBitmapLoader(CoilBitmapLoader(applicationContext))
      .build()

    val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
    notificationProvider.setSmallIcon(R.mipmap.ic_launcher)
    setMediaNotificationProvider(notificationProvider)

    Log.i(TAG, "PlaybackService created, pid=${Process.myPid()}")
  }

  @UnstableApi
  class SentenceAwarePlayer(
    player: Player,
    private val onNextSentence: () -> Unit,
    private val onPreviousSentence: () -> Unit,
  ) : ForwardingSimpleBasePlayer(player) {

    // 告诉 MediaSession：播放器支持上一项和下一项
    override fun getState(): State {
      val state = super.getState()

      val commands = state.availableCommands.buildUpon()
        .add(Player.COMMAND_SEEK_TO_NEXT)
        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
        .build()

      return state.buildUpon()
        .setAvailableCommands(commands)
        .build()
    }

    // 拦截上一项、下一项操作
    override fun handleSeek(
      mediaItemIndex: Int,
      positionMs: Long,
      seekCommand: Int,
    ): ListenableFuture<*> {

      when (seekCommand) {
        Player.COMMAND_SEEK_TO_NEXT -> {
          onNextSentence()
          return Futures.immediateFuture(Unit)
        }

        Player.COMMAND_SEEK_TO_PREVIOUS -> {
          onPreviousSentence()
          return Futures.immediateFuture(Unit)
        }

        else -> return super.handleSeek(
          mediaItemIndex,
          positionMs,
          seekCommand
        )
      }
    }
  }

  @OptIn(UnstableApi::class)
  private fun createSessionPlayer(): Player {
    return object : ForwardingPlayer(playbackGraph.media3Player.exoPlayer) {
      override fun hasPreviousMediaItem(): Boolean {
        return true
      }

      override fun seekToPrevious() {
        seekToPreviousSentence()
      }

      override fun seekToPreviousMediaItem() {
        seekToPreviousSentence()
      }

      override fun hasNextMediaItem(): Boolean {
        return true
      }

      override fun seekToNext() {
        seekToNextSentence()
      }

      override fun seekToNextMediaItem() {
        seekToNextSentence()
      }

      private fun seekToNextSentence() {
        playbackGraph.sentenceController.seekToNextSentence()
      }

      private fun seekToPreviousSentence() {
        playbackGraph.sentenceController.seekToPreviousSentence()
      }
    }
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
    return mediaSession
  }

  /**
   * 划掉任务卡时先落盘进度，再交由父类决定去留。
   *
   * 注意：应用级 MediaController 常驻绑定本服务，父类内部的 stopSelf() 不会真正销毁
   * 已被 bind 的服务，因此后台不播放时的实际效果是「暂停并保留服务」，
   * onDestroy 基本只在进程被回收时才走到。
   */
  override fun onTaskRemoved(rootIntent: Intent?) {
    playbackGraph.saveProgressSnapshot()
    super.onTaskRemoved(rootIntent)
    Log.i(TAG, "PlaybackService onTaskRemoved, pid=${Process.myPid()}")
  }

  override fun onDestroy() {
    Log.i(TAG, "PlaybackService onDestroy, pid=${Process.myPid()}")
    // 尽力落盘，进程正在消亡时不保证完成
    playbackGraph.saveProgressSnapshot()
    // 只解绑会话。绝不 release Player：它属于 Application，本服务只是借用。
    // MediaSession.release() 仅摘掉自己的 listener，不会动 player。
    mediaSession?.release()
    mediaSession = null
    super.onDestroy()
  }

  private fun getSingleTopActivityIntent(): PendingIntent {
    val intent = Intent(this, MainActivity::class.java)
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }
    return PendingIntent.getActivity(this, 0, intent, flags)
  }

  companion object {
    const val TAG = "PlaybackService"
  }
}
