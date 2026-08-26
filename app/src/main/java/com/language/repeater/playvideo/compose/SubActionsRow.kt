package com.language.repeater.playvideo.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.language.repeater.R

/**
 * 副操作栏 — 对齐旧 XML sub_actions_layout
 * 每个按钮 44dp 宽，图标居中，整体集中排列
 * 速度/定时/更多菜单锚定在对应按钮上
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
  onSpeedSelected: (Float) -> Unit,
  onTimerSelected: (Int) -> Unit,
  onTimerComplete: () -> Unit,
  onSelectSubtitle: () -> Unit,
  onSplitSubtitle: () -> Unit,
  onToggleFullScreen: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val iconTint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
  val activeTint = MaterialTheme.colorScheme.primary

  var showSpeedMenu by remember { mutableStateOf(false) }
  var showSleepTimerMenu by remember { mutableStateOf(false) }
  var showMoreMenu by remember { mutableStateOf(false) }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(56.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    // AB 拖动切换
    SubActionIcon(
      iconRes = R.drawable.ic_sub_action_edit,
      contentDescription = "AB 拖动模式",
      tint = if (abDragEnabled) activeTint else iconTint,
      onClick = onToggleAbDrag,
    )

    // 复读开关
    SubActionIcon(
      iconRes = if (repeatEnabled) {
        com.composables.icons.tabler.outline.R.drawable.tabler_ic_a_b_outline
      } else {
        com.composables.icons.tabler.outline.R.drawable.tabler_ic_a_b_off_outline
      },
      contentDescription = "AB 复读",
      tint = if (repeatEnabled) activeTint else iconTint,
      onClick = onToggleRepeat,
    )

    // 播放速度（带菜单）
    Box {
      SubActionIconWithText(
        iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_brand_speedtest_outline,
        contentDescription = "播放速度",
        badgeText = playSpeedText,
        tint = iconTint,
        onClick = { showSpeedMenu = true },
      )
      DropdownMenu(
        expanded = showSpeedMenu,
        onDismissRequest = { showSpeedMenu = false }
      ) {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val speedLabels = listOf("0.25x", "0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
        speeds.forEachIndexed { index, speed ->
          DropdownMenuItem(
            text = {
              Text(
                text = speedLabels[index],
                style = MaterialTheme.typography.bodyMedium,
              )
            },
            onClick = {
              onSpeedSelected(speed)
              showSpeedMenu = false
            },
          )
        }
      }
    }

    // 睡眠定时（带菜单）
    Box {
      SubActionIconWithText(
        iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_alarm_outline,
        contentDescription = "睡眠定时",
        badgeText = sleepTimerText,
        tint = iconTint,
        onClick = { showSleepTimerMenu = true },
      )
      DropdownMenu(
        expanded = showSleepTimerMenu,
        onDismissRequest = { showSleepTimerMenu = false },
      ) {
        val timers = listOf(0, 10, 20, 30, 45, 60)
        val timerLabels = listOf("关闭", "10 分钟", "20 分钟", "30 分钟", "45 分钟", "60 分钟")
        timers.forEachIndexed { index, minutes ->
          DropdownMenuItem(
            text = {
              Text(
                text = timerLabels[index],
                style = MaterialTheme.typography.bodyMedium,
              )
            },
            onClick = {
              onTimerSelected(minutes)
              showSleepTimerMenu = false
            },
          )
        }
        DropdownMenuItem(
          text = {
            Text(
              text = "播完当前",
              style = MaterialTheme.typography.bodyMedium,
            )
          },
          onClick = {
            onTimerComplete()
            showSleepTimerMenu = false
          },
        )
      }
    }

    // 全屏切换（只在横屏显示）
    if (showFullScreenBtn) {
      SubActionIcon(
        iconRes = R.drawable.ic_fullscreen_enter,
        contentDescription = "全屏切换",
        tint = iconTint,
        onClick = onToggleFullScreen,
      )
    }

    // 更多（带菜单）
    Box {
      SubActionIcon(
        iconRes = R.drawable.ic_more_vert,
        contentDescription = "更多",
        tint = iconTint,
        onClick = { showMoreMenu = true },
      )
      DropdownMenu(
        expanded = showMoreMenu,
        onDismissRequest = { showMoreMenu = false },
      ) {
        DropdownMenuItem(
          text = {
            Text(
              text = "选择字幕",
              style = MaterialTheme.typography.bodyMedium,
            )
          },
          onClick = {
            onSelectSubtitle()
            showMoreMenu = false
          },
        )
        DropdownMenuItem(
          text = {
            Text(
              text = "分割字幕",
              style = MaterialTheme.typography.bodyMedium,
            )
          },
          onClick = {
            onSplitSubtitle()
            showMoreMenu = false
          },
        )
      }
    }
  }
}

/**
 * 单个副操作按钮：44dp 宽，图标居中，无边框点击效果
 */
@Composable
private fun SubActionIcon(
  iconRes: Int,
  contentDescription: String,
  tint: Color,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .size(iconWrapperSize)
      .clickable(
        onClick = onClick,
      ),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      modifier = Modifier.size(innerIconSize),
      painter = painterResource(iconRes),
      contentDescription = contentDescription,
      tint = tint,
    )
  }
}

/**
 * 带底部小字的副操作按钮：图标居中，小字叠加在图标下方
 */
@Composable
private fun SubActionIconWithText(
  iconRes: Int,
  contentDescription: String,
  badgeText: String,
  tint: Color,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .width(iconWrapperSize)
      .fillMaxHeight()
      .clickable(
        onClick = onClick,
      ),
    contentAlignment = Alignment.Center,
  ) {
    if (badgeText.isNotEmpty()) {
      Text(
        text = badgeText,
        fontSize = 10.sp,
        maxLines = 1,
        fontWeight = FontWeight.Bold,
        color = tint,
        lineHeight = 14.sp,
        letterSpacing = 0.sp
      )
    } else {
      Icon(
        modifier = Modifier.size(innerIconSize),
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        tint = tint,
      )
    }
  }
}

private val innerIconSize = 20.dp
private val iconWrapperSize = 42.dp
