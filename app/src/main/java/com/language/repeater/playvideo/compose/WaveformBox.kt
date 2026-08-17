package com.language.repeater.playvideo.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import com.language.repeater.pcm.PCMSegmentLoader
import com.language.repeater.sentence.Sentence
import com.language.repeater.widgets.ScrollWaveformView

// =========================================================================
// 学习目标 (阶段 1 · WaveformBox):
// 1) AndroidView 与 factory/update 的进阶用法: 复杂 View + 多状态同步
// 2) rememberUpdatedState: 解决 "factory 里 lambda 不会随重组更新" 的经典问题
// 3) View 层 Listener 与 Composable 回调的桥接模式
//
// 关于 ScrollWaveformView (882 行 Canvas): 阶段 1 保留原样, 用 AndroidView 兜底.
// 阶段 7 会用 Compose Canvas + pointerInput 完全重写它 (最难的阶段).
//
// 关于 PlayScrollWaveComponent: 旧组件里的所有逻辑都会搬到这个 Composable + PlayerViewModel,
// 阶段 1 完成后 PlayScrollWaveComponent 可以整个清空 (文件保留到阶段 8 一起清).
// =========================================================================

/**
 * 波形图区, 对应旧 XML 里的 `audio_wave_view_wrapper` (CardView) + `audio_progress_wave_view`
 * (ScrollWaveformView).
 *
 * 阶段 1 用 AndroidView 直接包装 ScrollWaveformView, 通过 factory + update 同步状态和 Listener.
 *
 * @param sentences 句子列表, 来自 ViewModel.sentencesFlow
 * @param curAbSentence 当前 AB 复读句子, 来自 ViewModel.curAbSentenceFlow
 * @param editMode 是否处于编辑模式, 来自 ViewModel.editMode
 * @param pcmLoader PCM 数据加载器, 来自 ViewModel.pcmLoaderStateFlow
 * @param currentPositionSec 当前播放位置 (秒), 来自 ViewModel.currentPositionSeconds
 * @param onCustomClick 点击波形图 (通常触发播放/暂停切换)
 * @param onSeekBegin 用户开始拖动波形 seek; 返回 "拖动前是否在播放中" (用于拖完恢复播放)
 * @param onSeeking 拖动过程中每一帧回调, 参数是新位置 (秒)
 * @param onSeekEnd 拖动结束. 参数: 最终位置(秒), 拖动前是否在播放 (决定是否 resume)
 * @param onAbDragBegin 开始拖动 AB 边界圆点; 返回 "拖动前是否在播放中"
 * @param onAbDragEnd 结束拖动 AB. 参数: 拖动前是否在播放
 */
@Composable
fun WaveformBox(
  sentences: List<Sentence>,
  curAbSentence: Sentence?,
  editMode: Boolean,
  pcmLoader: PCMSegmentLoader?,
  currentPositionSec: Float,
  onCustomClick: () -> Unit,
  onSeekBegin: () -> Boolean,
  onSeeking: (Float) -> Unit,
  onSeekEnd: (position: Float, wasPlaying: Boolean) -> Unit,
  onAbDragBegin: () -> Boolean,
  onAbDragEnd: (wasPlaying: Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {

}

// =========================================================================
// 补充: AndroidView 的 update 频率
// =========================================================================
// 只要传给这个 Composable 的任何参数变化, 就会触发重组, update lambda 就会跑一次.
// 因此:
//   1) update 里做的事情一定要"幂等", 反复调不会有副作用 (invalidate 除外)
//   2) 昂贵的操作 (如 setPCMLoader) 需要额外的"是否真变化"判断
//   3) 传入的 List<Sentence> 如果每次都是新对象引用 (但内容相同), 也会触发 update
//      —— 这是 Compose 稳定性 (Stability) 相关话题, 后面遇到再展开
// =========================================================================
