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

/**
 * 绑定并保活 PlaybackService。
 *
 * 该连接由 Application 持有、与进程同生共死，不提供断开能力：
 * MediaController 只用于把 Service 绑定住，业务播放控制直接走同进程的 ExoPlayer。
 * 因此这里只需处理「首次连接」和「服务进程崩溃后重连」两种情况。
 *
 * 所有方法都必须在主线程调用。
 */
class PlaybackServiceConnection(
  context: Context
) {
  private val context = context.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())
  private var controllerFuture: ListenableFuture<MediaController>? = null
  private var mediaController: MediaController? = null
  private var retryCount = 0

  fun connect() {
    // 已连接或正在连接时无需重复发起
    if (controllerFuture != null || mediaController != null) {
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
            if (mediaController !== controller) {
              return
            }
            // 只有服务进程消亡才会走到这里，释放旧 controller 后重连
            controller.release()
            mediaController = null
            scheduleRetry("播放服务连接断开")
          }
        }
      )
      .buildAsync()
    controllerFuture = future
    future.addListener(
      {
        // directExecutor 下回调运行在完成 future 的线程，即 Media3 的主线程
        runCatching {
          future.get()
        }.onSuccess { controller ->
          controllerFuture = null
          mediaController = controller
          retryCount = 0
        }.onFailure { throwable ->
          controllerFuture = null
          Log.e(TAG, "连接播放服务失败", throwable)
          scheduleRetry("播放服务连接失败")
        }
      },
      MoreExecutors.directExecutor()
    )
  }

  private fun scheduleRetry(reason: String) {
    if (retryCount >= MAX_RETRY_COUNT) {
      // 退避重试用尽，等待下一次前台交互触发 connect()
      Log.w(TAG, "$reason，已达到最大重试次数")
      retryCount = 0
      return
    }

    val delayMs = RETRY_DELAYS_MS[retryCount]
    retryCount += 1
    mainHandler.postDelayed({ connect() }, delayMs)
  }

  private companion object {
    const val TAG = PlaybackService.TAG
    const val MAX_RETRY_COUNT = 3
    val RETRY_DELAYS_MS = longArrayOf(500L, 1_500L, 3_000L)
  }
}
