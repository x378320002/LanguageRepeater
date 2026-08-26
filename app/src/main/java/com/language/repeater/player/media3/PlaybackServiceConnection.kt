package com.language.repeater.player.media3

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.language.repeater.playcore.PlaybackService

class PlaybackServiceConnection(
  context: Context
) {
  private val context = context.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())
  private var controllerFuture: ListenableFuture<MediaController>? = null
  private var mediaController: MediaController? = null
  private var retryCount = 0
  private var connectionGeneration = 0L
  private var retryRunnable: Runnable? = null

  fun connect() {
    if (controllerFuture != null || mediaController != null) {
      return
    }

    cancelPendingRetry()
    retryCount = 0
    connectionGeneration += 1
    connectInternal(connectionGeneration)
  }

  fun disconnect() {
    connectionGeneration += 1
    cancelPendingRetry()
    controllerFuture?.let(MediaController::releaseFuture)
    controllerFuture = null
    mediaController?.release()
    mediaController = null
    retryCount = 0
  }

  private fun connectInternal(generation: Long) {
    if (generation != connectionGeneration ||
      controllerFuture != null ||
      mediaController != null
    ) {
      return
    }

    val sessionToken = SessionToken(
      context,
      ComponentName(context, PlaybackService::class.java)
    )
    val future = MediaController.Builder(context, sessionToken)
      .setListener(
        object : MediaController.Listener {
          override fun onDisconnected(controller: MediaController) {
            if (mediaController !== controller || generation != connectionGeneration) {
              return
            }
            controller.release()
            mediaController = null
            scheduleRetry(generation, "播放服务连接断开")
          }
        }
      )
      .buildAsync()
    controllerFuture = future
    future.addListener(
      {
        runCatching {
          future.get()
        }.onSuccess { controller ->
          if (controllerFuture === future && generation == connectionGeneration) {
            controllerFuture = null
            mediaController = controller
            retryCount = 0
          } else {
            controller.release()
          }
        }.onFailure { throwable ->
          if (controllerFuture === future) {
            controllerFuture = null
          }
          Log.e(TAG, "连接播放服务失败", throwable)
          scheduleRetry(generation, "播放服务连接失败")
        }
      },
      MoreExecutors.directExecutor()
    )
  }

  private fun scheduleRetry(generation: Long, reason: String) {
    if (generation != connectionGeneration || retryRunnable != null) {
      return
    }
    if (retryCount >= MAX_RETRY_COUNT) {
      Log.w(TAG, "$reason，已达到最大重试次数")
      return
    }

    retryCount += 1
    val delayMs = RETRY_DELAYS_MS[retryCount - 1]
    val runnable = Runnable {
      retryRunnable = null
      connectInternal(generation)
    }
    retryRunnable = runnable
    mainHandler.postDelayed(runnable, delayMs)
  }

  private fun cancelPendingRetry() {
    val runnable = retryRunnable ?: return
    mainHandler.removeCallbacks(runnable)
    retryRunnable = null
  }

  private companion object {
    const val TAG = "PlaybackServiceConnection"
    const val MAX_RETRY_COUNT = 3
    val RETRY_DELAYS_MS = longArrayOf(500L, 1_500L, 3_000L)
  }
}
