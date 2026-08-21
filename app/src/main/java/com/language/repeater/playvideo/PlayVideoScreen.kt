@file:OptIn(ExperimentalMaterial3Api::class)

package com.language.repeater.playvideo

import android.graphics.DashPathEffect
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.language.repeater.app.SettingPageKey
import com.language.repeater.playvideo.compose.EditActionsRow
import com.language.repeater.playvideo.compose.HistorySheet
import com.language.repeater.playvideo.compose.PlayTopBar
import com.language.repeater.playvideo.compose.PlaybackControlRow
import com.language.repeater.playvideo.compose.PlaylistSheet
import com.language.repeater.playvideo.compose.ProgressSeekBar
import com.language.repeater.playvideo.compose.SubActionsRow
import com.language.repeater.playvideo.compose.VideoPlayerBox
import com.language.repeater.playvideo.compose.WaveformBox
import com.language.repeater.playvideo.model.SubtitleItem
import com.language.repeater.playvideo.model.VideoEntity

@Composable
fun PlayVideoScreen(
  backStack: NavBackStack<NavKey>? = null,
  viewModel: PlayerViewModel = viewModel()
) {
  // 从 PlayerEngine 抽象层获取播放状态 — 不 import media3
  val engineState by viewModel.playerEngine.state.collectAsState()
  val repeatable by viewModel.repeatable.collectAsState()
  val editMode by viewModel.editMode.collectAsState()
  val sentences by viewModel.sentencesFlow.collectAsState()
  val curAbSentence by viewModel.curAbSentenceFlow.collectAsState()
  val pcmLoader by viewModel.pcmLoaderStateFlow.collectAsState()
  val currentIndex by viewModel.playerEngine.currentIndex.collectAsState()
  val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsState()

  val context = LocalContext.current
  var showPlaylistSheet by remember { mutableStateOf(false) }
  var showHistorySheet by remember { mutableStateOf(false) }

  // 文件选择器
  val openFileLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenMultipleDocuments()
  ) { uris ->
    if (uris.isNotEmpty()) {
      viewModel.handleSelectedVideos(uris)
    }
  }

  Scaffold(
    topBar = {
      PlayTopBar(onAdd = {
        openFileLauncher.launch(arrayOf("audio/*", "video/*"))
      }, onHistory = {
        showHistorySheet = true
      }, onSetting = {
        backStack?.add(SettingPageKey)
      })
    }
  ) { innerPadding: PaddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      // ---------- 视频标题 ----------
      if (engineState.currentMediaTitle.isNotEmpty()) {
        Text(
          text = engineState.currentMediaTitle,
          color = MaterialTheme.colorScheme.onBackground,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.MiddleEllipsis,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp).padding(bottom = 10.dp),
        )
      }

      // ---------- 视频播放区域 ----------
      VideoPlayerBox(
        player = viewModel.getPlayer(),
        modifier = Modifier.padding(horizontal = 15.dp),
      )

      // ---------- 播放进度 SeekBar ----------
      val durationSec = engineState.durationMs
      val positionSec = engineState.positionMs
      ProgressSeekBar(
        value = if (durationSec == 0L) 0f else positionSec.toFloat() / durationSec,
        onSeek = { newPositionSec ->
          viewModel.playerEngine.seekTo((newPositionSec * durationSec).toLong())
        }
      )

      // ---------- 波形区 ----------
      WaveformBox(
        sentences = sentences,
        curAbSentence = curAbSentence,
        editMode = editMode,
        repeatable = repeatable,
        pcmLoader = pcmLoader,
        currentPositionSec = engineState.positionMs / 1000f,
        onCustomClick = { viewModel.togglePlayPause() },
        onSeekBegin = {
          val wasPlaying = engineState.isPlaying
          viewModel.pause()
          wasPlaying
        },
        onSeeking = { pos -> viewModel.updatePosition(pos) },
        onSeekEnd = { pos, wasPlaying ->
          viewModel.playerEngine.seekTo((pos * 1000).toLong())
          if (wasPlaying) viewModel.play()
        },
        onAbDragBegin = {
          val wasPlaying = engineState.isPlaying
          viewModel.pause()
          wasPlaying
        },
        onAbDragEnd = { wasPlaying ->
          if (wasPlaying) viewModel.play()
          viewModel.saveSentenceData()
        },
        modifier = Modifier.padding(horizontal = 15.dp),
      )

      // ---------- 编辑栏 ----------
      if (editMode) {
        EditActionsRow(
          onMergePre = { viewModel.mergePreSentence() },
          onMergeNext = { viewModel.mergeNextSentence() },
          onSplitSentence = { viewModel.splitCurrentSentence() },
          onDeleteSentence = { viewModel.deleteCurrentSentence() },
          onInsertSentence = { viewModel.insertSentence() },
        )
      }

      // ---------- 副操作栏 ----------
      SubActionsRow(
        playSpeedText = "${engineState.speed}x",
        sleepTimerText = if (sleepTimerRemaining > 0) {
          com.language.repeater.playcore.SleepTimerManager.formatTime(sleepTimerRemaining)
        } else {
          ""
        },
        abDragEnabled = editMode,
        repeatEnabled = repeatable,
        showFullScreenBtn = false,
        onToggleAbDrag = { viewModel.editMode(!editMode) },
        onToggleRepeat = { viewModel.toggleRepeat() },
        onSpeedSelected = { speed -> viewModel.playerEngine.setSpeed(speed) },
        onTimerSelected = { minutes -> viewModel.startSleepTimer(minutes) },
        onTimerComplete = { viewModel.startSleepTimerByCurrentItem() },
        onSplitSubtitle = { viewModel.loadSentenceData(false) },
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
        isPlaying = engineState.isPlaying,
        onSeekPrevSentence = { viewModel.seekToPreviousSentence() },
        onBackSentenceHead = { viewModel.backToSentenceHead() },
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onSeekNextSentence = { viewModel.seekToNextSentence() },
        onOpenPlaylist = { showPlaylistSheet = true },
      )

      // 底部安全间距
      Spacer(modifier = Modifier.height(20.dp))
    }
  }

  // ---------- 播放列表 BottomSheet ----------
  if (showPlaylistSheet) {
    val playlist by viewModel.playerEngine.playlist.collectAsState()
    PlaylistSheet(
      playlist = playlist,
      currentIndex = currentIndex,
      onItemClick = { index ->
        if (index == currentIndex) {
          viewModel.togglePlayPause()
        } else {
          viewModel.playerEngine.seekToItem(index)
        }
      },
      onMoveToFirst = { index ->
        viewModel.playerEngine.moveItem(index, 0)
      },
      onDelete = { index ->
        viewModel.playerEngine.removeItem(index)
      },
      onDismiss = { showPlaylistSheet = false },
    )
  }

  // ---------- 历史记录 BottomSheet ----------
  if (showHistorySheet) {
    val historyList by viewModel.getHistoryFlow().collectAsState(emptyList())
    HistorySheet(
      historyList = historyList.map { it.videoInfo },
      onItemClick = { item ->
        viewModel.playHistoryItem(item)
        showHistorySheet = false
      },
      onDelete = { item ->
        viewModel.deleteHistory(item)
      },
      onDismiss = { showHistorySheet = false },
    )
  }
}
