package com.language.repeater.playvideo.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.language.repeater.R

// =========================================================================
// 学习目标 (阶段 1 · SubActionsRow):
// 1) Box + Alignment 做子元素叠加 (对应旧 XML 的 FrameLayout)
// 2) Column + Icon + Text 组合复合按钮 (图标下方带小字)
// 3) Row 里 Arrangement 均分子元素
// 4) 数据展示: 从 String 类型的参数直接绑到 Text 的 content
//
// 阶段 1 的目标: 只有 UI, 点击回调先透传给上层, 上层可以先 no-op.
// 阶段 2 会:
//   - 让速度/定时/更多这三个按钮打开 DropdownMenu
//   - 让 AB 拖动 / 复读切换按钮真正响应状态
// =========================================================================

/**
 * 副操作栏, 对应旧 XML 里的 `sub_actions_layout`, 从左到右 6 个按钮:
 *   1) AB 拖动切换 (switch_drag_ab, ic_sub_action_edit)
 *   2) 复读开关 (voice_repeat_switch, ic_ab_selector)
 *   3) 播放速度 (play_speed, ic_sub_action_speed + 速度文字)
 *   4) 睡眠定时 (set_timer, ic_sub_action_time + 定时文字)
 *   5) 全屏切换 (switch_full_screen, ic_fullscreen_enter, 竖屏隐藏)
 *   6) 更多 (sub_action_more, ic_more_vert)
 *
 * @param playSpeedText 当前播放速度显示文本, 比如 "1.0x"; 空字符串则不显示
 * @param sleepTimerText 当前睡眠定时显示文本, 比如 "剩 10:32"; 空字符串则不显示
 * @param abDragEnabled 是否开启 AB 拖动模式 (决定 switch_drag_ab 图标的高亮态)
 * @param repeatEnabled 是否开启 AB 复读 (决定 voice_repeat_switch 图标的高亮态)
 * @param showFullScreenBtn 是否显示全屏切换按钮 (竖屏 false, 横屏 true)
 * @param onToggleAbDrag / onToggleRepeat / onOpenSpeedMenu / onOpenTimerMenu / onToggleFullScreen / onOpenMore 六个点击回调
 */
@Composable
fun SubActionsRow(
  playSpeedText: String,
  sleepTimerText: String,
  abDragEnabled: Boolean,
  repeatEnabled: Boolean,
  showFullScreenBtn: Boolean,
  onToggleAbDrag: () -> Unit,
  onToggleRepeat: () -> Unit,
  onOpenSpeedMenu: () -> Unit,
  onOpenTimerMenu: () -> Unit,
  onToggleFullScreen: () -> Unit,
  onOpenMore: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(60.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceEvenly,
  ) {

    // ---------- 示例: 播放速度按钮 (play_speed) ----------
    // 教学点:
    // 1) 这是 "图标 + 底部小字" 的复合按钮, 用 IconButton + 内嵌 Box 来做.
    // 2) Box 允许子元素叠加, Alignment 决定子元素位置.
    // 3) 只有当 playSpeedText 非空时才显示速度小字 (对应旧 XML 里 play_speed_tv 的可见性逻辑).
    IconButton(
      onClick = onOpenSpeedMenu,
      modifier = Modifier.size(60.dp),
    ) {
      // Box 让 Icon 和 Text 叠加. Alignment 在 Box 的参数里传, 决定"默认对齐位置".
      // 但每个子元素可以用 Modifier.align(...) 覆盖默认, 单独指定自己的对齐.
      Box(
        modifier = Modifier.size(60.dp),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = painterResource(id = R.drawable.ic_sub_action_speed),
          contentDescription = "播放速度",
          modifier = Modifier.size(28.dp),
          tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        if (playSpeedText.isNotEmpty()) {
          Text(
            text = playSpeedText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomCenter),
          )
        }
      }
    }

    // ---------- TODO 1: AB 拖动切换按钮 (switch_drag_ab) ----------
    // 结构 (简单版, 没有覆盖文字):
    //   IconButton(onClick = onToggleAbDrag, modifier = Modifier.size(60.dp)) {
    //     Icon(
    //       painter = painterResource(R.drawable.ic_sub_action_edit),
    //       contentDescription = "AB 拖动模式",
    //       tint = if (abDragEnabled) MaterialTheme.colorScheme.primary
    //              else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
    //     )
    //   }
    // 教学点: 用 `if (state) 高亮色 else 普通色` 让图标随状态变色, 替代旧 selector.

    // ---------- TODO 2: 复读开关按钮 (voice_repeat_switch) ----------
    // 参照 TODO 1, 只换:
    //   - Icon 资源: R.drawable.ic_ab_selector
    //   - contentDescription: "AB 复读"
    //   - onClick: onToggleRepeat
    //   - 高亮态用 repeatEnabled 判断

    // ---------- TODO 3: 睡眠定时按钮 (set_timer) ----------
    // 参照上面 "播放速度" 示例的结构 (Box + Icon + 可选小字):
    //   - Icon 资源: R.drawable.ic_sub_action_time
    //   - contentDescription: "睡眠定时"
    //   - onClick: onOpenTimerMenu
    //   - 小字条件: sleepTimerText.isNotEmpty()
    //   - 小字内容: sleepTimerText

    // ---------- TODO 4: 全屏切换按钮 (switch_full_screen), 只在横屏显示 ----------
    // 参照 TODO 1 的简单结构, 外层加个 if (showFullScreenBtn) { ... } 判断:
    //   - Icon 资源: R.drawable.ic_fullscreen_enter
    //   - contentDescription: "全屏切换"
    //   - onClick: onToggleFullScreen

    // ---------- TODO 5: 更多按钮 (sub_action_more) ----------
    // 参照 TODO 1 简单结构:
    //   - Icon 资源: R.drawable.ic_more_vert
    //   - contentDescription: "更多"
    //   - onClick: onOpenMore
  }
}

// =========================================================================
// 补充: Box 的 contentAlignment vs Modifier.align()
// =========================================================================
// Box(contentAlignment = Alignment.Center) — 所有没手动 align 的子元素默认居中
// child Modifier.align(Alignment.BottomCenter) — 单独指定这个子元素靠下居中 (覆盖默认)
//
// 类比 XML: FrameLayout 里给 View 加 android:layout_gravity="bottom|center" 是一样的.
// =========================================================================

// =========================================================================
// 补充: Arrangement 是什么
// =========================================================================
// Row(horizontalArrangement = Arrangement.SpaceEvenly) —— 6 个子元素的间距均分:
//   [ item ]  [ item ]  [ item ]  [ item ]  [ item ]  [ item ]
// 其他常用值:
//   Arrangement.Start / End / Center — 靠某一侧或居中, 元素之间无额外间距
//   Arrangement.SpaceBetween — 首尾两端贴边, 中间均分
//   Arrangement.SpaceEvenly — 首尾和中间的间距都相等 (最均衡的 6 元素排列)
//   Arrangement.SpaceAround — 每个元素两侧的间距一样, 首尾间距是中间的一半
//   Arrangement.spacedBy(8.dp) — 显式指定固定间距
// =========================================================================
