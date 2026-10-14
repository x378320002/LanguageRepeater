package com.language.repeater.playvideo.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.language.repeater.app.MainApp
import com.language.repeater.datastore.DsManager
import com.language.repeater.playcore.SleepTimerManager
import com.language.repeater.player.PlaybackPlayer
import com.language.repeater.player.togglePlayPause
import com.language.repeater.playvideo.PlayerViewModel
import com.language.repeater.utils.FileUtil

/**
 * 播放页各功能区与 [PlayerViewModel] 的接线。
 *
 * 默认布局、竖向全屏、横向全屏三套页面只负责摆放这些区块，
 * 状态订阅与回调一律写在这里，保证三套布局的行为始终一致。
 *
 * 每个区块只订阅自己真正需要的流：进度条与波形跟 [PlaybackPlayer.progress]（每 50ms 推进），
 * 其余区块跟 [PlaybackPlayer.state]（仅在播放态、倍速、媒体变化时更新）。
 */

/** 播放进度条。跟随高频的位置流。 */
@Composable
fun PlayerProgressSection(
  viewModel: PlayerViewModel,
  modifier: Modifier = Modifier,
) {
  val progress by viewModel.player.progress.collectAsState()
  val durationMs = progress.durationMs

  ProgressSeekBar(
    modifier = modifier,
    value = if (durationMs == 0L) {
      0f
    } else {
      progress.positionMs.toFloat() / durationMs
    },
    onSeek = { ratio ->
      viewModel.player.seekTo((ratio * durationMs).toLong())
    }
  )
}

/** 波形区，含拖动定位与 AB 区间编辑。跟随高频的位置流。 */
@Composable
fun PlayerWaveformSection(
  viewModel: PlayerViewModel,
  modifier: Modifier = Modifier,
  height: Dp = 100.dp,
  colors: CardColors = CardDefaults.cardColors(),
) {
  val progress by viewModel.player.progress.collectAsState()
  val repeatable by viewModel.sentenceController.repeatAb.collectAsState()
  val editMode by DsManager.observe(DsManager.KEY_EDIT_SEN_MODE, false).collectAsState()
  val sentences by viewModel.sentenceController.sentences.collectAsState()
  val curAbSentence by viewModel.sentenceController.currentSentence.collectAsState()
  val pcmLoader by viewModel.sentenceController.pcmLoader.collectAsState()

  WaveformBox(
    sentences = sentences,
    curAbSentence = curAbSentence,
    editMode = editMode,
    repeatable = repeatable,
    pcmLoader = pcmLoader,
    currentPositionSec = progress.positionMs / 1000f,
    onCustomClick = { viewModel.player.togglePlayPause() },
    onSeekBegin = {
      val wasPlaying = viewModel.player.state.value.isPlaying
      viewModel.player.pause()
      wasPlaying
    },
    onSeeking = { pos ->
      viewModel.sentenceController.previewPosition((pos * 1000f).toLong())
    },
    onSeekEnd = { pos, wasPlaying ->
      viewModel.player.seekTo((pos * 1000).toLong())
      if (wasPlaying) {
        viewModel.player.play()
      }
    },
    onAbDragBegin = {
      val wasPlaying = viewModel.player.state.value.isPlaying
      viewModel.player.pause()
      wasPlaying
    },
    onAbDragEnd = { wasPlaying ->
      if (wasPlaying) {
        viewModel.player.play()
      }
      viewModel.sentenceController.mergeAndSaveSentences()
    },
    modifier = modifier,
    height = height,
    colors = colors,
  )
}

/** 句子编辑栏，只在编辑模式下展开。 */
@Composable
fun PlayerEditSection(
  viewModel: PlayerViewModel,
  modifier: Modifier = Modifier,
) {
  val editMode by DsManager.observe(DsManager.KEY_EDIT_SEN_MODE, false).collectAsState()
  AnimatedVisibility(
    modifier = modifier,
    visible = editMode,
    enter = expandVertically() + fadeIn(),
    exit = fadeOut() + shrinkVertically()
  ) {
    EditActionsRow(
      onMergePre = { viewModel.sentenceController.mergePreviousSentence() },
      onMergeNext = { viewModel.sentenceController.mergeNextSentence() },
      onSplitSentence = { viewModel.sentenceController.splitCurrentSentence() },
      onDeleteSentence = { viewModel.sentenceController.deleteCurrentSentence() },
      onInsertSentence = { viewModel.sentenceController.insertSentence() },
    )
  }
}

/**
 * 副操作栏：编辑模式、AB 复读、倍速、定时、字幕、全屏。
 *
 * @param fullScreenEntered 当前是否处于全屏。为 true 时栏内出现退出全屏按钮；
 *   为 false 时进入全屏由视频画面右下角的按钮负责，这里不重复放入口。
 */
@Composable
fun PlayerSubActionsSection(
  viewModel: PlayerViewModel,
  fullScreenEntered: Boolean,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val playerState by viewModel.player.state.collectAsState()
  val repeatable by viewModel.sentenceController.repeatAb.collectAsState()
  val editMode by DsManager.observe(DsManager.KEY_EDIT_SEN_MODE, false).collectAsState()
  val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsState()

  val subtitlePicker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      FileUtil.takePersistablePermission(context, uri)
      viewModel.subtitleController.updateSubtitle(uri)
    }
  }

  SubActionsRow(
    modifier = modifier,
    playSpeedText = "${playerState.speed}x",
    sleepTimerText = if (sleepTimerRemaining > 0) {
      SleepTimerManager.formatTime(sleepTimerRemaining)
    } else {
      ""
    },
    abDragEnabled = editMode,
    repeatEnabled = repeatable,
    fullScreenEntered = fullScreenEntered,
    onToggleAbDrag = { viewModel.editMode(!editMode) },
    onToggleRepeat = { viewModel.sentenceController.toggleRepeat() },
    onSpeedSelected = { speed -> viewModel.player.setSpeed(speed) },
    onTimerSelected = { minutes -> viewModel.startSleepTimer(minutes) },
    onTimerComplete = { viewModel.startSleepTimerByCurrentItem() },
    onSelectSubtitle = {
      if (MainApp.playbackGraph.playbackListController.items.value.isEmpty()) {
        return@SubActionsRow
      }
      subtitlePicker.launch(
        arrayOf(
          "application/x-subrip",
          "text/vtt",
          "text/plain",
          "application/octet-stream"
        )
      )
    },
    hasSubtitle = playerState.currentMedia?.subtitleUri != null,
    subtitleVisible = playerState.subtitleVisible,
    onToggleSubtitle = {
      viewModel.player.setSubtitleVisible(!playerState.subtitleVisible)
    },
    onSplitSubtitle = { viewModel.sentenceController.reloadSentences(it) },
    onToggleFullScreen = { viewModel.changeScreenMode(!fullScreenEntered) },
  )
}

/** 播放控制栏：前后句、回句首、播放暂停、播放列表。 */
@Composable
fun PlayerControlSection(
  viewModel: PlayerViewModel,
  onOpenPlaylist: () -> Unit,
  modifier: Modifier = Modifier,
  isFull: Boolean = false
) {
  val playerState by viewModel.player.state.collectAsState()

  PlaybackControlRow(
    modifier = modifier,
    isPlaying = playerState.isPlaying,
    onSeekPrevSentence = { viewModel.sentenceController.seekToPreviousSentence() },
    onBackSentenceHead = { viewModel.sentenceController.backToSentenceHead() },
    onTogglePlayPause = { viewModel.player.togglePlayPause() },
    onSeekNextSentence = { viewModel.sentenceController.seekToNextSentence() },
    onOpenPlaylist = onOpenPlaylist,
    isFull = isFull
  )
}

/** 播放列表底部弹窗。 */
@Composable
fun PlayerPlaylistSheet(
  viewModel: PlayerViewModel,
  onDismiss: () -> Unit,
) {
  val playerState by viewModel.player.state.collectAsState()
  val playbackList by viewModel.playbackListController.items.collectAsState()
  val playbackMode by viewModel.playbackListController.playbackMode.collectAsState()

  if (playbackList.isEmpty()) {
    onDismiss()
  } else {
    PlaylistSheet(
      playlist = playbackList,
      currentMediaId = playerState.currentMedia?.mediaId,
      isPlaying = playerState.isPlaying,
      playbackMode = playbackMode,
      onPlaybackModeClick = { viewModel.playbackListController.cyclePlaybackMode() },
      onItemClick = { mediaId -> viewModel.selectItem(mediaId) },
      onMoveToFirst = { mediaId -> viewModel.moveItemToFirst(mediaId) },
      onDelete = { mediaId -> viewModel.deleteItem(mediaId) },
      onDismiss = onDismiss,
    )
  }
}
