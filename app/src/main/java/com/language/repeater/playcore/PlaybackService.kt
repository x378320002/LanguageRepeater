package com.language.repeater.playcore

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
    get() = (application as MainApp).playbackGraph

  @OptIn(UnstableApi::class)
  override fun onCreate() {
    super.onCreate()

    mediaSession = MediaSession.Builder(this, createSessionPlayer())
      .setSessionActivity(getSingleTopActivityIntent())
      .setBitmapLoader(CoilBitmapLoader(applicationContext))
      .build()

    val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
    notificationProvider.setSmallIcon(R.drawable.ic_launcher_foreground)
    setMediaNotificationProvider(notificationProvider)

    Log.i(TAG, "PlaybackService created, pid=${Process.myPid()}")
  }

  /**
   * 在应用层播放器之上叠加通知栏专属行为，构造仅供本服务 MediaSession 使用的 Player。
   *
   * 音频焦点由 ExoPlayer 内部处理，这里只需把系统媒体卡片的「上一首 / 下一首」
   * 改写成本应用的「上一句 / 下一句」。应用层播放链路不经过这一层，
   * 因此 App 内的 seek 语义不受影响。
   */
  @OptIn(UnstableApi::class)
  private fun createSessionPlayer(): Player {
    return object : ForwardingPlayer(playbackGraph.media3Player.exoPlayer) {
      override fun seekToNext() {
        seekToNextSentence()
      }

      override fun seekToNextMediaItem() {
        seekToNextSentence()
      }

      override fun seekToPrevious() {
        seekToPreviousSentence()
      }

      override fun seekToPreviousMediaItem() {
        seekToPreviousSentence()
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
