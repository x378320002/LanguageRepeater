package com.language.repeater.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.language.repeater.R
import com.language.repeater.datastore.DsManager
import com.language.repeater.datastore.subtitleStore
import com.language.repeater.db.HistoryDao
import com.language.repeater.db.VideoInfoDao
import com.language.repeater.pcm.FFmpegUtil
import com.language.repeater.pcm.PCMSegmentLoader
import com.language.repeater.pcm.WaveformPoint
import com.language.repeater.player.PlaybackMedia
import com.language.repeater.player.PlaybackPlayer
import com.language.repeater.sentence.LocalVoiceSentenceDetector
import com.language.repeater.sentence.Sentence
import com.language.repeater.sentence.SentenceStoreUtil
import com.language.repeater.utils.SrtParser
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 当前媒体的分析与句子播放协调器。
 *
 * 该对象只依赖业务播放器接口，不持有 Media3 Player。它负责 PCM、句子、AB 复读、
 * 句子编辑、字幕持久化和历史记录，并以 mediaId 隔离媒体切换期间的异步结果。
 */
class CurrentMediaSentenceController(
  context: Context,
  private val player: PlaybackPlayer,
  private val historyDao: HistoryDao,
  private val videoInfoDao: VideoInfoDao,
  private val scope: CoroutineScope
) {
  private val applicationContext = context.applicationContext

  private val _pcmLoader = MutableStateFlow<PCMSegmentLoader?>(null)
  val pcmLoader = _pcmLoader.asStateFlow()

  private val _allWaveData = MutableStateFlow<List<WaveformPoint>>(emptyList())
  val allWaveData = _allWaveData.asStateFlow()

  private val _sentences = MutableStateFlow<List<Sentence>>(emptyList())
  val sentences = _sentences.asStateFlow()

  private val _repeatAb = MutableStateFlow(false)
  val repeatAb = _repeatAb.asStateFlow()

  private val _currentSentence = MutableStateFlow<Sentence?>(null)
  val currentSentence = _currentSentence.asStateFlow()
  val history = historyDao.getAllHistory()

  private var activeMediaId: String? = null
  private var analysisJob: Job? = null
  private var sentenceJob: Job? = null

  init {
    scope.launch(Dispatchers.IO) {
      _repeatAb.value = DsManager.get(DsManager.KEY_AB_REPEATED, false)
    }

    scope.launch {
      var previousMediaId: String? = null
      player.state.collectLatest { state ->
        val media = state.currentMedia
        val mediaId = media?.mediaId
        if (mediaId != previousMediaId) {
          previousMediaId = mediaId
          onMediaChanged(media)
        }
        updatePosition(state.positionMs, checkRepeat = true)
      }
    }
  }

  private fun onMediaChanged(media: PlaybackMedia?) {
    analysisJob?.cancel()
    sentenceJob?.cancel()
    activeMediaId = media?.mediaId
    clearAnalysis()
    if (media == null) {
      return
    }

    scope.launch(Dispatchers.IO) {
      saveHistory(media)
    }
    analysisJob = scope.launch(Dispatchers.IO) {
      analyzeMedia(media)
    }
  }

  private fun clearAnalysis() {
    _pcmLoader.value = null
    _allWaveData.value = emptyList()
    _sentences.value = emptyList()
    _currentSentence.value = null
  }

  private suspend fun analyzeMedia(media: PlaybackMedia) {
    val mediaId = media.mediaId
    try {
      val pcmFile = extractPcm(media)
      if (!isCurrent(mediaId)) {
        return
      }
      _pcmLoader.value = PCMSegmentLoader(pcmFile)

      val cachedSentences = SentenceStoreUtil.loadData(applicationContext, mediaId)
      if (!cachedSentences.isNullOrEmpty()) {
        publishSentences(mediaId, cachedSentences)
      } else {
        loadSentences(media, forceUseVad = false)
      }
    } catch (throwable: Throwable) {
      if (throwable is CancellationException) {
        throw throwable
      }
      if (!isCurrent(mediaId)) {
        return
      }
      Log.e(TAG, "Failed to analyze media: $mediaId", throwable)
      withContext(Dispatchers.Main) {
        ToastUtil.toast("解析音频数据失败")
      }
    }
  }

  private suspend fun extractPcm(media: PlaybackMedia): File {
    val path = FFmpegUtil.extractWavFileByFFmpeg(
      context = applicationContext,
      input = Uri.parse(media.uri),
      key = media.mediaId
    )
    return File(path)
  }

  private suspend fun loadSentences(
    media: PlaybackMedia,
    forceUseVad: Boolean
  ) {
    val mediaId = media.mediaId
    val newSentences = if (media.subtitleUri.isNullOrEmpty() || forceUseVad) {
      val pcmFile = extractPcm(media)
      if (!isCurrent(mediaId)) {
        return
      }
      LocalVoiceSentenceDetector().detectSentences(pcmFile)
    } else {
      val parsedSubtitles = SrtParser.parse(applicationContext, Uri.parse(media.subtitleUri))
      if (!isCurrent(mediaId)) {
        return
      }
      parsedSubtitles.map { subtitle ->
        Sentence(
          start = subtitle.startTime.toFloat() / 1000f,
          end = subtitle.endTime.toFloat() / 1000f
        )
      }
    }

    if (!isCurrent(mediaId)) {
      return
    }
    publishSentences(mediaId, newSentences)
    SentenceStoreUtil.saveData(applicationContext, mediaId, newSentences)
  }

  private fun publishSentences(mediaId: String, newSentences: List<Sentence>) {
    if (!isCurrent(mediaId)) {
      return
    }
    _sentences.value = newSentences
    updatePosition(player.state.value.positionMs, checkRepeat = false)
  }

  private suspend fun saveHistory(media: PlaybackMedia) {
    historyDao.saveHistory(
      com.language.repeater.playvideo.model.VideoEntity(
        id = media.mediaId,
        uri = media.uri,
        name = media.title,
        subUri = media.subtitleUri,
        position = player.state.value.positionMs
      )
    )
  }

  fun deleteHistory(mediaId: String) {
    scope.launch(Dispatchers.IO) {
      historyDao.deleteByVideoId(mediaId)
    }
  }

  fun toggleRepeat() {
    val enabled = !_repeatAb.value
    _repeatAb.value = enabled
    scope.launch(Dispatchers.IO) {
      DsManager.set(DsManager.KEY_AB_REPEATED, enabled)
    }
  }

  fun seekToNextSentence() {
    val list = _sentences.value
    if (list.isEmpty()) {
      return
    }
    val current = _currentSentence.value
    val currentSeconds = player.state.value.positionMs / 1000f
    val target = if (current == null) {
      sentenceAround(currentSeconds, list)
    } else if (currentSeconds < current.start) {
      current
    } else {
      val index = list.indexOf(current)
      list.getOrNull(index + 1) ?: list.last()
    }
    seekToSentence(target)
  }

  fun seekToPreviousSentence() {
    val list = _sentences.value
    if (list.isEmpty()) {
      return
    }
    val current = _currentSentence.value
    val currentSeconds = player.state.value.positionMs / 1000f
    val target = if (current == null) {
      sentenceAround(currentSeconds, list)
    } else {
      val index = list.indexOf(current)
      if (index <= 0) {
        current
      } else {
        list[index - 1]
      }
    }
    seekToSentence(target)
  }

  private fun sentenceAround(positionSeconds: Float, list: List<Sentence>): Sentence {
    val first = list.first()
    val last = list.last()
    return when {
      positionSeconds < first.start -> first
      positionSeconds >= last.end -> last
      else -> findSentenceByTime(positionSeconds) ?: last
    }
  }

  fun backToSentenceHead() {
    val sentence = _currentSentence.value ?: return
    player.seekTo((sentence.start * 1000f).toLong())
  }

  fun previewPosition(positionMs: Long) {
    updatePosition(positionMs, checkRepeat = false)
  }

  private fun updatePosition(positionMs: Long, checkRepeat: Boolean) {
    val currentSeconds = positionMs.coerceAtLeast(0L) / 1000f
    val current = _currentSentence.value
    if (checkRepeat && _repeatAb.value && current != null && player.state.value.isPlaying) {
      if (currentSeconds >= current.end) {
        player.seekTo((current.start * 1000f).toLong())
        return
      }
    }

    if (current == null || currentSeconds < current.start || currentSeconds > current.end) {
      _currentSentence.value = findSentenceByTime(currentSeconds)
    }
  }

  private fun findSentenceByTime(positionSeconds: Float): Sentence? {
    return _sentences.value.firstOrNull { sentence ->
      positionSeconds >= sentence.start && positionSeconds < sentence.end
    }
  }

  private fun seekToSentence(sentence: Sentence) {
    _currentSentence.value = sentence
    player.seekTo((sentence.start * 1000f).toLong())
  }

  fun reloadSentences(forceUseVad: Boolean = false) {
    val media = player.state.value.currentMedia ?: return
    sentenceJob?.cancel()
    sentenceJob = scope.launch(Dispatchers.IO) {
      try {
        loadSentences(media, forceUseVad)
      } catch (throwable: Throwable) {
        if (throwable is CancellationException) {
          throw throwable
        }
        if (!isCurrent(media.mediaId)) {
          return@launch
        }
        Log.e(TAG, "Failed to reload sentences: ${media.mediaId}", throwable)
        withContext(Dispatchers.Main) {
          ToastUtil.toast("加载句子失败")
        }
      }
    }
  }

  fun updateSubtitle(subtitleUri: Uri) {
    val media = player.state.value.currentMedia ?: return
    val mediaId = media.mediaId
    val subtitle = subtitleUri.toString()
    sentenceJob?.cancel()
    sentenceJob = scope.launch(Dispatchers.IO) {
      try {
        val preferenceKey = stringPreferencesKey(mediaId)
        applicationContext.subtitleStore.edit { preferences ->
          preferences[preferenceKey] = subtitle
        }
        videoInfoDao.updateSubUri(mediaId, subtitle)
        if (!isCurrent(mediaId)) {
          return@launch
        }

        withContext(Dispatchers.Main) {
          player.setSubtitle(subtitle)
        }
        loadSentences(media.copy(subtitleUri = subtitle), forceUseVad = false)
      } catch (throwable: Throwable) {
        if (throwable is CancellationException) {
          throw throwable
        }
        if (!isCurrent(mediaId)) {
          return@launch
        }
        Log.e(TAG, "Failed to update subtitle: $mediaId", throwable)
        withContext(Dispatchers.Main) {
          ToastUtil.toast("加载字幕失败")
        }
      }
    }
  }

  fun splitCurrentSentence() {
    val positionSeconds = player.state.value.positionMs / 1000f
    val sentence = _currentSentence.value
    if (sentence == null || sentence.start > positionSeconds || sentence.end < positionSeconds) {
      ToastUtil.toast(R.string.current_no_sentence)
      return
    }
    if (sentence.end - sentence.start < 0.1f) {
      ToastUtil.toast("当前句子太短，无法继续分割")
      return
    }

    val minimumEdgeGap = 0.5f
    if (positionSeconds <= sentence.start + minimumEdgeGap ||
      positionSeconds >= sentence.end - minimumEdgeGap
    ) {
      ToastUtil.toast("距离句子边缘太近，无法分割")
      return
    }

    val updated = _sentences.value.toMutableList()
    val index = updated.indexOf(sentence)
    if (index < 0) {
      return
    }
    val firstPart = sentence.copy(end = positionSeconds + 0.05f)
    val secondPart = Sentence(positionSeconds + 0.25f, sentence.end)
    updated[index] = firstPart
    updated.add(index + 1, secondPart)
    _sentences.value = updated
    _currentSentence.value = firstPart
    saveSentences(updated)
    ToastUtil.toast("分割成功")
  }

  fun mergePreviousSentence() {
    val sentence = _currentSentence.value
    if (sentence == null) {
      ToastUtil.toast(R.string.current_no_sentence)
      return
    }
    val updated = _sentences.value.toMutableList()
    val index = updated.indexOf(sentence)
    if (index <= 0) {
      ToastUtil.toast("当前没有上一句, 无法合并")
      return
    }

    val previous = updated[index - 1]
    val merged = Sentence(
      start = minOf(sentence.start, previous.start),
      end = maxOf(sentence.end, previous.end)
    )
    updated[index] = merged
    updated.removeAt(index - 1)
    _sentences.value = updated
    _currentSentence.value = merged
    saveSentences(updated)
  }

  fun mergeNextSentence() {
    val sentence = _currentSentence.value
    if (sentence == null) {
      ToastUtil.toast(R.string.current_no_sentence)
      return
    }
    val updated = _sentences.value.toMutableList()
    val index = updated.indexOf(sentence)
    if (index < 0 || index == updated.lastIndex) {
      ToastUtil.toast("当前没有下一句, 无法合并")
      return
    }

    val next = updated[index + 1]
    var mergedEnd = maxOf(sentence.end, next.end)
    val durationSeconds = player.state.value.durationMs / 1000f
    if (durationSeconds > 0f) {
      mergedEnd = mergedEnd.coerceAtMost(durationSeconds)
    }
    val merged = Sentence(
      start = minOf(sentence.start, next.start),
      end = mergedEnd
    )
    updated[index] = merged
    updated.removeAt(index + 1)
    _sentences.value = updated
    _currentSentence.value = merged
    saveSentences(updated)
  }

  fun insertSentence() {
    val durationSeconds = player.state.value.durationMs / 1000f
    if (durationSeconds < 1f) {
      return
    }
    val positionSeconds = player.state.value.positionMs / 1000f
    val current = _currentSentence.value
    val updated = _sentences.value.toMutableList()
    val start = (positionSeconds - 0.5f).coerceAtLeast(0f)
    val end = (positionSeconds + 0.5f).coerceAtMost(durationSeconds)
    val inserted = Sentence(start, end)

    if (current == null || current.end <= positionSeconds || current.start >= positionSeconds) {
      val insertionIndex = updated.indexOfFirst { sentence -> sentence.start > positionSeconds }
        .let { index -> if (index < 0) updated.size else index }
      updated.add(insertionIndex, inserted)
    } else {
      val index = updated.indexOf(current)
      if (index < 0) {
        return
      }
      val leading = current.copy(end = inserted.start)
      val trailing = Sentence(inserted.end, current.end)
      updated[index] = leading
      updated.add(index + 1, inserted)
      if (trailing.start < trailing.end - 0.1f) {
        updated.add(index + 2, trailing)
      }
      if (leading.start >= leading.end - 0.1f) {
        updated.remove(leading)
      }
    }

    _sentences.value = updated
    _currentSentence.value = inserted
    player.seekTo((inserted.start * 1000f).toLong())
    saveSentences(updated)
  }

  fun deleteCurrentSentence() {
    val current = _currentSentence.value
    if (current == null) {
      ToastUtil.toast(R.string.current_no_sentence)
      return
    }
    val updated = _sentences.value.toMutableList()
    if (updated.size > 1) {
      updated.remove(current)
    } else {
      val reset = Sentence(
        start = 0f,
        end = player.state.value.durationMs / 1000f
      )
      updated[0] = reset
    }
    _sentences.value = updated
    _currentSentence.value = findSentenceByTime(player.state.value.positionMs / 1000f)
    saveSentences(updated)
  }

  fun mergeAndSaveSentences() {
    val source = _sentences.value
    if (source.isEmpty()) {
      return
    }
    val sorted = source.sortedBy { sentence -> sentence.start }
    val merged = ArrayList<Sentence>()
    var current = sorted.first()
    for (index in 1 until sorted.size) {
      val next = sorted[index]
      if (next.end <= current.end) {
        if (next == _currentSentence.value) {
          _currentSentence.value = current
        }
      } else {
        merged.add(current)
        current = next
      }
    }
    merged.add(current)
    _sentences.value = merged
    saveSentences(merged)
  }

  private fun saveSentences(sentences: List<Sentence>) {
    val mediaId = activeMediaId ?: return
    scope.launch(Dispatchers.IO) {
      if (isCurrent(mediaId)) {
        SentenceStoreUtil.saveData(applicationContext, mediaId, sentences)
      }
    }
  }

  private fun isCurrent(mediaId: String): Boolean {
    return activeMediaId == mediaId && player.state.value.currentMedia?.mediaId == mediaId
  }

  private companion object {
    const val TAG = "CurrentMediaAnalysis"
  }
}
