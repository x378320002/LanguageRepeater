package com.language.repeater.playcore

import android.annotation.SuppressLint
import kotlin.collections.first
import kotlin.collections.firstOrNull
import kotlin.collections.isNotEmpty
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.edit
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.language.repeater.MyApp
import com.language.repeater.dataStore
import com.language.repeater.sentence.LocalVoiceSentenceDetector
import com.language.repeater.pcm.PCMSegmentLoader
import com.language.repeater.pcm.PcmDataUtil
import com.language.repeater.sentence.Sentence
import com.language.repeater.pcm.WaveformPoint
import com.language.repeater.playvideo.components.SubtitleAutoLoader
import com.language.repeater.playvideo.history.HistoryManager
import com.language.repeater.playvideo.model.CurrentPlayVideoEntity
import com.language.repeater.playvideo.model.toEntity
import com.language.repeater.playvideo.model.toMediaItem
import com.language.repeater.playvideo.playlist.PlaylistManager
import com.language.repeater.pcm.FFmpegUtil
import com.language.repeater.sentence.SentenceStoreUtil
import com.language.repeater.utils.DataStoreKey.KEY_CURRENT_PLAYLIST
import com.language.repeater.utils.DataStoreKey.KEY_IS_REPEATED
import com.language.repeater.utils.SrtParser
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Repository层, 比ViewModel更低一个层级
 * 播放连接管理器 (单例)
 * 职责：
 * 1. 连接 MediaSessionService
 * 2. 管理播放状态流 (isPlaying, position...)
 * 3. 管理媒体附属数据 (PCM, Waveform, Sentences) -> 解析一次，全局复用
 * 4. 提供高级播放控制 (上一句, 下一句, AB复读)
 */
class PlaybackCore(private val context: Context) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  // 私有组件：字幕自动加载器
  private var subtitleAutoLoader: SubtitleAutoLoader? = null

  // null = 未连接/断开, 非null = 已连接
  private val _playerState = MutableStateFlow<Player?>(null)
  val playerState = _playerState.asStateFlow()

  private val _isPlaying = MutableStateFlow(false)
  val isPlaying = _isPlaying.asStateFlow()

  private val _playWhenReady = MutableStateFlow(false)
  val playWhenReady = _playWhenReady.asStateFlow()

  private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
  val currentMediaItem = _currentMediaItem.asStateFlow()

  private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
  val playbackState = _playbackState.asStateFlow()

  private val _currentPosition = MutableStateFlow(0L)
  val currentPosition = _currentPosition.asStateFlow()

  private val _currentPositionSeconds = MutableStateFlow(0f)
  val currentPositionSeconds = _currentPositionSeconds.asStateFlow()

  private val _mediaItemCount = MutableStateFlow(0)
  val mediaItemCount = _mediaItemCount.asStateFlow()

  private val _playlistRefreshEvent = MutableSharedFlow<Unit>(replay = 1)
  val playlistRefreshEvent = _playlistRefreshEvent.asSharedFlow()

  // --- 业务数据 (下沉到单例，保证唯一性) ---
  private val _pcmLoaderStateFlow = MutableStateFlow<PCMSegmentLoader?>(null)
  val pcmLoaderStateFlow = _pcmLoaderStateFlow.asStateFlow()

  private val _allWaveDataFlow = MutableStateFlow<List<WaveformPoint>>(emptyList())
  val allWaveDataFlow = _allWaveDataFlow.asStateFlow()

  private val _sentencesFlow = MutableStateFlow<List<Sentence>>(emptyList())
  val sentencesFlow = _sentencesFlow.asStateFlow()

  // --- 复读控制状态 ---
  private val _repeatable = MutableStateFlow(false)
  val repeatable = _repeatable.asStateFlow()

  //当前播放的AB句子
  private val _curAbSentenceFlow = MutableStateFlow<Sentence?>(null)
  val curAbSentenceFlow = _curAbSentenceFlow.asStateFlow()

  // 播放模式状态流 (默认为不循环)
  private val _playerRepeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
  val playerRepeatMode = _playerRepeatMode.asStateFlow()

  // --- 内部变量 ---
  private var currentId: String = ""
  private var parseJob: Job? = null
  // 新增：用于防抖的保存任务 Job
  private var saveStateJob: Job? = null

  // 【关键变量】用于标记是否正在连接或已连接
  private var mediaController: MediaController? = null
  private var controllerFuture: ListenableFuture<MediaController>? = null

  companion object {
    const val TAG = "wangzixu_PlaybackConnection"

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var instance: PlaybackCore? = null
    fun getInstance(context: Context): PlaybackCore {
      return instance ?: synchronized(this) {
        instance ?: PlaybackCore(context.applicationContext).also { instance = it }
      }
    }
  }

  init {
    // 复读循环核心逻辑
    // 只要 播放中 + 开启复读 + 有目标句子，就检测是否越界
    //combine(
    //  _isPlaying,
    //  _currentPositionSeconds,
    //  _repeatable,
    //  _curAbSentenceFlow
    //) { isPlaying, posSec, isRepeat, abSentence ->
    //  if (isPlaying && isRepeat && abSentence != null) {
    //    if (posSec >= abSentence.end) {
    //      // 越界：跳回开始
    //      seekTo((abSentence.start * 1000).toLong())
    //    }
    //  }
    //  // 非复读模式下，如果有 UI 需要高亮当前句子，可以在这里计算并暴露 flow
    //}.launchIn(scope)

    scope.launch {
      val repeat = context.dataStore.data.map {
        it[KEY_IS_REPEATED]
      }.firstOrNull() ?: false
      Log.i(TAG, "init _repeatable:$repeat")
      _repeatable.value = repeat
    }
  }

  // 连接逻辑，但只为了启动 Service
  fun connect() {
    // 如果已经连接或正在连接，直接返回，防止重复调用
    if (mediaController != null || controllerFuture != null) {
      return
    }

    val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    val future = MediaController.Builder(context, sessionToken).buildAsync()
    controllerFuture = future

    future.addListener({
      try {
        // 我们拿到了 Controller，但我们主要用它来保持连接
        // 真正的控制还是走 setPlayer 进来的那个对象
        mediaController = future.get()
        // Controller 连接成功，说明 Service 已经起来了
        // 接下来等待 Service 调用 setPlayer 把真身传给我们
        Log.d(TAG, "MediaController connected")
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }, MoreExecutors.directExecutor())
  }

  /**
   * 【核心方法】由 PlaybackService 调用
   * 注入真正的 Player 实例
   */
  fun initPlayer(newPlayer: Player?) {
    val oldPlayer = _playerState.value
    if (oldPlayer == newPlayer) return

    // 1. 清理旧的
    oldPlayer?.removeListener(playerListener)
    subtitleAutoLoader?.release()
    subtitleAutoLoader = null

    // 2. 更新引用
    _playerState.value = newPlayer

    // 3. 设置新的
    if (newPlayer != null) {
      newPlayer.addListener(playerListener)

      // 初始化字幕加载器
      subtitleAutoLoader = SubtitleAutoLoader(context, newPlayer, scope)

      // 立即同步一次状态
      syncState()

      // 启动进度轮询
      startProgressTicker()

      // 尝试恢复上次会话 (仅当 Service 空闲时)
      restoreLastSessionIfNeeded()
    } else {
      release()
    }
  }

  private fun release() {
    controllerFuture?.let {
      MediaController.releaseFuture(it)
    }
    mediaController = null
    controllerFuture = null
    progressJob?.cancel()
    progressJob = null
  }

  /**
   * 使用 PlaylistManager 恢复上次会话
   */
  private fun restoreLastSessionIfNeeded() {
    val player = _playerState.value ?: return

    // 如果 Service 已经在运行且有数据（比如后台播放中），则不恢复
    if (player.mediaItemCount > 0) {
      Log.d(TAG, "Service is active. Skip restore.")
      return
    }

    scope.launch(Dispatchers.IO) {
      // 1. 读取列表 (返回 List<VideoEntity>)
      val videoEntities = PlaylistManager.loadLastPlaylist(context)
      if (videoEntities.isNotEmpty()) {
        // 2. 转换为 MediaItem
        val mediaItems = videoEntities.map { it.toMediaItem() }

        // 3. 读取上次播放位置 (VideoEntity -> Index/Pos)
        val playInfo = PlaylistManager.loadCurrentPlayIndex(context)

        val startIndex = playInfo?.index ?: 0
        val startPos = playInfo?.positionMs ?: C.TIME_UNSET

        withContext(Dispatchers.Main) {
          Log.i(TAG, "Restoring playlist: ${mediaItems.size} items, index: $startIndex")
          player.playWhenReady = false // 恢复时不自动播放
          player.setMediaItems(mediaItems, startIndex, startPos)
          player.prepare()
        }
      }
    }
  }

  var progressJob: Job? = null
  private fun startProgressTicker() {
    progressJob?.cancel()
    progressJob = scope.launch {
      while (isActive) {
        if (_playerState.value?.isPlaying == true) {
          updatePosition(true)
        }
        delay(16)
      }
    }
  }

  // --- 核心业务：媒体切换处理 ---
  private fun handleMediaItemTransition(item: MediaItem?) {
    val key = item?.mediaId ?: return
    // 【关键修复】如果 ID 没变，说明是同源切换(比如加字幕)，不要重新解析 PCM
    if (key == currentId && _sentencesFlow.value.isNotEmpty()) return

    currentId = key
    Log.i(TAG, "handleMediaItemTransition ID: $key")

    val entity = item.toEntity(_playerState.value?.currentPosition ?: 0L)
    // 1. 保存历史记录
    scope.launch(Dispatchers.IO) {
      HistoryManager.addHistory(context, entity)
    }

    // 2. 开始解析数据 (取消上一次的解析)
    parseJob?.cancel()
    parseJob = parseUriToPcm(item)
  }

  // --- 核心业务：PCM 与句子解析 ---
  private fun parseUriToPcm(item: MediaItem) = scope.launch(Dispatchers.IO) {
    // 清空旧数据，避免 UI 显示错误的波形
    _pcmLoaderStateFlow.value = null
    _allWaveDataFlow.value = emptyList()
    _sentencesFlow.value = emptyList()
    _curAbSentenceFlow.value = null

    val uri = item.localConfiguration?.uri ?: return@launch
    try {
      Log.i(TAG, "parseUriToPcm Start parsing: $currentId, uri: $uri")
      // 提取 PCM 文件
      val path = FFmpegUtil.extractWavFileByFFmpeg(context, uri, currentId)
      val pcmFile = File(path)
      Log.i(TAG, "parseUriToPcm pcmFile size: ${pcmFile.length()/1048576}MB")
      _pcmLoaderStateFlow.value = PCMSegmentLoader(pcmFile)

      //加载全部波形数据 (并行)
      //launch {
      //  val waveData = PcmDataUtil.readAllPcmToWavePoint(pcmFile, ScreenUtil.getScreenSize().width)
      //  _allWaveDataFlow.value = waveData
      //}

      // 加载句子 (优先缓存)
      val cachedSentences = SentenceStoreUtil.loadData(context, currentId)
      if (!cachedSentences.isNullOrEmpty()) {
        _sentencesFlow.value = cachedSentences
        Log.i(TAG, "parseUriToPcm Loaded cached sentences: ${cachedSentences.size}")
      } else {
        loadSentences(item)
      }

      withContext(Dispatchers.Main) {
        updatePosition()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Parse error", e)
      withContext(Dispatchers.Main) {
        ToastUtil.toast("解析音频数据失败")
      }
    }
  }

  private suspend fun loadSentences(item: MediaItem?, forceUseVad: Boolean = false) = withContext(Dispatchers.IO) {
    if (item == null) return@withContext

    val uri = item.localConfiguration?.uri ?: return@withContext
    val subUri = item.localConfiguration?.subtitleConfigurations?.firstOrNull()?.uri

    val newSentences = if (subUri == null || forceUseVad) {
      val path = FFmpegUtil.extractWavFileByFFmpeg(context, uri, currentId)
      val pcmFile = File(path)
      Log.i(TAG, "loadSentences from pcmFile, size: ${pcmFile.length()/1048576}MB")
      LocalVoiceSentenceDetector().detectSentences(PcmDataUtil.readPcmFile(pcmFile))
    } else {
      Log.i(TAG, "loadSentences from subtitle : $subUri")
      val subList = SrtParser.parse(context, subUri)
      subList.map {
        Sentence(it.startTime.toFloat() / 1000, it.endTime.toFloat() / 1000)
      }
    }
    scope.launch {
      SentenceStoreUtil.saveData(context, currentId, newSentences)
    }
    _sentencesFlow.value = newSentences
    Log.i(TAG, "loadSentences sentences: ${newSentences.size}")
  }

  // 复读控制
  fun toggleRepeat() {
    val repeat = !_repeatable.value
    _repeatable.value = repeat
    scope.launch {
      context.dataStore.edit { prefs ->
        prefs[KEY_IS_REPEATED] = repeat
      }
    }
  }

  fun seekToNextSentence() {
    val list = _sentencesFlow.value
    if (list.isEmpty()) return

    val nextSen = list.firstOrNull {
        it.start > _currentPositionSeconds.value + 0.05f
      } ?: list.firstOrNull()
    if (nextSen != null) {
      seekToSentence(nextSen)
    }
  }

  fun seekToPreviousSentence() {
    val list = _sentencesFlow.value
    if (list.isEmpty()) return

    val nextSen = list.lastOrNull {
      it.end < _currentPositionSeconds.value
    } ?: list.lastOrNull()
    if (nextSen != null) {
      seekToSentence(nextSen)
    }
    //val curSen = _curAbSentenceFlow.value ?: return
    //val index = list.indexOf(curSen)
    //if (index == 0) {
    //  seekToSentence(list.last())
    //} else {
    //  val prevIndex = (index - 1).coerceAtLeast(0)
    //  seekToSentence(list[prevIndex])
    //}
  }

  fun backToSentenceHead() {
    val sentence = _curAbSentenceFlow.value ?: return
    seekTo((sentence.start * 1000).toLong())
  }

  // --- 手动更新字幕 ---
  // 这是 UI 唯一需要调用的“更换字幕”接口
  fun updateSubtitle(subtitleUri: Uri) {
    // 1. 调用 Loader 替换播放器字幕
    val item = subtitleAutoLoader?.updateCurrentItemSubtitle(subtitleUri)
    if (item != null) {
      scope.launch {
        loadSentences(item)
      }
    }
  }

  fun forceLoadCurrentSentences(forceUseVad: Boolean = false) {
    scope.launch {
      loadSentences(_currentMediaItem.value, forceUseVad)
    }
  }

  private fun seekToSentence(sentence: Sentence) {
    if (_repeatable.value) {
      _curAbSentenceFlow.value = sentence
    }
    seekTo((sentence.start * 1000).toLong())
  }

  private fun findSentenceByTime(currentSec: Float): Sentence? {
    // 优化：二分查找或简单遍历
    return _sentencesFlow.value.firstOrNull { currentSec <= it.end }
  }

  // --- 基础播放器同步逻辑 ---
  private val playerListener = object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
      //Log.i(TAG, "onIsPlayingChanged isPlaying: $isPlaying")
      _isPlaying.value = isPlaying
      if (!isPlaying) {
        saveCurrentState()
      }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
      //Log.i(TAG, "onPlayWhenReadyChanged playWhenReady: $playWhenReady")
      _playWhenReady.value = playWhenReady
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
      val preItem = _currentMediaItem.value
      Log.i(TAG, "onMediaItemTransition reason: $reason, pre:${preItem?.mediaId}, mediaItem:${mediaItem?.mediaId}")
      if (mediaItem != preItem) {
        _currentMediaItem.value = mediaItem
        handleMediaItemTransition(mediaItem)
        saveCurrentState()
      }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
      //Log.i(TAG, "onPlaybackStateChanged playbackState: $playbackState")
      /*
      状态常量 (Int)	含义	UI/业务逻辑应该做什么？
        STATE_IDLE (1)	闲置	播放器里没东西，或者出错了。UI 应该禁用控制按钮。
        STATE_BUFFERING (2)	缓冲中	数据不够了，正在加载。UI 应该显示 Loading 转圈。
        STATE_READY (3)	就绪	数据够了，可以随时播放（不论现在是暂停还是播放中）。UI 隐藏 Loading，启用控制按钮。
        STATE_ENDED (4)	结束	播完了。UI 显示重播按钮，或重置进度条。
       */
      _playbackState.value = playbackState
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
      Log.i(TAG, "onTimelineChanged reason: $reason")
      //如果是列表增删改，保存列表
      if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
        _mediaItemCount.value = _playerState.value?.mediaItemCount ?: 0
        scope.launch { _playlistRefreshEvent.emit(Unit) }
        saveCurrentPlaylist()
      }
    }

    override fun onPositionDiscontinuity(
      oldPosition: Player.PositionInfo,
      newPosition: Player.PositionInfo,
      reason: Int,
    ) {
      updatePosition()
    }

    // 监听播放器内部模式变化 (比如用户通过通知栏改了模式)
    override fun onRepeatModeChanged(repeatMode: Int) {
      _playerRepeatMode.value = repeatMode
    }
  }

  private fun syncState() {
    val player = _playerState.value ?: return
    _isPlaying.value = player.isPlaying
    _playWhenReady.value = player.playWhenReady
    _currentMediaItem.value = player.currentMediaItem
    _playbackState.value = player.playbackState
    _mediaItemCount.value = player.mediaItemCount
    _playerRepeatMode.value = player.repeatMode
    updatePosition()
  }

  fun updatePosition(checkAb: Boolean = false, seekPosition: Long? = null) {
    val pos = seekPosition ?: (_playerState.value?.currentPosition ?: 0L)

    _currentPosition.value = pos
    val curSec = pos.toFloat() / 1000f
    _currentPositionSeconds.value = curSec

    val curAbSen = _curAbSentenceFlow.value
    if (checkAb && curAbSen != null && _repeatable.value) {
      if (_playerState.value?.isPlaying == true && curSec >= curAbSen.end) {
        seekTo((curAbSen.start * 1000).toLong())
      }
    } else {
      val curSen = findSentenceByTime(curSec)
      _curAbSentenceFlow.value = curSen
    }
    //Log.i(TAG, "====== updatePosition _currentPositionSeconds:${_currentPositionSeconds.value}, pos:${pos}")
  }

  /**
   * 保存当前播放位置 (Index + Position)
   * 在 IO 线程执行
   */
  private fun saveCurrentState() {
    // 取消上一次未执行的保存任务
    saveStateJob?.cancel()
    // 启动新的保存任务，带延迟
    saveStateJob = scope.launch {
      delay(1000) // 防抖时间 0.5秒

      val p = _playerState.value ?: return@launch
      val index = p.currentMediaItemIndex
      val pos = p.currentPosition

      // 确保索引有效
      if (index != C.INDEX_UNSET) {
        val info = CurrentPlayVideoEntity(index, pos)
        // PlaylistManager 内部会切到 IO 线程
        PlaylistManager.saveCurrentPlayIndex(context, info)
        Log.d(TAG, "Saved state: index=$index, pos=$pos")
      }
    }
  }

  /**
   * 保存当前播放列表
   * 在 IO 线程执行
   */
  private fun saveCurrentPlaylist() {
    val player = _playerState.value ?: return
    // 必须在主线程提取数据
    val items = ArrayList<MediaItem>()
    for (i in 0 until player.mediaItemCount) {
      items.add(player.getMediaItemAt(i))
    }

    scope.launch(Dispatchers.IO) {
      // 使用你现有的 PlaylistManager
      PlaylistManager.saveCurrentPlaylist(context, items)
      Log.d(TAG, "Saved playlist: size=${items.size}")
    }
  }

  /**
   * 分割结果枚举
   */
  enum class SplitResult {
    SUCCESS,
    NO_SENTENCE,        // 没找到当前句子
    TOO_SHORT,          // 句子本身太短
    TOO_CLOSE_TO_EDGE   // 分割点离边缘太近
  }

  /**
   * 分割当前句子 (迁移后的逻辑)
   */
  fun splitCurrentSentence(): SplitResult {
    val currentPos = _currentPositionSeconds.value
    val targetSentence = _curAbSentenceFlow.value

    if (targetSentence == null) {
      return SplitResult.NO_SENTENCE
    }

    // 1. 检查总长度 ( < 1.0s 不分)
    if (targetSentence.end - targetSentence.start < 1.0f) {
      return SplitResult.TOO_SHORT
    }

    // 2. 检查边缘距离 ( < 0.5s 不分)
    val sentenceMinGap = 0.5f
    if (currentPos <= targetSentence.start + sentenceMinGap ||
      currentPos >= targetSentence.end - sentenceMinGap) {
      return SplitResult.TOO_CLOSE_TO_EDGE
    }

    val currentList = _sentencesFlow.value.toMutableList()
    val index = currentList.indexOf(targetSentence)

    if (index != -1) {
      // 3. 执行分割
      // 新的前半段：End = currentPos - 0.1s
      val newEndTime = (currentPos - 0.1f).coerceIn(targetSentence.start, currentPos)
      val newSen = Sentence(targetSentence.start, newEndTime)

      // 修改后半段(原句)：Start = currentPos
      targetSentence.start = currentPos

      // 4. 插入列表
      currentList.add(index, newSen)

      // 5. 更新流和磁盘
      _sentencesFlow.value = currentList

      scope.launch {
        saveSentencesToDisk(currentList)
      }

      return SplitResult.SUCCESS
    }

    return SplitResult.NO_SENTENCE
  }

  fun mergePre() {
    val sen = _curAbSentenceFlow.value
    val sentences = _sentencesFlow.value.toMutableList()
    if (sen == null) {
      ToastUtil.toast("当前没有选中任何句子")
      return
    }

    val index = sentences.indexOf(sen)
    if (index <= 0) {
      ToastUtil.toast("当前没有上一句, 无法合并")
      return
    }

    val nextSen = sentences[index - 1]
    sen.start = minOf(sen.start, nextSen.start)
    sen.end = maxOf(sen.end, nextSen.end)
    _curAbSentenceFlow.value = sen
    sentences.remove(nextSen)
    _sentencesFlow.value = sentences
  }

  fun mergeNext() {
    val sen = _curAbSentenceFlow.value
    val sentences = _sentencesFlow.value.toMutableList()
    if (sen == null) {
      ToastUtil.toast("当前没有选中任何句子")
      return
    }

    val index = sentences.indexOf(sen)
    if (index == -1 || index == sentences.lastIndex) {
      ToastUtil.toast("当前没有下一句, 无法合并")
      return
    }

    val nextSen = sentences[index + 1]
    sen.start = minOf(sen.start, nextSen.start)
    sen.end = maxOf(sen.end, nextSen.end)
    _curAbSentenceFlow.value = sen
    sentences.remove(nextSen)
    _sentencesFlow.value = sentences
  }

  /**
   * 辅助方法：保存句子改动到本地
   */
  suspend fun saveSentencesToDisk(list: List<Sentence> = _sentencesFlow.value) = withContext(Dispatchers.IO) {
    if (currentId.isNotEmpty()) {
      SentenceStoreUtil.saveData(context, currentId, list)
      Log.i(TAG, "Sentences updated and saved to disk.")
    }
  }

  /**
   * 保存并合并重叠的句子
   */
  fun mergeAndSaveSentences() {
    val list = _sentencesFlow.value
    if (list.isEmpty()) return

    scope.launch(Dispatchers.IO) {
      // 1. 按 start 升序排列
      val sorted = list.sortedBy { it.start }
      val merged = ArrayList<Sentence>()

      if (sorted.isNotEmpty()) {
        var current = sorted.first()
        for (i in 1 until sorted.size) {
          val next = sorted[i]
          if (next.start <= current.end) {
            // 有重叠：取较大的 end 值进行合并
            current.end = max(current.end, next.end)
            Log.i(TAG, "Merge overlap sentences at index $i")
            //合并后本质上会删掉后一个sentence, 如果删掉的正好是curAbSen, 需要特殊处理
            if (next == _curAbSentenceFlow.value) {
              _curAbSentenceFlow.value = current
            }
          } else {
            // 无重叠：保存当前区间，移动到下一个
            merged.add(current)
            current = next
          }
        }
        // 最后一个也别忘了加进去
        merged.add(current)
      }

      // 2. 更新内存数据流 (UI 会自动刷新显示新的合并后的列表)
      _sentencesFlow.value = merged

      // 3. 持久化保存
      saveSentencesToDisk(merged)
    }
  }

  fun deleteCurSentence() {
    val currentPos = _currentPositionSeconds.value
    // 逻辑优化：优先取当前复读选中的句子；如果没有，则取当前播放时间点所在的句子
    val sen = _curAbSentenceFlow.value ?: findSentenceByTime(currentPos) ?: return
    val sentences = _sentencesFlow.value.toMutableList()
    val index = sentences.indexOf(sen)
    if (index != -1) {
      if (repeatable.value && _curAbSentenceFlow.value != null) {
        seekToNextSentence()
      }

      if (sentences.size > 1) {
        sentences.remove(sen)
        _sentencesFlow.value = sentences
      } else {
        //如果当前只有这一个句子, 不删除, 把它变成从头到尾
        playerState.value?.also {
          sen.start = 0f
          sen.end = it.duration.toFloat() / 1000
        }
      }

      scope.launch {
        saveSentencesToDisk(sentences)
      }
    }
  }

  fun setPlayerRepeatMode(mode: Int) {
    _playerState.value?.repeatMode = mode
    _playerRepeatMode.value = mode
  }

  // --- 暴露给 UI 的基础操作 ---
  fun play() = _playerState.value?.play()
  fun pause() = _playerState.value?.pause()
  fun seekTo(positionMs: Long) = _playerState.value?.seekTo(positionMs)
  fun seekToDefaultPosition(index: Int) = _playerState.value?.seekToDefaultPosition(index)
  fun removeMediaItem(index: Int) = _playerState.value?.removeMediaItem(index)
  fun hasNextMediaItem() = _playerState.value?.hasNextMediaItem() ?: false
  fun seekToNextMediaItem() = _playerState.value?.seekToNextMediaItem()
  fun hasPreviousMediaItem() = _playerState.value?.hasPreviousMediaItem() ?: false
  fun seekToPreviousMediaItem() = _playerState.value?.seekToPreviousMediaItem()
}