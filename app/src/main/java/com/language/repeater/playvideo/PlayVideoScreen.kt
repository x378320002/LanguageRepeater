@file:OptIn(ExperimentalMaterial3Api::class)

package com.language.repeater.playvideo

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.composables.icons.tabler.outline.R
import com.language.repeater.app.SettingPageKey
import com.language.repeater.playcore.PlaybackCore
import com.language.repeater.playvideo.compose.PlayTopBar

@Composable
fun PlayVideoScreen(
  backStack: NavBackStack<NavKey>? = null,
  viewModel: PlayerViewModel = viewModel()
) {
  val context = LocalContext.current
  val instance = PlaybackCore.getInstance(context)

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    topBar = {
      PlayTopBar(onAdd = {

      }, onHistory = {

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
      // ---------- 2. 视频标题 ----------
      Text(
        text = "",
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 15.dp),
      )

      // TODO 2: 调用 VideoPlayerBox(...)
      // 传参:
      //   player = player,
      //   onClickSubtitle = { /* 阶段 2 会弹字幕菜单, 阶段 1 先 no-op 或打 Log */ },
      //   onClickFullScreen = { /* 阶段 4 会切换全屏, 阶段 1 先 no-op */ },
      //   modifier = Modifier.padding(horizontal = 15.dp, vertical = 5.dp)

      // ---------- 4. 波形区 ----------
      // TODO 3: 调用 WaveformBox(...)
      // 传参:
      //   sentences = sentences,
      //   curAbSentence = curAbSentence,
      //   editMode = editMode,
      //   pcmLoader = pcmLoader,
      //   currentPositionSec = currentPositionSec,
      //   onCustomClick = { viewModel.togglePlayPause() },
      //   onSeekBegin = {
      //     val wasPlaying = viewModel.isUiPlaying.value
      //     viewModel.pause()
      //     wasPlaying
      //   },
      //   onSeeking = { pos -> viewModel.updatePosition(pos) },
      //   onSeekEnd = { pos, wasPlaying ->
      //     viewModel.getPlayer()?.seekTo((pos * 1000).toLong())
      //     if (wasPlaying) viewModel.play()
      //   },
      //   onAbDragBegin = {
      //     val wasPlaying = viewModel.isUiPlaying.value
      //     viewModel.pause()
      //     wasPlaying
      //   },
      //   onAbDragEnd = { wasPlaying ->
      //     if (wasPlaying) viewModel.play()
      //     viewModel.saveSentenceData()
      //   },
      //   modifier = Modifier.padding(horizontal = 15.dp)

      // ---------- 5. 视频进度 Slider ----------
      // 示例: 完整写好的 Slider (你可以直接留着).
      // 教学点:
      //   Slider 是纯 UI, 状态在外部 (currentPositionSec).
      //   拖动时 onValueChange 每帧回调; 拖动结束在 onValueChangeFinished 里执行 seek.
      //   Composable 里"拖动过程中的临时状态"用 rememberSaveable/remember 存, 拖完覆盖真实状态.
      //   为了教学简单, 这里先拖一次就 seek 一次 (每帧 seek, 性能可能一般, 后续可优化).
//      Slider(
//        value = currentPositionSec.coerceIn(0f, durationSec.coerceAtLeast(0.01f)),
//        onValueChange = { newValue ->
//          viewModel.updatePosition(newValue)
//        },
//        onValueChangeFinished = {
//          viewModel.getPlayer()?.seekTo((currentPositionSec * 1000).toLong())
//        },
//        valueRange = 0f..(durationSec.coerceAtLeast(0.01f)),
//        modifier = Modifier
//          .fillMaxWidth()
//          .padding(horizontal = 15.dp),
//      )

      // ---------- 6. 编辑栏 (阶段 2 实现) ----------
      // 阶段 1 留空, 阶段 2 会写:
      //   AnimatedVisibility(editMode) {
      //     EditActionsRow(
      //       onMergePre = { viewModel.mergePreSentence() },
      //       ...
      //     )
      //   }

      // ---------- 7. 副操作栏 ----------
      // TODO 4: 调用 SubActionsRow(...)
      // 传参:
      //   playSpeedText = playSpeedText,
      //   sleepTimerText = "",  // 阶段 1 先空字符串, 阶段 2 接 SleepTimerManager
      //   abDragEnabled = false, // 阶段 2 会接一个真状态
      //   repeatEnabled = repeatEnabled,
      //   showFullScreenBtn = false, // 阶段 4 横屏时为 true
      //   onToggleAbDrag = { /* 阶段 2 */ },
      //   onToggleRepeat = { viewModel.toggleRepeat() },
      //   onOpenSpeedMenu = { /* 阶段 2 打开 DropdownMenu */ },
      //   onOpenTimerMenu = { /* 阶段 2 */ },
      //   onToggleFullScreen = { /* 阶段 4 */ },
      //   onOpenMore = { /* 阶段 2 */ },

      // 用 Spacer + weight 把 PlaybackControlRow 推到底部
      Spacer(modifier = Modifier.weight(1f))

      // ---------- 8. 播放控制栏 ----------
      // TODO 5: 调用 PlaybackControlRow(...)
      // 传参:
      //   isPlaying = isPlaying,
      //   onSeekPrevSentence = { viewModel.seekToPreviousSentence() },
      //   onBackSentenceHead = { viewModel.backToSentenceHead() },
      //   onTogglePlayPause = { viewModel.togglePlayPause() },
      //   onSeekNextSentence = { viewModel.seekToNextSentence() },
      //   onOpenPlaylist = onOpenPlaylist,

      // 底部安全间距
      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}
