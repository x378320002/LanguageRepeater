package com.language.repeater.player.media3

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.language.repeater.player.PlaybackError
import com.language.repeater.player.PlaybackMedia
import com.language.repeater.player.PlaybackPlayer
import com.language.repeater.player.PlaybackEvent
import com.language.repeater.player.PlaybackProgress
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

/**
 * 应用级唯一播放器。自行创建并持有 ExoPlayer，与进程同生共死、不提供释放入口。
 *
 * PlaybackService 只是借用这里的播放器去建 MediaSession，因此播放器永远非空，
 * 不存在「等平台组件投喂」的空窗期。
 */
@OptIn(UnstableApi::class)
class Media3PlaybackPlayer(
  private val context: Context
) : PlaybackPlayer, AndroidVideoOutput {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val _state = MutableStateFlow(PlaybackState())
  private val _progress = MutableStateFlow(PlaybackProgress())
  private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 8)
  private val attachedViews = mutableSetOf<PlayerView>()

  // 平滑进度锚点：音频播放时 ExoPlayer 的 currentPosition 更新粒度较粗，
  // 连续多次轮询可能返回同一个值，进度条看起来"一跳一跳"。
  // 记录上一次读到的真实位置与时间点，位置没前进且正在播放时才用插值补足。
  // -1 表示尚未读过任何位置，避免把首次读取误判成"跟前一次相同"。
  private var lastRawPositionMs = -1L
  private var lastRawTimeMs = 0L

  /**
   * 全局唯一、与进程同生共死的播放器，同时供 PlaybackService 包装出 MediaSession。
   *
   * 显式指定主线程为 application looper，使本类即使被后台线程首次触达，
   * 后续访问仍统一收敛到主线程。
   *
   * 类型收窄为 Player 而非 ExoPlayer：本播放器不提供释放入口，
   * 不应让外部拿到 release() 之类的生命周期方法。
   */
  val exoPlayer: Player = ExoPlayer.Builder(context)
    .setLooper(Looper.getMainLooper())
    // 交由 ExoPlayer 内置管理音频焦点：起播自动申请，丢失自动暂停，重获自动续播。
    // contentType 用 SPEECH，让被短暂抢音时暂停而不是降低音量，避免句子内容被盖住。
    .setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
        .build(),
      /* handleAudioFocus = */ true
    )
    // 耳机拔出自动暂停，无需自行监听 ACTION_AUDIO_BECOMING_NOISY
    .setHandleAudioBecomingNoisy(true)
    .build()

  private var positionJob: Job? = null

  override val state: StateFlow<PlaybackState> = _state.asStateFlow()
  override val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()
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

    // 音频焦点被抢等压制状态同样不改变 playWhenReady，需单独同步
    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
      updateState()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
      updateState()
      updatePositionTicker()
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
      _state.value = _state.value.copy(speed = playbackParameters.speed)
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
      _state.value = _state.value.copy(videoAspectRatio = videoSize.toAspectRatio())
    }

    override fun onPositionDiscontinuity(
      oldPosition: Player.PositionInfo,
      newPosition: Player.PositionInfo,
      reason: Int
    ) {
      if (reason == Player.DISCONTINUITY_REASON_SEEK) {
        resetPositionBase(newPosition.positionMs.coerceAtLeast(0L))
      }
      updatePosition()
      if (reason == Player.DISCONTINUITY_REASON_SEEK) {
        val mediaIdStr = exoPlayer.currentMediaItem?.mediaId ?: return
        val mediaId = mediaIdStr.toLongOrNull() ?: return
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

  init {
    // ExoPlayer 明确允许在任意线程注册监听，无需切主线程
    exoPlayer.addListener(listener)
  }

  override fun prepare(
    media: PlaybackMedia,
    startPositionMs: Long,
    playWhenReady: Boolean
  ) {
    runOnPlayerThread {
      val safePositionMs = startPositionMs.coerceAtLeast(0L)
      // 必须在重置 state 之前取，重置后读到的已经是新媒体
      val mediaChanged = _state.value.currentMedia?.mediaId != media.mediaId

      // 状态整体重置，必须发生在操作 exoPlayer 之前：下面的调用会同步触发
      // Player.Listener，而 updateState() 是在当前 state 上做 copy 且不涉及
      // currentMedia，若这里还没换掉，回调就会发出「上一个媒体配新状态」的组合。
      // 位置和播放态按本次请求填入，避免进度条闪回 0、播放图标闪回暂停。
      _state.value = PlaybackState(
        currentMedia = media,
        isPlaying = playWhenReady,
        speed = _state.value.speed,
        subtitleVisible = _state.value.subtitleVisible
      )
      _progress.value = PlaybackProgress(positionMs = safePositionMs)

      exoPlayer.setMediaItem(createMediaItem(media), safePositionMs)
      // setMediaItem 不会清掉上一媒体遗留的 playWhenReady，
      // 先置 false 避免上一首的播放意图被带到新媒体上
      exoPlayer.playWhenReady = false
      exoPlayer.prepare()
      if (playWhenReady) {
        exoPlayer.play()
      }

      // prepare() 会触发 onPlaybackStateChanged → updateState()，用真实状态覆盖上面
      // 的乐观预设，末尾无需再读一次。
      if (mediaChanged) {
        _events.tryEmit(PlaybackEvent.MediaChanged(media, safePositionMs))
      }

      // 以新媒体的起播位置作为插值锚点，避免沿用上一媒体的旧锚点
      resetPositionBase(safePositionMs)
    }
  }

  override fun play() {
    runOnPlayerThread {
      prepareIfIdle()
      exoPlayer.play()
      updateState()
    }
  }

  /**
   * 播放器停在 IDLE 时重新准备，位置保留在中断处；非 IDLE 时无副作用。
   *
   * 应用长时间退到后台后，系统可能把稀缺的 MediaCodec 解码器回收给前台应用，
   * ExoPlayer 随之报错并回落到 STATE_IDLE：画面变黑，而 play() 只是把
   * playWhenReady 置为 true，不触发任何加载，表现为「点播放按钮没反应」。
   * 官方的恢复方式就是重新 prepare()。
   *
   * 由 play() 和回到前台时各调一次：前者让播放起得来，后者让画面回得来。
   * 恢复本身不接管播放意图，因此先把 playWhenReady 归零，避免切回应用时被突然出声；
   * play() 路径紧跟其后的 play() 会重新置上。
   */
  fun prepareIfIdle() {
    if (exoPlayer.playbackState != Player.STATE_IDLE) {
      return
    }
    // 没有媒体可恢复（如刚 clear 过），交给正常的 prepare 流程处理
    if (exoPlayer.currentMediaItem == null) {
      return
    }
    exoPlayer.playWhenReady = false
    exoPlayer.prepare()
    _state.value = _state.value.copy(error = null)
  }

  override fun pause() {
    runOnPlayerThread {
      exoPlayer.pause()
    }
  }

  override fun seekTo(positionMs: Long) {
    runOnPlayerThread {
      val safePositionMs = positionMs.coerceAtLeast(0L)
      exoPlayer.seekTo(safePositionMs)
      // seek 之后位置直接跳到目标值，锚点一并重置，避免用旧锚点做插值
      resetPositionBase(safePositionMs)
      updatePosition()
    }
  }

  override fun setSpeed(speed: Float) {
    runOnPlayerThread {
      val safeSpeed = speed.coerceIn(0.1f, 4f)
      exoPlayer.setPlaybackSpeed(safeSpeed)
      _state.value = _state.value.copy(speed = safeSpeed)
      // 倍速改变会改变插值斜率，以当前位置重新起锚保持平滑
      resetPositionBase()
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

    // 字幕只能随 MediaItem 在 prepare 阶段配置，因此需要原位重建并恢复现场
    val positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
    val playWhenReady = exoPlayer.playWhenReady
    val speed = exoPlayer.playbackParameters.speed

    exoPlayer.setMediaItem(createMediaItem(updatedMedia), positionMs)
    exoPlayer.setPlaybackSpeed(speed)
    exoPlayer.playWhenReady = false
    exoPlayer.prepare()
    if (playWhenReady) {
      exoPlayer.play()
    }
    // 原位重建后位置仍在 positionMs，重置锚点，不依赖 setMediaItem 是否派发 SEEK 事件
    resetPositionBase(positionMs)
    _state.value = _state.value.copy(
      currentMedia = updatedMedia,
      error = null
    )
    updateState()
  }

  override fun setSubtitleVisible(visible: Boolean) {
    runOnPlayerThread {
      // 字幕是否渲染由 Media3 的轨道选择参数控制，不动已选字幕配置本身
      val disabledTrackTypes = exoPlayer.trackSelectionParameters.disabledTrackTypes.toMutableSet()
      if (visible) {
        disabledTrackTypes.remove(C.TRACK_TYPE_TEXT)
      } else {
        disabledTrackTypes.add(C.TRACK_TYPE_TEXT)
      }
      exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
        .buildUpon()
        .setDisabledTrackTypes(disabledTrackTypes)
        .build()
      _state.value = _state.value.copy(subtitleVisible = visible)
    }
  }

  override fun clear() {
    runOnPlayerThread {
      val mediaChanged = _state.value.currentMedia != null
      exoPlayer.clearMediaItems()
      _state.value = PlaybackState(
        speed = _state.value.speed,
        subtitleVisible = _state.value.subtitleVisible
      )
      _progress.value = PlaybackProgress()
      resetPositionBase(0L)
      if (mediaChanged) {
        _events.tryEmit(PlaybackEvent.MediaChanged(media = null, positionMs = 0L))
      }
    }
  }

  override fun attach(playerView: PlayerView) {
    runOnPlayerThread {
      attachedViews.add(playerView)
      playerView.player = exoPlayer
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
      .setMediaId(media.mediaId.toString())
      .setUri(media.uri)
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setTitle(media.title)
          .setArtworkUri(media.uri.toUri())
          .build()
      )

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
    _state.value = _state.value.copy(
      isPlaying = exoPlayer.isPlayIntended(),
      speed = exoPlayer.playbackParameters.speed
    )
    updatePosition()
  }

  /**
   * 播放意图判定：只要用户想播、媒体没结束、且没有被系统压制，就算播放态。
   *
   * 不能直接用 [Player.isPlaying]，它在 STATE_BUFFERING 期间返回 false，
   * 会导致缓冲瞬间 UI 闪回暂停图标、误导用户再点一次播放。
   *
   * 也必须排除 playbackSuppressionReason：音频焦点被抢时 playWhenReady 仍为 true，
   * 但实际并没有出声，此时不应显示为播放中。
   */
  private fun Player.isPlayIntended(): Boolean {
    return playWhenReady &&
        playbackState != Player.STATE_IDLE &&
        playbackState != Player.STATE_ENDED &&
        playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
  }

  private fun Player.safeDurationMs(): Long {
    val rawDuration = duration
    if (rawDuration == C.TIME_UNSET) {
      return 0L
    }
    return rawDuration.coerceAtLeast(0L)
  }

  /**
   * 换算出用于布局的宽高比。
   *
   * [VideoSize.width] / [VideoSize.height] 是解码后的像素尺寸，已计入旋转元数据；
   * 再乘 [VideoSize.pixelWidthHeightRatio] 才是非方形像素（部分老设备录制）下的显示比例。
   * 纯音频与尚未出画面时尺寸为 0，统一返回 0 表示未知。
   */
  private fun VideoSize.toAspectRatio(): Float {
    if (width <= 0 || height <= 0) {
      return 0f
    }
    return width * pixelWidthHeightRatio / height
  }

  private fun updatePositionTicker() {
    positionJob?.cancel()
    positionJob = null
    // 缓冲期间进度不推进，只在真正出声时轮询
    if (!exoPlayer.isPlaying) {
      return
    }

    // 从恢复播放的瞬时位置重新起锚，避免沿用暂停前的老锚点产生跳变
    resetPositionBase()

    positionJob = scope.launch {
      while (isActive && exoPlayer.isPlaying) {
        updatePosition()
        delay(POSITION_INTERVAL_MS)
      }
    }
  }

  /** 重置插值锚点到指定位置。seek、切媒体、速度变化等改变播放节奏的场景必须调用。 */
  private fun resetPositionBase(positionMs: Long = exoPlayer.currentPosition.coerceAtLeast(0L)) {
    lastRawPositionMs = positionMs
    lastRawTimeMs = SystemClock.elapsedRealtime()
  }

  /**
   * 发布当前播放位置。音频播放时 ExoPlayer 的 currentPosition 更新粒度较粗
   * （取决于音频 buffer 大小与设备驱动，可能连续多次读到同一个值），
   * 直接上报会导致进度条"一跳一跳"。
   *
   * 处理方式：拿到当前真实位置后与上一次读取值对比——若位置没前进且正在播放，
   * 就以「上一次真实位置 + 经过时间 × 速度」做插值，让进度连续推进；
   * 一旦位置推进（拿到新真实值），立即采用新真实值。插值起点永远是最后读到
   * 的真实位置，因此估算值只会填充真实值之间的空档，不会超前或跳变。
   */
  private fun updatePosition() {
    val now = SystemClock.elapsedRealtime()
    val durationMs = exoPlayer.safeDurationMs()

    // 当前的真实读取值；与前一次相同说明音频硬件尚未推进
    val actual = exoPlayer.currentPosition.coerceAtLeast(0L)

    val positionMs = if (lastRawPositionMs != -1L
      && _state.value.currentMedia?.isVideo == false
      && actual == lastRawPositionMs
      && exoPlayer.isPlaying
    ) {
      // 位置停在同一个值且正在播放：用插值补足推进，起点是最后一次真实位置
      val elapsed = now - lastRawTimeMs
      val speed = exoPlayer.playbackParameters.speed
      lastRawPositionMs + (elapsed * speed).toLong() - 1
    } else {
      // 位置有推进（或未播放/尚未读过）：直接采用真实位置，并重置上一次读取记录
      lastRawPositionMs = actual
      lastRawTimeMs = now
      actual
    }

    // 时长未知（未解析出、流式无时长）时不钳制，否则播放头会被压到 0
    val safePositionMs = if (durationMs > 0L) {
      positionMs.coerceAtMost(durationMs)
    } else {
      positionMs
    }

    _progress.value = PlaybackProgress(
      positionMs = safePositionMs,
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
    const val POSITION_INTERVAL_MS = 30L
  }
}
