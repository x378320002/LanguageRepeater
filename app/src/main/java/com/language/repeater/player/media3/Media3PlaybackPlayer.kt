package com.language.repeater.player.media3

import android.content.Context
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.language.repeater.player.PlaybackError
import com.language.repeater.player.PlaybackMedia
import com.language.repeater.player.PlaybackPlayer
import com.language.repeater.player.PlaybackEvent
import com.language.repeater.player.PlaybackState
import com.language.repeater.utils.SubtitleUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class Media3PlaybackPlayer(
  private val context: Context
) : PlaybackPlayer, AndroidVideoOutput {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val _state = MutableStateFlow(PlaybackState())
  private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 8)
  private val attachedViews = mutableSetOf<PlayerView>()

  private var player: Player? = null
  private var positionJob: Job? = null

  // 播放器未挂载期间累积的播放意图，挂载后决定是否自动起播；
  // 媒体与进度不在此保存，统一以 _state 为准
  private var pendingPlayWhenReady = false

  override val state: StateFlow<PlaybackState> = _state.asStateFlow()
  override val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

  private val listener = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
      updateState()
      if (playbackState == Player.STATE_ENDED) {
        _state.value.currentMedia?.mediaId?.let { mediaId ->
          _events.tryEmit(PlaybackEvent.MediaEnded(mediaId))
        }
      }
    }

    // playWhenReady 变化不一定伴随 isPlaying 变化（例如缓冲中点播放），需单独同步
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
      updateState()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
      updateState()
      updatePositionTicker()
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
      _state.value = _state.value.copy(speed = playbackParameters.speed)
    }

    override fun onPositionDiscontinuity(
      oldPosition: Player.PositionInfo,
      newPosition: Player.PositionInfo,
      reason: Int
    ) {
      updatePosition()
      if (reason == Player.DISCONTINUITY_REASON_SEEK) {
        val mediaId = player?.currentMediaItem?.mediaId ?: return
        if (_state.value.currentMedia?.mediaId != mediaId) {
          return
        }
        val positionMs = newPosition.positionMs.coerceAtLeast(0L)
        _events.tryEmit(
          PlaybackEvent.SeekCompleted(
            mediaId = mediaId,
            positionMs = positionMs
          )
        )
      }
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
      updateState()
    }

    override fun onPlayerError(error: PlaybackException) {
      _state.value = _state.value.copy(
        isPlaying = false,
        error = PlaybackError(
          code = error.errorCodeName,
          message = error.message
        )
      )
    }
  }

  fun attachPlayer(newPlayer: Player) {
    runOnPlayerThread {
      attachPlayerInternal(newPlayer)
    }
  }

  private fun attachPlayerInternal(newPlayer: Player) {
    if (player === newPlayer) {
      return
    }

    player?.removeListener(listener)
    player = newPlayer
    newPlayer.addListener(listener)
    attachedViews.forEach { playerView ->
      playerView.player = newPlayer
    }
    // 无媒体时挂载完监听即可，state 保持默认值（含已设置的倍速）
    val currentMedia = _state.value.currentMedia ?: return

    // 媒体与进度取自 state，无论是首次挂载还是 Service 重建后重新挂载
    prepareOnPlayer(
      currentPlayer = newPlayer,
      media = currentMedia,
      startPositionMs = _state.value.positionMs,
      playWhenReady = pendingPlayWhenReady
    )
    pendingPlayWhenReady = false
    updatePositionTicker()
  }

  fun detachPlayer(detachedPlayer: Player) {
    runOnPlayerThread {
      detachPlayerInternal(detachedPlayer)
    }
  }

  private fun detachPlayerInternal(detachedPlayer: Player) {
    if (player !== detachedPlayer) {
      return
    }

    detachedPlayer.removeListener(listener)
    attachedViews.forEach { playerView ->
      if (playerView.player === detachedPlayer) {
        playerView.player = null
      }
    }
    player = null
    // 记住被打断时的播放意图，Service 重建后据此续播
    pendingPlayWhenReady = detachedPlayer.isPlaying || detachedPlayer.playWhenReady
    positionJob?.cancel()
    positionJob = null
    _state.value = _state.value.copy(
      isPlaying = false,
      positionMs = detachedPlayer.currentPosition.coerceAtLeast(0L)
    )
  }

  override fun prepare(
    media: PlaybackMedia,
    startPositionMs: Long,
    playWhenReady: Boolean
  ) {
    runOnPlayerThread {
      prepareInternal(media, startPositionMs, playWhenReady)
    }
  }

  private fun prepareInternal(
    media: PlaybackMedia,
    startPositionMs: Long,
    playWhenReady: Boolean
  ) {
    val currentPlayer = player
    if (currentPlayer == null) {
      // 播放器尚未挂载，只记录意图并落 state，挂载时再真正 prepare
      pendingPlayWhenReady = playWhenReady
      resetStateForPrepare(media, startPositionMs.coerceAtLeast(0L), playWhenReady)
      return
    }
    prepareOnPlayer(currentPlayer, media, startPositionMs, playWhenReady)
  }

  private fun prepareOnPlayer(
    currentPlayer: Player,
    media: PlaybackMedia,
    startPositionMs: Long,
    playWhenReady: Boolean
  ) {
    val safePositionMs = startPositionMs.coerceAtLeast(0L)
    resetStateForPrepare(media, safePositionMs, playWhenReady)
    currentPlayer.setMediaItem(createMediaItem(media), safePositionMs)
    currentPlayer.playWhenReady = false
    currentPlayer.prepare()
    if (playWhenReady) {
      currentPlayer.play()
    }
    _events.tryEmit(PlaybackEvent.MediaChanged(media))
  }

  private fun resetStateForPrepare(
    media: PlaybackMedia,
    startPositionMs: Long,
    playWhenReady: Boolean
  ) {
    _state.value = PlaybackState(
      currentMedia = media,
      isPlaying = playWhenReady,
      positionMs = startPositionMs,
      speed = _state.value.speed
    )
  }

  override fun play() {
    runOnPlayerThread {
      val currentPlayer = player
      if (currentPlayer != null) {
        currentPlayer.play()
      } else if (_state.value.currentMedia != null) {
        // 播放器未挂载但已有待播媒体，先记下意图并让 UI 立即进入播放态
        pendingPlayWhenReady = true
        _state.value = _state.value.copy(isPlaying = true)
      }
    }
  }

  override fun pause() {
    runOnPlayerThread {
      val currentPlayer = player
      if (currentPlayer != null) {
        currentPlayer.pause()
      } else {
        pendingPlayWhenReady = false
        _state.value = _state.value.copy(isPlaying = false)
      }
    }
  }

  override fun seekTo(positionMs: Long) {
    runOnPlayerThread {
      val safePositionMs = positionMs.coerceAtLeast(0L)
      val currentPlayer = player
      if (currentPlayer != null) {
        currentPlayer.seekTo(safePositionMs)
        updatePosition()
      } else {
        // 进度写入 state 即可，挂载时会从 state 取起播位置
        _state.value = _state.value.copy(positionMs = safePositionMs)
      }
    }
  }

  override fun setSpeed(speed: Float) {
    runOnPlayerThread {
      player?.also {
        val safeSpeed = speed.coerceIn(0.1f, 4f)
        it.setPlaybackSpeed(safeSpeed)
        _state.value = _state.value.copy(speed = safeSpeed)
      }
    }
  }

  override fun setSubtitle(subtitleUri: String?) {
    runOnPlayerThread {
      setSubtitleInternal(subtitleUri)
    }
  }

  private fun setSubtitleInternal(subtitleUri: String?) {
    val currentMedia = _state.value.currentMedia ?: return
    val updatedMedia = currentMedia.copy(subtitleUri = subtitleUri)
    val currentPlayer = player
    if (currentPlayer == null) {
      _state.value = _state.value.copy(currentMedia = updatedMedia)
      return
    }

    val positionMs = currentPlayer.currentPosition.coerceAtLeast(0L)
    val playWhenReady = currentPlayer.playWhenReady
    val speed = currentPlayer.playbackParameters.speed

    currentPlayer.setMediaItem(createMediaItem(updatedMedia), positionMs)
    currentPlayer.setPlaybackSpeed(speed)
    currentPlayer.playWhenReady = false
    currentPlayer.prepare()
    if (playWhenReady) {
      currentPlayer.play()
    }
    _state.value = _state.value.copy(
      currentMedia = updatedMedia,
      isPlaying = playWhenReady,
      error = null
    )
  }

  override fun clear() {
    runOnPlayerThread {
      pendingPlayWhenReady = false
      player?.clearMediaItems()
      _state.value = PlaybackState(speed = _state.value.speed)
    }
  }

  override fun attach(playerView: PlayerView) {
    runOnPlayerThread {
      attachedViews.add(playerView)
      playerView.player = player
    }
  }

  override fun detach(playerView: PlayerView) {
    runOnPlayerThread {
      attachedViews.remove(playerView)
      playerView.player = null
    }
  }

  private fun createMediaItem(media: PlaybackMedia): MediaItem {
    val builder = MediaItem.Builder()
      .setMediaId(media.mediaId)
      .setUri(media.uri)
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setTitle(media.title)
          .setArtworkUri(media.uri.toUri())
          .build()
      )

    media.mimeType?.let(builder::setMimeType)
    media.subtitleUri
      ?.takeUnless { it.equals("null", ignoreCase = true) }
      ?.toUri()
      ?.let { subtitleUri ->
        builder.setSubtitleConfigurations(
          listOf(SubtitleUtils.createSubtitleConfig(context, subtitleUri))
        )
      }
    return builder.build()
  }

  private fun updateState() {
    val currentPlayer = player
    val duration = currentPlayer?.duration ?: 0L
    val durationMs = if (duration == C.TIME_UNSET) {
      0L
    } else {
      duration.coerceAtLeast(0L)
    }
    _state.value = _state.value.copy(
      isPlaying = currentPlayer?.isPlayIntended() ?: false,
      positionMs = currentPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L,
      durationMs = durationMs,
      speed = currentPlayer?.playbackParameters?.speed ?: _state.value.speed
    )
  }

  /**
   * 播放意图判定：只要用户想播且媒体没结束就算播放态。
   *
   * 不能直接用 [Player.isPlaying]，它在 STATE_BUFFERING 期间返回 false，
   * 会导致缓冲瞬间 UI 闪回暂停图标、误导用户再点一次播放。
   */
  private fun Player.isPlayIntended(): Boolean {
    return playWhenReady &&
      playbackState != Player.STATE_IDLE &&
      playbackState != Player.STATE_ENDED
  }

  private fun updatePositionTicker() {
    positionJob?.cancel()
    positionJob = null
    if (player?.isPlaying != true) {
      return
    }

    positionJob = scope.launch {
      while (isActive && player?.isPlaying == true) {
        updatePosition()
        delay(POSITION_INTERVAL_MS)
      }
    }
  }

  private fun updatePosition() {
    val currentPlayer = player ?: return
    val duration = currentPlayer.duration
    val durationMs = if (duration == C.TIME_UNSET) {
      0L
    } else {
      duration.coerceAtLeast(0L)
    }
    _state.value = _state.value.copy(
      positionMs = currentPlayer.currentPosition.coerceAtLeast(0L),
      durationMs = durationMs
    )
  }

  private fun runOnPlayerThread(action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      action()
    } else {
      scope.launch {
        action()
      }
    }
  }

  private companion object {
    const val POSITION_INTERVAL_MS = 50L
  }
}
