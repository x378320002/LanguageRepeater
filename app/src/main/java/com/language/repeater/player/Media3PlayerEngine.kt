package com.language.repeater.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import com.language.repeater.playcore.PlaybackCore
import com.language.repeater.playvideo.model.VideoEntity
import com.language.repeater.playvideo.model.isPlaceHold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Media3 播放器引擎适配器 — 把 PlaybackCore 包装成 PlayerEngine
 *
 * 这是唯二允许 import androidx.media3.* 的文件（另一个是 PlaybackCore.kt）
 * 所有业务层代码必须通过 PlayerEngine 接口访问播放能力
 */
class Media3PlayerEngine(
  private val context: Context,
  private val playbackCore: PlaybackCore
) : PlayerEngine {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  private val _state = MutableStateFlow(PlayerEngineState())
  override val state: StateFlow<PlayerEngineState> = _state.asStateFlow()

  private val _playlist = MutableStateFlow<List<PlaylistItem>>(emptyList())
  override val playlist: StateFlow<List<PlaylistItem>> = _playlist.asStateFlow()

  private val _currentIndex = MutableStateFlow(0)
  override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

  init {
    // 桥接 PlaybackCore 的各个状态流到统一的 PlayerEngineState
    scope.launch {
      playbackCore.isPlaying.collect { isPlaying ->
        _state.value = _state.value.copy(isPlaying = isPlaying)
      }
    }
    scope.launch {
      playbackCore.playbackState.collect { playbackState ->
        _state.value = _state.value.copy(
          playbackState = when (playbackState) {
            Player.STATE_IDLE -> PlaybackState.Idle
            Player.STATE_BUFFERING -> PlaybackState.Buffering
            Player.STATE_READY -> PlaybackState.Ready
            Player.STATE_ENDED -> PlaybackState.Ended
            else -> PlaybackState.Idle
          }
        )
        // STATE_READY 时 duration 才可用，更新它
        if (playbackState == Player.STATE_READY) {
          val player = playbackCore.playerInstance.value
          val duration = player?.duration ?: 0L
          _state.value = _state.value.copy(
            durationMs = if (duration == C.TIME_UNSET.toLong()) 0L else duration
          )
        }
      }
    }
    scope.launch {
      playbackCore.currentPosition.collect { position ->
        _state.value = _state.value.copy(positionMs = position)
      }
    }
    scope.launch {
      playbackCore.playSpeed.collect { speed ->
        _state.value = _state.value.copy(speed = speed)
      }
    }
    // duration 需要从 playerInstance 获取，这里监听 playerInstance 变化
    scope.launch {
      playbackCore.playerInstance.collect { player ->
        val duration = player?.duration ?: 0L
        _state.value = _state.value.copy(
          durationMs = if (duration == C.TIME_UNSET.toLong()) 0L else duration
        )
        // 同时刷新播放列表
        refreshPlaylist(player)
      }
    }
    // 监听播放列表刷新事件
    scope.launch {
      playbackCore.playlistRefreshEvent.collect {
        refreshPlaylist(playbackCore.playerInstance.value)
      }
    }
    // 监听当前媒体项变化
    scope.launch {
      playbackCore.currentMediaItem.collect { mediaItem ->
        updateCurrentIndex(mediaItem)
        // 更新当前媒体标题
        _state.value = _state.value.copy(
          currentMediaTitle = mediaItem?.mediaMetadata?.title?.toString() ?: ""
        )
      }
    }
    // 监听播放状态变化，更新播放列表项状态
    scope.launch {
      playbackCore.isPlaying.collect { isPlaying ->
        updatePlaylistPlayingState(isPlaying)
      }
    }
  }

  private fun refreshPlaylist(player: Player?) {
    if (player == null) {
      _playlist.value = emptyList()
      return
    }
    val items = mutableListOf<PlaylistItem>()
    for (i in 0 until player.mediaItemCount) {
      val item = player.getMediaItemAt(i)
      if (!item.isPlaceHold()) {
        items.add(
          PlaylistItem(
            id = item.mediaId,
            title = item.mediaMetadata.title?.toString() ?: "",
            uri = item.localConfiguration?.uri?.toString() ?: "",
            durationMs = if (player.duration == C.TIME_UNSET.toLong()) 0L else player.duration,
            isPlaying = i == player.currentMediaItemIndex && player.isPlaying,
            isCurrent = i == player.currentMediaItemIndex,
          )
        )
      }
    }
    _playlist.value = items
    _currentIndex.value = player.currentMediaItemIndex.coerceAtLeast(0)
  }

  private fun updateCurrentIndex(mediaItem: androidx.media3.common.MediaItem?) {
    val player = playbackCore.playerInstance.value ?: return
    val index = (0 until player.mediaItemCount).firstOrNull { i ->
      player.getMediaItemAt(i).mediaId == mediaItem?.mediaId
    } ?: -1
    _currentIndex.value = index
    // 刷新播放列表的 isCurrent 标记
    _playlist.value = _playlist.value.mapIndexed { i, item ->
      item.copy(isCurrent = i == index)
    }
  }

  private fun updatePlaylistPlayingState(isPlaying: Boolean) {
    val currentIdx = _currentIndex.value
    _playlist.value = _playlist.value.mapIndexed { i, item ->
      item.copy(isPlaying = i == currentIdx && isPlaying)
    }
  }

  override fun setSource(source: MediaSource) {
    // 将 MediaSource 转换为 VideoEntity，再通过 PlaybackCore 播放
    val videoEntity = VideoEntity(
      id = source.uri.hashCode().toString(),
      uri = source.uri,
      name = source.uri.substringAfterLast('/'),
      subUri = null
    )
    playbackCore.playItem(videoEntity)
  }

  override fun play() {
    playbackCore.play()
  }

  override fun pause() {
    playbackCore.pause()
  }

  override fun seekTo(positionMs: Long) {
    playbackCore.seekTo(positionMs)
  }

  override fun setSpeed(speed: Float) {
    // PlaybackCore 目前没有直接暴露 setSpeed，通过 playerInstance 设置
    playbackCore.playerInstance.value?.setPlaybackSpeed(speed)
  }

  override fun seekToItem(index: Int) {
    playbackCore.seekToItem(index)
  }

  override fun moveItem(from: Int, to: Int) {
    playbackCore.changeItemPosition(from, to)
  }

  override fun removeItem(index: Int) {
    playbackCore.removeMediaItem(index)
  }

  override fun release() {
    playbackCore.playerInstance.value?.release()
  }
}
