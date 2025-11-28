package com.language.repeater.playvideo.components

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.pcm.Sentence
import com.language.repeater.playvideo.PlayVideoFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class PlayCoreComponent: BaseComponent<PlayVideoFragment>(), Player.Listener {
  companion object {
    const val TAG = PlayVideoFragment.Companion.TAG
  }

  private var repeatable = false
  val player: ExoPlayer by lazy {
    ExoPlayer.Builder(context).build().apply {
      repeatMode = Player.REPEAT_MODE_ALL
    }
  }

  //当前所有的语音片段
  private var sentences = listOf<Sentence>()

  //当前正在复读的语音片段
  val curAbSentenceFlow = MutableStateFlow<Sentence?>(null)

  //当前进度的对外发布
  val curPosSecFlow = MutableStateFlow<Float>(0f)

  private var loopProgressJob: Job? = null

  override fun onCreate() {
    super.onCreate()
    player.addListener(this)

    fragment.viewModel.sentencesFlow.onEach {
      sentences = it
      updateAbSentence()
    }.launchIn(fScope)

    fragment.viewModel.playUriStateFlow.onEach {
      if (it != null) {
        val mediaItem = MediaItem.fromUri(it)
        player.setMediaItem(mediaItem)
        player.seekTo(0)
        player.prepare()
        if (fragment.isResumed) {
          player.play()
        }
      }
    }.launchIn(fScope)
  }

  override fun onDestroy() {
    super.onDestroy()
    player.removeListener(this)
  }

  @UnstableApi
  override fun onCreateView() {
    super.onCreateView()
  }

  fun setRepeat(repeat: Boolean) {
    if (repeatable != repeat) {
      repeatable = repeat
      updateAbSentence()
    }
  }

  fun seekToNext() {
    val curSen = curAbSentenceFlow.value
    if (curSen != null) {
      val index = sentences.indexOf(curSen)
      var target: Sentence? = null
      if (index >= 0 && index < sentences.lastIndex) {
        //找到第一个end比自己end大的句子, 因为有可能用户已经拖动了当前句子, 把旁边的句子都盖住了
        for (i in (index + 1)..sentences.lastIndex) {
          val sentence = sentences[i]
          if (sentence.end > curSen.end) {
            target = sentence
            break
          }
        }
      }
      seekToSegment(target ?: sentences.firstOrNull())
    }
  }

  fun seekToPrevious() {
    val curSen = curAbSentenceFlow.value
    if (curSen != null) {
      val index = sentences.indexOf(curSen)
      var target: Sentence? = null
      if (index > 0) {
        //找到第一个start比自己start小的句子, 因为有可能用户已经拖动了当前句子, 把旁边的句子都盖住了
        for (i in (index - 1)downTo 0) {
          val sentence = sentences[i]
          if (sentence.start < curSen.start) {
            target = sentence
            break
          }
        }
      }
      seekToSegment(target ?: sentences.lastOrNull())
    }
  }

  fun seekToSegment(sentence: Sentence?) {
    curAbSentenceFlow.value = sentence
    if (sentence != null) {
      player.seekTo((sentence.start * 1000).toLong())
    }
  }

  override fun onIsPlayingChanged(isPlaying: Boolean) {
    super.onIsPlayingChanged(isPlaying)
    //Log.i(TAG, "$TAG onIsPlayingChanged $isPlaying")
    if (isPlaying) {
      startLoopPos()
    } else {
      loopProgressJob?.cancel()
      loopProgressJob = null
    }
  }

  private fun startLoopPos() {
    loopProgressJob?.cancel()
    loopProgressJob = fScope.launch {
      while (true) {
        if (player.isPlaying) {
          val curPosMs = player.currentPosition.toFloat()
          var curSec = curPosMs / 1000

          //处理复读的逻辑
          if (repeatable) {
            if (curAbSentenceFlow.value == null) {
              updateAbSentence(curSec)
            }
            val seg = curAbSentenceFlow.value
            if (seg != null && curSec >= seg.end) {
              //跳回开始
              player.seekTo((seg.start * 1000).toLong())
              curSec = seg.start
            }
          }

          //更新进度flow
          curPosSecFlow.value = curSec
        }
        delay(16)
      }
    }
  }

  override fun onPositionDiscontinuity(
    oldPosition: Player.PositionInfo,
    newPosition: Player.PositionInfo,
    reason: Int
  ) {
    super.onPositionDiscontinuity(oldPosition, newPosition, reason)
    curPosSecFlow.value = newPosition.positionMs.toFloat() / 1000
  }

  fun updateAbSentence(specificTime: Float? = null) {
    if (repeatable && sentences.isNotEmpty()) {
      var targetSentence: Sentence = sentences.first()
      val timeSec = specificTime ?: (player.currentPosition.toFloat() / 1000)
      for (sen in sentences) {
        if (timeSec <= sen.end) {
          targetSentence = sen
          break
        }
      }
      curAbSentenceFlow.value = targetSentence
    } else {
      curAbSentenceFlow.value = null
    }
  }

  /**
   * 获取当前播放位置对应的句子, 不是用来做复读,
   * 当前正在复读的句子用 curAbSentenceFlow获取
   */
  fun findCurSentence(): Sentence? {
    var targetSentence: Sentence? = null
    if (sentences.isNotEmpty()) {
      val timeSec = (player.currentPosition.toFloat() / 1000)
      for (sen in sentences) {
        if (timeSec > sen.start && timeSec < sen.end) {
          targetSentence = sen
          break
        }
      }
    }
    return targetSentence
  }
}