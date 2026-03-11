package com.language.repeater.playvideo.components

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.TextView
import com.language.repeater.dataStore
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.utils.DataStoreUtil
import com.language.repeater.widgets.GestureCardView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

class PlayGestureComponent : BaseComponent<PlayVideoFragment>() {

  companion object {
    private const val TAG = "PlayGestureComponent"
    private const val GESTURE_TIPS_DISPLAY_DURATION = 1500L
    private const val FLING_VELOCITY_THRESHOLD = 1000f
  }

  private var audioManager: AudioManager? = null
  private var maxVolume: Int = 0
  private var currentVolume: Int = 0
  private var currentBrightness: Float = -1f
  private var gestureTipsView: TextView? = null
  private var hideGestureTipJob: Job? = null

  override fun onCreateView() {
    super.onCreateView()
    Log.d(TAG, "onCreateView: Initializing gesture component")
    initAudioManager()
    initBrightness()
    gestureTipsView = fragment.binding.gestureTipsText
    fragment.binding.root.isHapticFeedbackEnabled = true
    if (fragment.isLandScreen) {
      fragment.binding.root.setOnGestureListener(gestureListener)
    } else {
      uiScope.launch {
        context.dataStore.data.map {
          it[DataStoreUtil.KEY_FULL_GESTURE] ?: false
        }.collect {
          if (it) {
            fragment.binding.root.setOnGestureListener(gestureListener)
            (fragment.binding.exoVideoViewWrapper as GestureCardView).setOnGestureListener(null)
          } else {
            (fragment.binding.exoVideoViewWrapper as GestureCardView).setOnGestureListener(gestureListener)
            fragment.binding.root.setOnGestureListener(null)
          }
        }
      }
    }
  }

  private var gestureListener = object : GestureCardView.OnGestureListener {
    var originalSpeed = 1.0f
    override fun onClick() {
      fragment.viewModel.togglePlayPause()
    }

    override fun onDoubleClick() {
      fragment.viewModel.backToSentenceHead()
    }

    override fun onLongPressed(x: Float, y: Float) {
      val player = fragment.viewModel.getPlayer() ?: return
      originalSpeed = player.playbackParameters.speed
      val speed = originalSpeed * 0.5f
      player.setPlaybackSpeed(speed)
      showFeedback("⏩ ${speed}X", false)
      fragment.binding.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    override fun onLongPressedEnd() {
      val player = fragment.viewModel.getPlayer() ?: return
      player.setPlaybackSpeed(originalSpeed)
      //showFeedback("⏩ ${originalSpeed}X")
      gestureTipsView?.visibility = View.GONE
    }

    override fun onHorizontalScroll(deltaX: Float, deltaY: Float) {
      //nothing
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
      if (abs(velocityX) < abs(velocityY)) return
      if (abs(velocityX) < FLING_VELOCITY_THRESHOLD) return

      if (velocityX > 0) {
        fragment.viewModel.seekToPreviousSentence()
        showFeedback("👈🏻 Previous")
      } else {
        fragment.viewModel.seekToNextSentence()
        showFeedback("👉🏻 Next")
      }
    }

    override fun onLeftVerticalScroll(deltaX: Float, deltaY: Float) {
      adjustBrightness(deltaY)
    }

    override fun onRightVerticalScroll(deltaX: Float, deltaY: Float) {
      adjustVolume(deltaY)
    }
  }

  private fun initAudioManager() {
    audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    if (audioManager == null) {
      Log.w(TAG, "AudioManager is not available")
    }

    maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: run {
      Log.w(TAG, "Using default max volume: 15")
      15
    }

    currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
  }

  private fun initBrightness() {
    currentBrightness = fragment.activity?.window?.attributes?.screenBrightness ?: -1f
    if (currentBrightness < 0) {
      currentBrightness = 0.5f
    }
  }

  private fun adjustBrightness(delta: Float) {
    try {
      val window = fragment.activity?.window ?: return

      if (currentBrightness < 0 || currentBrightness > 1.0f) {
        currentBrightness = window.attributes.screenBrightness
        if (currentBrightness < 0) {
          currentBrightness = 0.5f
        }
      }

      //val change = delta * BRIGHTNESS_SENSITIVITY
      val steps = if (delta > 0) {
        0.01f
      } else if (delta < 0) {
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

  private fun adjustVolume(delta: Float) {
    try {
      val manager = audioManager ?: return
      //val steps = (delta * VOLUME_SENSITIVITY).toInt().coerceAtLeast(1)
      val steps = if (delta > 0) {
        1
      } else if (delta < 0) {
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

  private fun showFeedback(text: String, autoDisappear: Boolean = true) {
    val textView = gestureTipsView ?: return
    hideGestureTipJob?.cancel()
    textView.text = text
    textView.visibility = View.VISIBLE
    if (autoDisappear) {
      hideGestureTipJob = uiScope.launch {
        delay(GESTURE_TIPS_DISPLAY_DURATION)
        textView.visibility = View.GONE
      }
    }
  }
}
