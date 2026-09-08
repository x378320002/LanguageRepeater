@file:OptIn(ExperimentalMaterial3Api::class)

package com.language.repeater.playvideo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.language.repeater.app.SettingPageKey
import com.language.repeater.player.togglePlayPause
import com.language.repeater.playvideo.compose.EditActionsRow
import com.language.repeater.playvideo.compose.PlayTopBar
import com.language.repeater.playvideo.compose.PlaybackControlRow
import com.language.repeater.playvideo.compose.PlaylistSheet
import com.language.repeater.playvideo.compose.ProgressSeekBar
import com.language.repeater.playvideo.compose.SubActionsRow
import com.language.repeater.playvideo.compose.VideoPlayerBox
import com.language.repeater.playvideo.compose.WaveformBox
import com.language.repeater.utils.FileUtil
import java.lang.System.exit

@Composable
fun PlayVideoScreen(
  backStack: NavBackStack<NavKey>? = null,
  viewModel: PlayerViewModel = viewModel()
) {
  // 播放事实、列表、句子分析都直接观察应用级对象，不经 ViewModel 转发。
  val playerState by viewModel.player.state.collectAsState()
  val playbackList by viewModel.playbackListController.items.collectAsState()
  val playbackMode by viewModel.playbackListController.playbackMode.collectAsState()
  val repeatable by viewModel.sentenceController.repeatAb.collectAsState()
  val editMode by viewModel.editMode.collectAsState()
  val sentences by viewModel.sentenceController.sentences.collectAsState()
  val curAbSentence by viewModel.sentenceController.currentSentence.collectAsState()
  val pcmLoader by viewModel.sentenceController.pcmLoader.collectAsState()
  val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsState()

  val context = LocalContext.current
  var showPlaylistSheet by remember { mutableStateOf(false) }

  val subtitlePicker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      FileUtil.takePersistablePermission(context, uri)
      viewModel.subtitleController.updateSubtitle(uri)
    }
  }

  Scaffold(
    topBar = {
      PlayTopBar(viewModel, onGoSetting = {
        backStack?.add(SettingPageKey)
      })
    }
  ) { innerPadding: PaddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      // ---------- 视频播放区域 ----------
      VideoPlayerBox(
        viewModel = viewModel,
        modifier = Modifier.padding(horizontal = 15.dp)
      )

      // ---------- 播放进度 SeekBar ----------
      val durationSec = playerState.durationMs
      val positionSec = playerState.positionMs
      ProgressSeekBar(
        value = if (durationSec == 0L) {
          0f
        } else {
          positionSec.toFloat() / durationSec
        },
        onSeek = { newPositionSec ->
          viewModel.player.seekTo((newPositionSec * durationSec).toLong())
        }
      )

      // ---------- 波形区 ----------
      WaveformBox(
        sentences = sentences,
        curAbSentence = curAbSentence,
        editMode = editMode,
        repeatable = repeatable,
        pcmLoader = pcmLoader,
        currentPositionSec = playerState.positionMs / 1000f,
        onCustomClick = { viewModel.player.togglePlayPause() },
        onSeekBegin = {
          val wasPlaying = playerState.isPlaying
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
          val wasPlaying = playerState.isPlaying
          viewModel.player.pause()
          wasPlaying
        },
        onAbDragEnd = { wasPlaying ->
          if (wasPlaying) {
            viewModel.player.play()
          }
          viewModel.sentenceController.mergeAndSaveSentences()
        },
        modifier = Modifier.padding(horizontal = 15.dp),
      )

      // ---------- 编辑栏 ----------
      AnimatedVisibility(
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

      // ---------- 副操作栏 ----------
      SubActionsRow(
        playSpeedText = "${playerState.speed}x",
        sleepTimerText = if (sleepTimerRemaining > 0) {
          com.language.repeater.playcore.SleepTimerManager.formatTime(sleepTimerRemaining)
        } else {
          ""
        },
        abDragEnabled = editMode,
        repeatEnabled = repeatable,
        showFullScreenBtn = false,
        onToggleAbDrag = { viewModel.editMode(!editMode) },
        onToggleRepeat = { viewModel.sentenceController.toggleRepeat() },
        onSpeedSelected = { speed -> viewModel.player.setSpeed(speed) },
        onTimerSelected = { minutes -> viewModel.startSleepTimer(minutes) },
        onTimerComplete = { viewModel.startSleepTimerByCurrentItem() },
        onSelectSubtitle = {
          subtitlePicker.launch(
            arrayOf(
              "application/x-subrip",
              "text/vtt",
              "text/plain",
              "application/octet-stream"
            )
          )
        },
        onSplitSubtitle = { viewModel.sentenceController.reloadSentences() },
        onToggleFullScreen = {
          val activity = context as? android.app.Activity
          activity?.let {
            val orientation = it.resources.configuration.orientation
            it.requestedOrientation =
              if (orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
              } else {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
              }
          }
        },
      )

      // ---------- 播放控制栏 ----------
      PlaybackControlRow(
        isPlaying = playerState.isPlaying,
        onSeekPrevSentence = { viewModel.sentenceController.seekToPreviousSentence() },
        onBackSentenceHead = { viewModel.sentenceController.backToSentenceHead() },
        onTogglePlayPause = { viewModel.player.togglePlayPause() },
        onSeekNextSentence = { viewModel.sentenceController.seekToNextSentence() },
        onOpenPlaylist = { showPlaylistSheet = true },
      )

      // 底部安全间距
      Spacer(modifier = Modifier.height(20.dp))
    }
  }

  // ---------- 播放列表 BottomSheet ----------
  if (showPlaylistSheet) {
    PlaylistSheet(
      playlist = playbackList,
      currentMediaId = playerState.currentMedia?.mediaId,
      isPlaying = playerState.isPlaying,
      playbackMode = playbackMode,
      onPlaybackModeClick = {
        viewModel.playbackListController.cyclePlaybackMode()
      },
      onItemClick = { mediaId ->
        viewModel.selectItem(mediaId)
      },
      onMoveToFirst = { mediaId ->
        viewModel.moveItemToFirst(mediaId)
      },
      onDelete = { mediaId ->
        viewModel.deleteItem(mediaId)
      },
      onDismiss = { showPlaylistSheet = false },
    )
  }
}
