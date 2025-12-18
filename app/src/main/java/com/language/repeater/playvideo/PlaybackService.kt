package com.language.repeater.playvideo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.language.repeater.MainActivity
import com.language.repeater.R

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
        // 转交给 Connection 处理业务逻辑 (跳到下一句)
        PlaybackConnection.getInstance(this@PlaybackService).seekToNextSentence()
      }

      // 拦截耳机/蓝牙的"上一首"指令 (三击)
      override fun seekToPrevious() {
        PlaybackConnection.getInstance(this@PlaybackService).seekToPreviousSentence()
      }

      // 播放/暂停通常不需要拦截，除非你有特殊需求(比如复读开关)
      // 如果要拦截，记得 syncState 可能需要处理
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
    PlaybackConnection.getInstance(this).initPlayer(interceptingPlayer)

    // 5. 【修正】使用 Builder 构建 Provider，这是设置 BitmapLoader 的正确方式
    val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
    notificationProvider.setSmallIcon(R.drawable.ic_launcher_foreground)
    setMediaNotificationProvider(notificationProvider)

    Log.i(TAG, "PlaybackService onCreate, pid:${Process.myPid()}, currentThread:${Thread.currentThread().name}")
  }

  override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
    Log.i(TAG, "PlaybackService onUpdateNotification:${startInForegroundRequired}")
    super.onUpdateNotification(session, startInForegroundRequired)
    //startForegroundWithPlaceholder()
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
    PlaybackConnection.getInstance(this).initPlayer(null)

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

  private fun startForegroundWithPlaceholder() {
    try {
      val channelId = "playback_setup" // 专门用于启动的临时渠道
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // IMPORTANCE_MIN 意味着这个通知会尽量不打扰用户（无声、折叠）
        val channel = NotificationChannel(channelId, "Service Startup", NotificationManager.IMPORTANCE_MIN)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
      }

      val notification = NotificationCompat.Builder(this, channelId)
        .setSmallIcon(android.R.drawable.ic_media_play) // 随便设一个图标
        .setContentTitle("服务正在启动...")
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()
      // 这里的 ID (1001) 只要是非0即可
      startForeground(1001, notification)
    } catch (e: Exception) {
      // 在极少数国产魔改ROM上可能会报错，捕获住防止崩
      e.printStackTrace()
    }
  }
}