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
import com.language.repeater.R
import com.language.repeater.app.MainActivity
import com.language.repeater.app.MainApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Media3 平台宿主。ExoPlayer 只在此处创建和释放，业务控制交给应用级 PlaybackGraph。
 */
class PlaybackService : MediaSessionService() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var player: Player? = null
  private var mediaSession: MediaSession? = null
  private lateinit var audioManager: AudioManager
  private var resumeOnFocusGain = false
  private var hasAudioFocus = false

  private val playbackGraph
    get() = (application as MainApp).playbackGraph

  private val becomingNoisyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent?.action) {
        playbackGraph.player.pause()
      }
    }
  }

  private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focus ->
    when (focus) {
      AudioManager.AUDIOFOCUS_LOSS -> {
        resumeOnFocusGain = false
        hasAudioFocus = false
        pauseForAudioFocusLoss()
      }

      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
        // 学习场景不降低语音音量，统一暂停，避免句子内容被遮盖。
        resumeOnFocusGain = playbackGraph.player.state.value.isPlaying
        hasAudioFocus = false
        pauseForAudioFocusLoss()
      }

      AudioManager.AUDIOFOCUS_GAIN -> {
        hasAudioFocus = true
        val shouldResume = resumeOnFocusGain
        resumeOnFocusGain = false
        if (shouldResume) {
          playbackGraph.player.play()
        }
      }
    }
  }

  @OptIn(UnstableApi::class)
  override fun onCreate() {
    super.onCreate()
    audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val realPlayer = ExoPlayer.Builder(this).build()
    val interceptingPlayer = object : ForwardingPlayer(realPlayer) {
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

      override fun play() {
        if (!requestAudioFocusIfNeeded()) {
          Log.i(TAG, "play ignored because audio focus was denied")
          return
        }
        super.play()
      }
    }

    player = interceptingPlayer
    playbackGraph.attachPlatformPlayer(interceptingPlayer)
    mediaSession = MediaSession.Builder(this, interceptingPlayer)
      .setSessionActivity(getSingleTopActivityIntent())
      .setBitmapLoader(CoilBitmapLoader(this))
      .build()

    val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
    notificationProvider.setSmallIcon(R.drawable.ic_launcher_foreground)
    setMediaNotificationProvider(notificationProvider)

    registerReceiver(
      becomingNoisyReceiver,
      IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    )
    serviceScope.launch {
      playbackGraph.playbackListController.restore()
    }
    Log.i(TAG, "PlaybackService created, pid=${Process.myPid()}")
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    // 尽早提交快照；进程仍存活时由应用级 IO scope 完成尽力保存。
    val currentPlayer = player
    if (currentPlayer != null) {
      playbackGraph.progressController.saveCurrentPositionNow(currentPlayer.currentPosition.coerceAtLeast(0L))
    }
    super.onTaskRemoved(rootIntent)
    Log.i(TAG, "PlaybackService onTaskRemoved, pid=${Process.myPid()}")
  }

  override fun onDestroy() {
    Log.i(TAG, "PlaybackService onDestroy, pid=${Process.myPid()}")
    unregisterReceiver(becomingNoisyReceiver)
    abandonAudioFocus()

    val currentPlayer = player
    if (currentPlayer != null) {
      // 直接读取平台 Player 的最终位置，避免状态流采样尚未追上销毁时刻。
      playbackGraph.progressController.saveCurrentPositionNow(currentPlayer.currentPosition.coerceAtLeast(0L))
      playbackGraph.detachPlatformPlayer(currentPlayer)
    }

    val currentSession = mediaSession
    mediaSession = null
    if (currentSession != null) {
      currentSession.player.release()
      currentSession.release()
    } else {
      currentPlayer?.release()
    }
    player = null
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
    return mediaSession
  }

  private fun pauseForAudioFocusLoss() {
    playbackGraph.player.pause()
  }

  private fun requestAudioFocusIfNeeded(): Boolean {
    if (hasAudioFocus) {
      return true
    }

    val result = audioManager.requestAudioFocus(
      audioFocusListener,
      AudioManager.STREAM_MUSIC,
      AudioManager.AUDIOFOCUS_GAIN
    )
    hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    return hasAudioFocus
  }

  private fun abandonAudioFocus() {
    if (!hasAudioFocus) {
      return
    }
    audioManager.abandonAudioFocus(audioFocusListener)
    hasAudioFocus = false
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

  private companion object {
    const val TAG = "PlaybackService"
  }
}
