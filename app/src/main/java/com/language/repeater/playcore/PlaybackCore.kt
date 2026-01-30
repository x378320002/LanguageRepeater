package com.language.repeater.playcore

import android.annotation.SuppressLint
import kotlin.collections.first
import kotlin.collections.firstOrNull
import kotlin.collections.isNotEmpty
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.language.repeater.R
import com.language.repeater.dataStore
import com.language.repeater.db.historyDao
import com.language.repeater.sentence.LocalVoiceSentenceDetector
import com.language.repeater.pcm.PCMSegmentLoader
import com.language.repeater.pcm.PcmDataUtil
import com.language.repeater.sentence.Sentence
import com.language.repeater.pcm.WaveformPoint
import com.language.repeater.playvideo.components.SubtitleAutoLoader
import com.language.repeater.playvideo.model.CurrentPlayVideoEntity
import com.language.repeater.playvideo.model.toEntity
import com.language.repeater.playvideo.model.toMediaItem
import com.language.repeater.playvideo.playlist.PlaylistManager
import com.language.repeater.pcm.FFmpegUtil
import com.language.repeater.playvideo.model.VideoEntity
import com.language.repeater.playvideo.model.isPlaceHold
import com.language.repeater.playvideo.model.toPlaceHold
import com.language.repeater.sentence.SentenceStoreUtil
import com.language.repeater.utils.DataStoreKey
import com.language.repeater.utils.DataStoreKey.KEY_AB_REPEATED
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.collections.HashSet

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

  private val _playSpeed = MutableStateFlow(1.0f)
  val playSpeed = _playSpeed.asStateFlow()

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
        it[KEY_AB_REPEATED]
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
      scope.launch(Dispatchers.IO) {
        val cycle = DataStoreKey.observeRepeatMode().first()
        withContext(Dispatchers.Main) {
          newPlayer.repeatMode = cycle
        }
      }

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
      // 3. 读取上次播放位置 (VideoEntity -> Index/Pos)
      val playInfo = PlaylistManager.loadCurrentPlayIndex(context)
      val startIndex = playInfo?.index ?: 0
      val startPos = playInfo?.positionMs ?: C.TIME_UNSET

      withContext(Dispatchers.Main) {
        addPlayList(
          list = videoEntities,
          isReplace = true,
          playWhenReady = false,
          index = startIndex,
          position = startPos
        )
      }
    }
  }

  var progressJob: Job? = null
  private fun startProgressTicker() {
    progressJob?.cancel()
    progressJob = scope.launch {
      while (isActive) {
        if (_playerState.value?.isPlaying == true) {
          updatePosition()
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
    val currentId = item.mediaId
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
        scope.launch(Dispatchers.Main) {
          updatePosition(false)
        }
        Log.i(TAG, "parseUriToPcm Loaded cached sentences: ${cachedSentences.size}")
      } else {
        loadSentences(item)
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
    val currentId = item.mediaId

    val newSentences = if (subUri == null || forceUseVad) {
      val path = FFmpegUtil.extractWavFileByFFmpeg(context, uri, currentId)
      val pcmFile = File(path)
      Log.i(TAG, "loadSentences from pcmFile, size: ${pcmFile.length()/1048576}MB")
      //LocalVoiceSentenceDetector().detectSentences(PcmDataUtil.readPcmFile(pcmFile))
      LocalVoiceSentenceDetector().detectSentences(pcmFile)
    } else {
      Log.i(TAG, "loadSentences from subtitle : $subUri")
      val subList = SrtParser.parse(context, subUri)
      subList.map {
        Sentence(it.startTime.toFloat() / 1000, it.endTime.toFloat() / 1000)
      }
    }

    _sentencesFlow.value = newSentences
    saveSentencesToDisk(newSentences)
    scope.launch(Dispatchers.Main) {
      updatePosition(false)
    }
    Log.i(TAG, "loadSentences sentences: ${newSentences.size}")
  }

  // 复读控制
  fun toggleRepeat() {
    val repeat = !_repeatable.value
    _repeatable.value = repeat
    scope.launch {
      context.dataStore.edit { prefs ->
        prefs[KEY_AB_REPEATED] = repeat
      }
    }
  }

  fun seekToNextSentence() {
    val list = _sentencesFlow.value
    if (list.isEmpty()) return

    //val nextSen = list.firstOrNull {
    //    it.start > _currentPositionSeconds.value + 0.05f
    //  } ?: list.firstOrNull()

    val curSen = _curAbSentenceFlow.value ?: return
    val index = list.indexOf(curSen)
    val nextSen = if (index >= list.size) {
      list.firstOrNull()
    } else {
      list[index + 1]
    }
    if (nextSen != null) {
      seekToSentence(nextSen)
    }
  }

  fun seekToPreviousSentence() {
    val list = _sentencesFlow.value
    if (list.isEmpty()) return

    //val nextSen = list.lastOrNull {
    //  it.end < _currentPositionSeconds.value
    //} ?: list.lastOrNull()

    val curSen = _curAbSentenceFlow.value ?: return
    val index = list.indexOf(curSen)
    val nextSen = if (index <= 0) {
      list.lastOrNull()
    } else {
      list[index - 1]
    }
    if (nextSen != null) {
      seekToSentence(nextSen)
    }
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

  private fun seekToSentence(sentence: Sentence?) {
    _curAbSentenceFlow.value = sentence
    if (sentence != null) {
      seekTo((sentence.start * 1000).toLong())
    }
  }

  private fun findSentenceByTime(currentSec: Float): Sentence? {
    // 优化：二分查找或简单遍历
    return _sentencesFlow.value.firstOrNull { currentSec < it.end }
  }

  // --- 基础播放器同步逻辑 ---
  private val playerListener = object : Player.Listener {
    override fun onPlayerError(error: PlaybackException) {
      super.onPlayerError(error)
      Log.i(TAG, "onPlayerError reason: ${error.message}")
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
      super.onPlaybackParametersChanged(playbackParameters)
      _playSpeed.value = playbackParameters.speed
    }

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
      if (mediaItem?.isPlaceHold() == true) {
        _playerState.value?.seekTo(0, C.TIME_UNSET)
      } else {
        val preItem = _currentMediaItem.value
        Log.i(TAG, "onMediaItemTransition reason: $reason, pre:${preItem?.mediaId}, mediaItem:${mediaItem?.mediaId}")
        if (mediaItem != preItem) {
          _currentMediaItem.value = mediaItem
          handleMediaItemTransition(mediaItem)

          // 保存历史记录
          mediaItem?.toEntity()?.also {
            scope.launch(Dispatchers.IO) {
              //HistoryManager.addHistory(context, entity)
              context.historyDao.saveHistory(it)
            }
          }

          saveCurrentState()
        }
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
        val player = _playerState.value ?: return
        var count = player.mediaItemCount
        if (count > 0 && player.getMediaItemAt(count - 1).isPlaceHold()) {
          count--
        }
        _mediaItemCount.value = count
        scope.launch { _playlistRefreshEvent.emit(Unit) }
        saveCurrentPlaylist()
        saveCurrentState()
      }
    }

    override fun onPositionDiscontinuity(
      oldPosition: Player.PositionInfo,
      newPosition: Player.PositionInfo,
      reason: Int,
    ) {
      updatePosition()
      saveCurrentState()
    }

    // 监听播放器内部模式变化 (比如用户通过通知栏改了模式)
    override fun onRepeatModeChanged(repeatMode: Int) {
      //_playerRepeatMode.value = repeatMode
    }
  }

  private fun syncState() {
    val player = _playerState.value ?: return
    _isPlaying.value = player.isPlaying
    _playWhenReady.value = player.playWhenReady
    _currentMediaItem.value = player.currentMediaItem
    _playbackState.value = player.playbackState
    _mediaItemCount.value = player.mediaItemCount
    //_playerRepeatMode.value = player.repeatMode
    updatePosition()
  }

  fun updatePosition(checkRepeat: Boolean = true, seekPosition: Long? = null) {
    val pos = seekPosition ?: (_playerState.value?.currentPosition ?: 0L)

    _currentPosition.value = pos
    val curSec = pos.toFloat() / 1000f
    _currentPositionSeconds.value = curSec

    val curAbSen = _curAbSentenceFlow.value
    if (checkRepeat && curAbSen != null && _repeatable.value) {
      if (_playerState.value?.isPlaying == true && curSec >= curAbSen.end) {
        seekTo((curAbSen.start * 1000).toLong())
      }
    } else {
      val curSen = _curAbSentenceFlow.value
      if (curSen == null || curSec < curSen.start || curSec > curSen.end) {
        _curAbSentenceFlow.value = findSentenceByTime(curSec)
      }
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
   * 分割当前句子 (迁移后的逻辑)
   *     // 根据返回的枚举做具体提示
   *     when (result) {
   *       PlaybackCore.SplitResult.SUCCESS -> {
   *         ToastUtil.toast("分割成功")
   *       }
   *       PlaybackCore.SplitResult.NO_SENTENCE -> {
   *         ToastUtil.toast("当前位置没有可分割的句子")
   *       }
   *       PlaybackCore.SplitResult.TOO_SHORT -> {
   *         ToastUtil.toast("当前句子太短，无法继续分割")
   *       }
   *       PlaybackCore.SplitResult.TOO_CLOSE_TO_EDGE -> {
   *         ToastUtil.toast("距离句子边缘太近，无法分割")
   *       }
   *     }
   */
  fun splitCurrentSentence() {
    val currentPos = _currentPositionSeconds.value
    val sen = _curAbSentenceFlow.value
    if (sen == null || sen.start > currentPos || sen.end < currentPos) {
      ToastUtil.toast(R.string.current_no_sentence)
      return
    }

    // 1. 检查总长度 ( < 1.0s 不分)
    if (sen.end - sen.start < 0.1f) {
      ToastUtil.toast("当前句子太短，无法继续分割")
      return
    }

    // 2. 检查边缘距离 ( < 0.5s 不分)
    val sentenceMinGap = 0.5f
    if (currentPos <= sen.start + sentenceMinGap ||
      currentPos >= sen.end - sentenceMinGap) {
      ToastUtil.toast("距离句子边缘太近，无法分割")
      return
    }

    val currentList = _sentencesFlow.value.toMutableList()
    val index = currentList.indexOf(sen)

    if (index != -1) {
      // 3. 执行分割
      val newSen = Sentence(currentPos + 0.25f, sen.end)

      // 修改后半段(原句)：Start = currentPos
      sen.end = currentPos + 0.05f

      // 4. 插入列表
      currentList.add(index + 1, newSen)

      // 5. 更新流和磁盘
      _sentencesFlow.value = currentList

      saveSentencesToDisk(currentList)
      ToastUtil.toast("分割成功")
    }
  }

  fun mergePre() {
    val sen = _curAbSentenceFlow.value
    if (sen == null) {
      ToastUtil.toast(R.string.current_no_sentence)
      return
    }

    val sentences = _sentencesFlow.value.toMutableList()
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

    saveSentencesToDisk(sentences)
  }

  fun mergeNext() {
    val sen = _curAbSentenceFlow.value
    val sentences = _sentencesFlow.value.toMutableList()
    if (sen == null) {
      ToastUtil.toast(R.string.current_no_sentence)
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

    saveSentencesToDisk(sentences)
  }

  //插入句子
  fun insertSentence() {
    val player = _playerState.value ?: return
    val curPos = _currentPositionSeconds.value
    val maxPos = player.duration / 1000f
    if (maxPos < 1.0f) return
    val curSen = _curAbSentenceFlow.value
    val sentences = _sentencesFlow.value.toMutableList()

    if (curSen == null || curSen.end <= curPos || curSen.start >= curPos) {
      //当前位置在句子外
      val newSentence = Sentence(curPos - 0.5f, curPos + 0.5f)
      var index = sentences.indexOf(curSen)
      if (index == -1) index = 0
      sentences.add(index, newSentence)
      _sentencesFlow.value = sentences
      _curAbSentenceFlow.value = newSentence

      saveSentencesToDisk(sentences)
    } else {
      //当前位置在句子内, 这是插入句子, 会把当前句子一分为三
      //分离后的中间的句子
      val newSentence = Sentence(curPos - 0.5f, curPos + 0.5f)
      val index = sentences.indexOf(curSen)
      sentences.add(index + 1, newSentence)

      //分离后的后面的句子
      val newSenNext = Sentence(newSentence.end, curSen.end)
      if (newSenNext.start < newSenNext.end - 0.1f) {
        //插入分离后的后半部分个句子
        sentences.add(index + 2, newSenNext)
      }

      //分离后的开头的句子
      curSen.end = newSentence.start
      if (curSen.start >= curSen.end - 0.1f) {
        //这个句子没意义了, 直接删除
        sentences.remove(curSen)
      }

      _sentencesFlow.value = sentences
      _curAbSentenceFlow.value = newSentence

      saveSentencesToDisk(sentences)
    }
  }

  /**
   * 辅助方法：保存句子改动到本地
   */
  fun saveSentencesToDisk(list: List<Sentence> = _sentencesFlow.value) = scope.launch(Dispatchers.IO) {
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
      var current = sorted.first()
      for (i in 1 until sorted.size) {
        val next = sorted[i]
        if (next.end <= current.end) {
          // 完全重叠了
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

      // 2. 更新内存数据流 (UI 会自动刷新显示新的合并后的列表)
      _sentencesFlow.value = merged

      // 3. 持久化保存
      saveSentencesToDisk(merged)
    }
  }

  fun deleteCurSentence() {
    val currentPos = _currentPositionSeconds.value
    val curSen = _curAbSentenceFlow.value
    if (curSen == null) {
      ToastUtil.toast(R.string.current_no_sentence)
      return
    }

    val sentences = _sentencesFlow.value.toMutableList()
    val index = sentences.indexOf(curSen)
    if (index != -1) {
      //if (repeatable.value && _curAbSentenceFlow.value != null) {
      //  seekToNextSentence()
      //}

      if (sentences.size > 1) {
        sentences.remove(curSen)
        _sentencesFlow.value = sentences
      } else {
        //如果当前只有这一个句子, 不删除, 把它变成从头到尾
        playerState.value?.also {
          curSen.start = 0f
          curSen.end = it.duration.toFloat() / 1000
        }
      }
      seekToSentence(findSentenceByTime(currentPos))

      saveSentencesToDisk(sentences)
    }
  }

  fun setPlayerRepeatMode(mode: Int) {
    _playerState.value?.repeatMode = mode
    scope.launch {
      DataStoreKey.saveRepeatMode(mode)
    }
  }

  // --- 暴露给 UI 的基础操作 ---
  fun play() = _playerState.value?.play()
  fun pause() = _playerState.value?.pause()
  fun seekTo(positionMs: Long) = _playerState.value?.seekTo(positionMs)
  fun seekToItem(index: Int) = _playerState.value?.seekTo(index, C.TIME_UNSET)
  fun removeMediaItem(index: Int) {
    val player = _playerState.value ?: return
    val count = player.mediaItemCount
    if (count == 2 && player.getMediaItemAt(1).isPlaceHold()) {
      player.clearMediaItems()
    } else {
      player.removeMediaItem(index)
    }
  }

  // 播放历史记录中的某一项
  fun addAndPlay(item: VideoEntity) {
    val player = _playerState.value?: return
    // 1. 如果就是当前播放的，直接返回
    if (item.id == currentId) {
      return
    }

    // 2. 检查播放列表中是否已存在该视频
    var existingIndex = -1
    for (i in 0 until player.mediaItemCount) {
      if (player.getMediaItemAt(i).mediaId == item.id) {
        existingIndex = i
        break
      }
    }

    if (existingIndex != -1) {
      // 3. 如果存在，直接切过去
      player.seekTo(existingIndex, C.TIME_UNSET)
      player.play()
    } else {
      // 4. 如果不存在，添加到当前播放位置，并播放
      val mediaItem = item.toMediaItem()
      val index = if (player.currentMediaItemIndex == C.INDEX_UNSET) {
        0
      } else {
        player.currentMediaItemIndex
      }
      player.addMediaItem(index, mediaItem)
      player.seekTo(index, C.TIME_UNSET)
      player.prepare()
      player.play()
    }
  }

  fun addPlayList(
    list: List<VideoEntity>,
    isReplace: Boolean = true,
    playWhenReady: Boolean = true,
    index: Int = 0,
    position: Long = C.TIME_UNSET,
  ) {
    val player = _playerState.value ?: return
    if (list.isEmpty()) return

    Log.i(TAG, "addPlayList : ${list.size} items, index: $index")
    var items: MutableList<MediaItem>
    val count = player.mediaItemCount
    if (isReplace || count <= 0 || (count == 1 && player.getMediaItemAt(0).isPlaceHold())) {
      //保证一定有下一个视频,激活seekToNext功能
      items = list
        .distinctBy { it.id }
        .map { it.toMediaItem() }
        .toMutableList()
    } else {
      //手动去重, 不用distinctBy了, 提高一点效率
      items = mutableListOf()
      val set = HashSet<String>()
      for (i in list.indices) {
        val item = list[i]
        if (set.add(item.id)) {
          items.add(item.toMediaItem())
        }
      }
      for (i in 0 until player.mediaItemCount) {
        val item = player.getMediaItemAt(i)
        if (set.add(item.mediaId)) {
          items.add(item)
        }
      }
    }

    val placeHolder = items.last().toPlaceHold()
    items.add(placeHolder)
    player.playWhenReady = playWhenReady
    player.setMediaItems(items, index, position)
    player.prepare()
  }
}