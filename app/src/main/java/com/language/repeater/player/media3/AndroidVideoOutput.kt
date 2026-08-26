package com.language.repeater.player.media3

import androidx.media3.ui.PlayerView

interface AndroidVideoOutput {
  fun attach(playerView: PlayerView)

  fun detach(playerView: PlayerView)
}
