package com.language.repeater.playcore

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.language.repeater.MainActivity
import com.language.repeater.R

/**
 * 继承MediaSessionService, 提供真实播放服务
 * 连接系统媒体服务, 能无权限展示Notification, 能直接系统的默认Notification
 */
class PlaybackService : MediaSessionService() {
  companion object {
    private const val TAG = "wangzixu_PlaybackService"
  }

  private var player: Player? = null
  private var mediaSession: MediaSession? = null

  @OptIn(UnstableApi::class)
  override fun onCreate() {
    super.onCreate()

    // 1. 创建真身
    val realPlayer = ExoPlayer.Builder(this).build()

    // 2. 创建拦截器 (处理耳机按键)
    // 这里拦截的逻辑最终调用 PlaybackConnection 的业务方法
    val interceptingPlayer = object : ForwardingPlayer(realPlayer) {
      // 拦截耳机/蓝牙的"下一首"指令 (双击)
      override fun seekToNext() {
        Log.i(TAG, "interceptingPlayer seekToNext")
        // 转交给 Connection 处理业务逻辑 (跳到下一句)
        PlaybackCore.getInstance(this@PlaybackService).seekToNextSentence()
      }

      // 拦截耳机/蓝牙的"上一首"指令 (三击)
      override fun seekToPrevious() {
        Log.i(TAG, "interceptingPlayer seekToPrevious")
        PlaybackCore.getInstance(this@PlaybackService).seekToPreviousSentence()
      }
    }

    player = interceptingPlayer

    // 3. 创建 Session (为了通知栏和系统媒体控制)
    mediaSession = MediaSession
      .Builder(this, interceptingPlayer)
      .setSessionActivity(getSingleTopActivityIntent())
      .setBitmapLoader(CoilBitmapLoader(this))
      .build()

    // 4. 【核心】把 Player 实例上交给单例 Connection
    // 这样 UI 层就能直接访问这个内存对象，零延迟
    PlaybackCore.getInstance(this).initPlayer(interceptingPlayer)

    // 5. 【修正】使用 Builder 构建 Provider，这是设置 BitmapLoader 的正确方式
    val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
    notificationProvider.setSmallIcon(R.drawable.ic_launcher_foreground)
    setMediaNotificationProvider(notificationProvider)

    Log.i(TAG, "PlaybackService onCreate, pid:${Process.myPid()}, currentThread:${Thread.currentThread().name}")
  }

  override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
    //Log.i(TAG, "PlaybackService onUpdateNotification:${startInForegroundRequired}")
    super.onUpdateNotification(session, startInForegroundRequired)
  }

  //override fun onTaskRemoved(rootIntent: Intent?) {
  //  //val p = player
  //  // 如果暂停了且用户划掉了 App，彻底停止服务
  //  //if (p != null && !p.playWhenReady) {
  //  //  p.stop()
  //  //  stopSelf()
  //  //}
  //  Log.i(TAG, "PlaybackService onTaskRemoved")
  //  player?.stop()
  //  stopSelf()
  //  super.onTaskRemoved(rootIntent)
  //}

  override fun onDestroy() {
    // 服务销毁，断开连接
    PlaybackCore.getInstance(this).initPlayer(null)

    mediaSession?.run {
      player.release()
      release()
      mediaSession = null
    }
    super.onDestroy()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
    return mediaSession
  }

  // 辅助方法：创建跳转到 MainActivity 的 PendingIntent
  private fun getSingleTopActivityIntent(): PendingIntent {
    val intent = Intent(this, MainActivity::class.java)
    // 关键标志位：如果不加 FLAG_ACTIVITY_SINGLE_TOP，点击通知可能会重建 Activity，导致播放状态丢失
    // 你的 MainActivity 应该在 Manifest 里设置 launchMode="singleTop" 或 "singleTask"

    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }

    return PendingIntent.getActivity(this, 0, intent, flags)
  }
}