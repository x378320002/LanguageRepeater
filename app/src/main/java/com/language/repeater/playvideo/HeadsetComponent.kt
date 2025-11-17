package com.language.repeater.playvideo

import android.util.Log
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.language.repeater.foundation.BaseComponent

/**
 * Date: 2025-11-14
 * Time: 16:21
 * Description:
 */
class HeadsetComponent: BaseComponent<PlayVideoFragment>() {
  private var mediaSession: MediaSession? = null
  companion object {
    const val TAG = PlayVideoFragment.TAG
  }

  override fun onCreateView() {
    super.onCreateView()
//    val mediaCallback = object : MediaSession.Callback {
//      // 处理“播放”或“暂停” (通常是中间键单击)
//      private fun handlePlayPause() {
//        Log.d(TAG, "onPlay/onPause 被触发 (中间键单击)")
//      }
//
//      override fun onPlay(
//        session: MediaSession,
//        controller: MediaSession.ControllerInfo
//      ): ListenableFuture<SessionResult> {
//        handlePlayPause()
//        // 关键：返回成功, 告诉系统我们已处理
//        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
//      }
//
//      override fun onPause(
//        session: MediaSession,
//        controller: MediaSession.ControllerInfo
//      ): ListenableFuture<SessionResult> {
//        handlePlayPause()
//        // 关键：返回成功
//        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
//      }
//
//      // 处理“下一首” (双击 / 长按音量+ / AVRCP)
//      override fun onSkipToNext(
//        session: MediaSession,
//        controller: MediaSession.ControllerInfo
//      ): ListenableFuture<SessionResult> {
//        Log.d(TAG, "onSkipToNext() 被触发 (下一首)")
//        listener?.onNextClicked()
//        // 关键：返回成功
//        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
//      }
//
//      // 处理“上一首” (三击 / 长按音量- / AVRCP)
//      override fun onSkipToPrevious(
//        session: MediaSession,
//        controller: MediaSession.ControllerInfo
//      ): ListenableFuture<SessionResult> {
//        Log.d(TAG, "onSkipToPrevious() 被触发 (上一首)")
//        listener?.onPreviousClicked()
//        // 关键：返回成功
//        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
//      }
//    }
//
//    // 4c. 创建 MediaSession 并绑定
//    mediaSession = MediaSession.Builder(fragment!!, playerInstance)
//      .setCallback(mediaCallback)
//      .build()
//
//    // 4d. (可选) media3 会自动处理激活, 但明确设置一下更保险
//    mediaSession?.isActive = true
  }

  override fun onDestroyView() {
    super.onDestroyView()
  }
}