package com.language.repeater.playcore

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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

  private val becomingNoisyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent?.action) {
        Log.i(TAG, "ACTION_AUDIO_BECOMING_NOISY → pause")
        // 统一走你的播放核心逻辑
        PlaybackCore.getInstance(this@PlaybackService).pause()
      }
    }
  }

  private lateinit var audioManager: AudioManager
  private var resumeOnFocusGain = false
  private var hasAudioFocus = false
  private val audioFocusListener =
    AudioManager.OnAudioFocusChangeListener { focus ->
      val core = PlaybackCore.getInstance(this@PlaybackService)
      when (focus) {

        AudioManager.AUDIOFOCUS_LOSS -> {
          resumeOnFocusGain = false
          core.pause()
          abandonAudioFocus()
        }

        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
          resumeOnFocusGain = core.isPlaying.value
          core.pause()
        }

        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
          // 学习类音频，直接暂停更合理
          //resumeOnFocusGain = core.isPlaying.value
          //core.pause()
        }

        AudioManager.AUDIOFOCUS_GAIN -> {
          if (resumeOnFocusGain) {
            core.play()
            resumeOnFocusGain = false
          }
        }
      }
    }

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

      override fun play() {
        if (!requestAudioFocusIfNeeded()) {
          Log.i(TAG, "interceptingPlayer play() but requestAudioFocusIfNeeded false")
          return
        }
        super.play()
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

    //监听耳机拔出
    val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    registerReceiver(becomingNoisyReceiver, filter)

    //监听其他声音播放
    audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    Log.i(TAG, "PlaybackService onCreate, pid:${Process.myPid()}, currentThread:${Thread.currentThread().name}")
  }

  private fun requestAudioFocusIfNeeded(): Boolean {
    if (hasAudioFocus) {
      return true
    }

    //audioManager.abandonAudioFocus(audioFocusListener)
    val result = audioManager.requestAudioFocus(
      audioFocusListener,
      AudioManager.STREAM_MUSIC,
      AudioManager.AUDIOFOCUS_GAIN
    )
    hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    return hasAudioFocus
  }

  private fun abandonAudioFocus() {
    if (!hasAudioFocus) return

    audioManager.abandonAudioFocus(audioFocusListener)
    hasAudioFocus = false
  }

  override fun onDestroy() {
    // 服务销毁，断开连接
    unregisterReceiver(becomingNoisyReceiver)
    abandonAudioFocus()
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