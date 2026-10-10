package com.language.repeater.playvideo.compose

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.language.repeater.R
import com.language.repeater.datastore.DsManager
import com.language.repeater.player.togglePlayPause
import com.language.repeater.playvideo.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

const val TAG = "VideoPlayerBox"

/**
 * Android 视频输出。Composable 只绑定和解绑画面，不持有或释放底层播放器。
 *
 * @param fullScreen true 时画面铺满父容器、纯黑底、不显示进入全屏按钮；
 *   false 时按 16:9 卡片样式摆放，右下角带进入全屏按钮。
 * @param onTap 单击画面的行为。默认播放/暂停；全屏布局可改为切换控制面板显隐。
 */
@Composable
fun VideoPlayerBox(
  viewModel: PlayerViewModel,
  modifier: Modifier = Modifier,
  fullScreen: Boolean = false,
  onTap: (() -> Unit)? = null
) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val player = viewModel.player
  val sentenceController = viewModel.sentenceController
  val videoOutput = viewModel.videoOutput
  val state = remember(videoOutput, scope) {
    VideoPlayerBoxState(context, scope)
  }

  val supportLeft by DsManager.observe(DsManager.KEY_LEFT_BRIGHTNESS_GESTURE, true).collectAsState()
  val supportRight by DsManager.observe(DsManager.KEY_RIGHT_VOLUME_GESTURE, true).collectAsState()

  val surfaceModifier = if (fullScreen) {
    Modifier
      .fillMaxSize()
      .background(Color.Black)
  } else {
    Modifier
      .fillMaxWidth()
      .aspectRatio(16f / 9f)
      .clip(CardDefaults.shape)
      .background(CardDefaults.cardColors().containerColor)
  }

  Box(
    modifier = modifier
      .then(surfaceModifier)
      .detectHorizontalFling(onFlingLeft = {
        scope.launch {
          Log.i(TAG, "detectHorizontalFling onFlingLeft")
          sentenceController.seekToNextSentence()
        }
      }, onFlingRight = {
        scope.launch {
          Log.i(TAG, "detectHorizontalFling onFlingRight")
          sentenceController.seekToPreviousSentence()
        }
      })
      .pointerInput(Unit) {
        var isLeftDrag = true
        detectVerticalDragGestures(
          onDragStart = {
            isLeftDrag = it.x < size.width / 2
          },
        ) { change, dragAmount ->
          if (isLeftDrag && supportLeft) {
            Log.i(TAG, "detectVerticalDragGestures leftDrag, dragAmount:${dragAmount}")
            state.adjustBrightness(dragAmount)
          } else if (!isLeftDrag && supportRight) {
            Log.i(TAG, "detectVerticalDragGestures rightDrag, dragAmount:${dragAmount}")
            state.adjustVolume(dragAmount)
          }
        }
      }
      .pointerInput(onTap) {
        var oldSpeed: Float? = null
        detectTapGestures(
          onLongPress = {
            Log.i(TAG, "detectTapGestures onLongPress")
            oldSpeed = player.state.value.speed
            scope.launch {
              val speed = oldSpeed ?: return@launch
              val newSpeed = speed * DsManager.get(DsManager.KEY_LONG_PRESS_SPEED, 1.0f)
              player.setSpeed(newSpeed)
              state.showFeedback("⏩ ${newSpeed}X")
            }
          },
          onDoubleTap = {
            Log.i(TAG, "detectTapGestures onDoubleTap")
            sentenceController.backToSentenceHead()
          },
          onTap = {
            Log.i(TAG, "detectTapGestures onTap")
            if (onTap != null) {
              onTap()
            } else {
              player.togglePlayPause()
            }
          },
          onPress = {
            val press = tryAwaitRelease()
            if (press && oldSpeed != null) {
              player.setSpeed(oldSpeed ?: 1.0f)
            }
            oldSpeed = null
          }
        )
      }
  ) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { context ->
        PlayerView(context).apply {
          useController = false
          videoOutput.attach(this)
        }
      },
      update = { playerView ->
        videoOutput.attach(playerView)
      },
      onRelease = { playerView ->
        videoOutput.detach(playerView)
      }
    )

//    if (!fullScreen) {
//      IconButton(
//        onClick = {
//          viewModel.changeScreenMode(true)
//        },
//        colors = IconButtonDefaults.iconButtonColors(contentColor = LocalContentColor.current.copy(alpha = 0.6f)),
//        modifier = Modifier.align(Alignment.BottomEnd)
//      ) {
//        Icon(
//          painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_maximize_outline),
//          contentDescription = null
//        )
//      }
//    }

    val text = state.tipText
    if (text.isNotEmpty()) {
      Text(
        modifier = Modifier
          .align(alignment = Alignment.TopCenter)
          .padding(top = 10.dp)
          .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.medium)
          .padding(horizontal = 8.dp),
        fontSize = 12.sp,
        text = text
      )
    }
  }
}

fun Modifier.detectHorizontalFling(
  minFlingVelocity: Float = 1000f,
  onFlingLeft: () -> Unit,
  onFlingRight: () -> Unit
): Modifier = this.pointerInput(Unit) {
  var velocityTracker: VelocityTracker? = null
  detectHorizontalDragGestures(
    onDragStart = {
      velocityTracker = VelocityTracker()
    },
    onDragEnd = {
      val velocityX = velocityTracker?.calculateVelocity()?.x ?: 0f
      if (abs(velocityX) > minFlingVelocity) {
        if (velocityX > 0) {
          onFlingRight()
        } else {
          onFlingLeft()
        }
      }
    }
  ) { change, dragAmount ->
    change.consume()
    velocityTracker?.addPosition(change.uptimeMillis, change.position)
  }
}

class VideoPlayerBoxState(val context: Context, val scope: CoroutineScope) {
  var tipText by mutableStateOf("")
    private set

  private val window = (context as Activity).window
  private var currentBrightness = (context as Activity).window.attributes?.screenBrightness ?: -1f
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
  private var maxVolume: Int = 0
  private var currentVolume: Int = 0

  init {
    initBrightness()
    initAudioManager()
  }

  private fun initAudioManager() {
    maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: run {
      Log.w(TAG, "Using default max volume: 15")
      15
    }
    currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
  }

  private fun initBrightness() {
    currentBrightness = window?.attributes?.screenBrightness ?: -1f
    if (currentBrightness < 0) {
      currentBrightness = 0.5f
    }
  }

  fun adjustBrightness(delta: Float) {
    try {
      if (currentBrightness !in 0.0..1.0) {
        currentBrightness = window.attributes.screenBrightness
        if (currentBrightness < 0) {
          currentBrightness = 0.5f
        }
      }

      //val change = delta * BRIGHTNESS_SENSITIVITY
      val steps = if (delta < 0) {
        0.01f
      } else if (delta > 0) {
        -0.01f
      } else {
        0f
      }
      currentBrightness = (currentBrightness + steps).coerceIn(0.01f, 1.0f)
      val layoutParams = window.attributes
      layoutParams.screenBrightness = currentBrightness
      window.attributes = layoutParams
      val percentage = (currentBrightness * 100).toInt()
      showFeedback("☀️ $percentage%")
    } catch (e: Exception) {
      Log.e(TAG, "Error adjusting brightness", e)
    }
  }

  fun adjustVolume(delta: Float) {
    try {
      val manager = audioManager ?: return
      //val steps = (delta * VOLUME_SENSITIVITY).toInt().coerceAtLeast(1)
      val steps = if (delta < 0) {
        1
      } else if (delta > 0) {
        -1
      } else {
        0
      }
      Log.d(TAG, "adjustVolume steps:$steps delta:$delta")
      if (steps == 0) return

      currentVolume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
      val newVolume = (currentVolume + steps).coerceAtLeast(0)
      manager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
      currentVolume = newVolume
      val percentage = (currentVolume * 100 / maxVolume)
      showFeedback("🔊 $percentage%")
    } catch (e: Exception) {
      Log.e(TAG, "Error adjusting volume", e)
    }
  }

  var job: Job? = null
  fun showFeedback(text: String) {
    tipText = text
    job?.cancel()
    job = scope.launch {
      delay(2000)
      tipText = ""
    }
  }
}


