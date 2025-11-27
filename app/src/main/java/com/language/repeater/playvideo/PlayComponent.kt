package com.language.repeater.playvideo

import androidx.core.content.ContentProviderCompat.requireContext
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.pcm.Sentence

class PlayComponent: BaseComponent<PlayVideoFragment>() {
  companion object {
    const val TAG = PlayVideoFragment.TAG
  }

  private var curPosition = 0L
  private var exoPlayer: ExoPlayer? = null
  //当前所有的语音片段
  private var voiceSegments = listOf<Sentence>()

  override fun onCreate() {
    super.onCreate()
    exoPlayer = getPlayer()
  }

  @UnstableApi
  override fun onCreateView() {
    super.onCreateView()

  }

  fun getPlayer(): ExoPlayer {
    var player = exoPlayer
    if (player == null) {
      player = ExoPlayer.Builder(requireContext()).build().apply {
        repeatMode = Player.REPEAT_MODE_ALL
      }
      exoPlayer = player
    }
    return player
  }
}