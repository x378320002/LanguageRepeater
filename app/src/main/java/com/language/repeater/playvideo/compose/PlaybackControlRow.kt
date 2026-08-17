package com.language.repeater.playvideo.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.language.repeater.R

// =========================================================================
// 学习目标 (阶段 1 · PlaybackControlRow):
// 1) 有状态 UI: 根据 isPlaying 切换播放/暂停图标 (对应旧 XML 的 ic_play_pause_selector)
// 2) Row 里用 weight 让 5 个按钮均分宽度
// 3) Box + clip(CircleShape) 做圆形背景 (对应旧 XML 的 bg_play_pause 圆形背景)
// 4) 图标大小 (Modifier.size) 与触控区分离
//
// 关键差异 (跟 View 系统对比):
// - selector.xml 在 Compose 里不会响应 View 的 state, 需要用 Kotlin 条件表达式手动切图.
// - View 系统里 ImageView 是 "图标 + 点击热区" 一体, Compose 里 IconButton 是热区、Icon 是图标.
// =========================================================================

/**
 * 播放控制栏, 对应旧 XML 里的 5 个播放控制按钮 (seek_pre_sentence / back_sentence_head /
 * play_pause_btn / seek_next_sentence / play_list).
 *
 * 布局: 5 个按钮均分宽度, 中间的播放/暂停按钮是 80dp 的大圆形按钮.
 *
 * @param isPlaying 是否在播放中 (由 ViewModel 的 isUiPlaying 传入); 决定中间按钮显示播放还是暂停图标
 * @param onSeekPrevSentence 点击上一句
 * @param onBackSentenceHead 点击回到句首
 * @param onTogglePlayPause 点击中间大按钮
 * @param onSeekNextSentence 点击下一句
 * @param onOpenPlaylist 点击右侧播放列表按钮
 */
@Composable
fun PlaybackControlRow(
  isPlaying: Boolean,
  onSeekPrevSentence: () -> Unit,
  onBackSentenceHead: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onSeekNextSentence: () -> Unit,
  onOpenPlaylist: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(80.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    // ---------- TODO 1: 上一句按钮 (seek_pre_sentence) ----------
    // 结构:
    //   IconButton(
    //     onClick = onSeekPrevSentence,
    //     modifier = Modifier.weight(1f).fillMaxHeight(),
    //   ) {
    //     Icon(
    //       painter = painterResource(R.drawable.ic_pre_sentence),
    //       contentDescription = "上一句",
    //       tint = MaterialTheme.colorScheme.onBackground,
    //     )
    //   }
    // 注意: 需要 import
    //   - androidx.compose.foundation.layout.fillMaxHeight

    // ---------- TODO 2: 回到句首按钮 (back_sentence_head) ----------
    // 参照 TODO 1, 只是换 icon 和 onClick:
    //   - Icon 资源: R.drawable.ic_go_sentence_head_1
    //   - contentDescription: "回到当前句开头"
    //   - onClick: onBackSentenceHead

    // ---------- 示例: 中间大按钮 (播放/暂停, play_pause_btn) ----------
    // 教学点:
    // 1) 用 `if (isPlaying) 图标A else 图标B` 根据状态切换资源.
    // 2) Box + clip(CircleShape) + background 画一个圆形背景, 对应旧 XML 的 bg_play_pause.
    // 3) Modifier.size(80.dp) 定义整体触控区; 内部 Icon 用 size(40.dp) 控制图标本身大小.
    //
    // 关于图标资源: 旧 XML 是 ic_play_pause_selector, 那是一个 selector, 内部引用了
    // 两个静态图标 (你可以自己去看 res/drawable/ic_play_pause_selector.xml).
    // Compose 里不能直接使用 selector 的 state_selected 等 state, 所以手动切.
    // 请你自己在 res/drawable/ 目录下找到实际的 play 和 pause 两个 drawable id, 替换下面的占位:
    val playPauseIconRes = if (isPlaying) {
      // TODO 请替换为项目里 "暂停" 图标的资源 id, 比如 R.drawable.ic_pause 之类
      R.drawable.ic_play_pause_selector
    } else {
      // TODO 请替换为项目里 "播放" 图标的资源 id, 比如 R.drawable.ic_play 之类
      R.drawable.ic_play_pause_selector
    }
    IconButton(
      onClick = onTogglePlayPause,
      modifier = Modifier
        .size(80.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
      Icon(
        painter = painterResource(id = playPauseIconRes),
        contentDescription = if (isPlaying) "暂停" else "播放",
        modifier = Modifier.size(40.dp),
        tint = MaterialTheme.colorScheme.onBackground,
      )
    }

    // ---------- TODO 3: 下一句按钮 (seek_next_sentence) ----------
    // 参照 TODO 1:
    //   - Icon 资源: R.drawable.ic_next_sentence
    //   - contentDescription: "下一句"
    //   - onClick: onSeekNextSentence
    //   - Modifier: weight(1f).fillMaxHeight()

    // ---------- TODO 4: 播放列表按钮 (play_list) ----------
    // 参照 TODO 1:
    //   - Icon 资源: R.drawable.ic_list
    //   - contentDescription: "播放列表"
    //   - onClick: onOpenPlaylist
    //   - Modifier: weight(1f).fillMaxHeight()
  }
}

// =========================================================================
// 补充: Modifier.weight(1f) 只能在 RowScope / ColumnScope 里用
// =========================================================================
// 你会看到编译器提示: `weight` 是 Row 的隐式接收者上的扩展函数.
// 具体细节等你写第一个 IconButton 的时候看 Android Studio 的自动 import 就懂了.
// =========================================================================

// =========================================================================
// 关于 clip(CircleShape) 和 background 的顺序
// =========================================================================
// .clip(CircleShape).background(color)  ← 先裁成圆再画背景, 背景是圆形
// .background(color).clip(CircleShape)  ← 先画方形背景再裁圆, 视觉上还是圆的, 但触控区还是方形
// 因为触控区跟 Modifier.size 一起走, 上面示例都是 size(80.dp) 决定的, 所以两种顺序看起来一样.
// 但如果加了 clickable 等 Modifier, 顺序就会有影响: 试试看在 IconButton 上加不同顺序对比效果.
// =========================================================================
