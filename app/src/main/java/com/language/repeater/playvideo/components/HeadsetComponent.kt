package com.language.repeater.playvideo.components

import android.util.Log
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.playvideo.PlayVideoFragment

/**
 * Date: 2025-11-14
 * Time: 16:21
 * Description: 耳机播控处理逻辑
 */
class HeadsetComponent: BaseComponent<PlayVideoFragment>() {
  private var mediaSession: MediaSession? = null
  companion object {
    const val TAG = PlayVideoFragment.Companion.TAG
  }

  @UnstableApi
  override fun onCreateView() {
    super.onCreateView()

    val myPlayer = object : ForwardingPlayer(fragment.playComponent.player) {
      /**
       * 覆写“播放”命令
       */
      override fun play() {
        // --- 在这里执行你的自定义“播放”逻辑 ---
        Log.d(TAG, "play() 已被拦截！执行自定义操作。")
        //super.play()
        fragment.playComponent.setRepeat(!fragment.playComponent.repeatable)
        fragment.binding.voiceRepeatSwitch.isChecked = fragment.playComponent.repeatable
      }

      /**
       * 覆写“暂停”命令
       */
      override fun pause() {
        Log.d(TAG, "pause() 已被拦截！执行自定义操作。")
        //super.pause()
        fragment.playComponent.setRepeat(!fragment.playComponent.repeatable)
        fragment.binding.voiceRepeatSwitch.isChecked = fragment.playComponent.repeatable
      }

      override fun increaseDeviceVolume(flags: Int) {
        //super.increaseDeviceVolume(flags)
        Log.d(TAG, "increaseDeviceVolume。")
        fragment.playComponent.seekToNextSentence()
      }

      override fun decreaseDeviceVolume(flags: Int) {
        //super.decreaseDeviceVolume(flags)
        Log.d(TAG, "decreaseDeviceVolume。")
        fragment.playComponent.seekToPreviousSentence()
      }

      /**
       * 覆写“下一首”命令
       */
      override fun seekToNext() {
        super.seekToNext()
      }

      /**
       * 覆写“上一首”命令
       */
      override fun seekToPrevious() {
        super.seekToPrevious()
      }
    }

    // 4c. 创建 MediaSession 并绑定
    mediaSession = MediaSession.Builder(context, myPlayer)
      .setId("HeadsetComponent" + System.currentTimeMillis())
      .build()
  }
}