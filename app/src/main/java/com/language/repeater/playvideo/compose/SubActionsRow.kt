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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.language.repeater.R

/**
 * 副操作栏
 * 每个按钮 42dp 宽，图标居中，整体集中排列
 * 速度/定时/更多菜单锚定在对应按钮上
 */
@Composable
fun SubActionsRow(
  playSpeedText: String,
  sleepTimerText: String,
  abDragEnabled: Boolean,
  repeatEnabled: Boolean,
  onToggleAbDrag: () -> Unit,
  onToggleRepeat: () -> Unit,
  onSpeedSelected: (Float) -> Unit,
  onTimerSelected: (Int) -> Unit,
  onTimerComplete: () -> Unit,
  onSelectSubtitle: () -> Unit,
  hasSubtitle: Boolean,
  subtitleVisible: Boolean,
  onToggleSubtitle: () -> Unit,
  onSplitSubtitle: (Boolean) -> Unit,
  onToggleFullScreen: () -> Unit,
  modifier: Modifier = Modifier,
  fullScreenEntered: Boolean = false,
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
      contentDescription = stringResource(R.string.ab_drag_mode),
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
      contentDescription = stringResource(R.string.ab_repeat),
      tint = if (repeatEnabled) activeTint else iconTint,
      onClick = onToggleRepeat,
    )

    // 播放速度（带菜单）
    Box {
      SubActionIconWithText(
        iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_brand_speedtest_outline,
        contentDescription = stringResource(R.string.playback_speed),
        badgeText = playSpeedText,
        tint = iconTint,
        onClick = { showSpeedMenu = true },
      )
      DropdownMenu(
        expanded = showSpeedMenu,
        onDismissRequest = { showSpeedMenu = false }
      ) {
        // 播放器接收数值，菜单文案由对应的速度资源提供。
        val speedOptions = listOf(
          0.25f to R.string.speed_0_25,
          0.5f to R.string.speed_0_5,
          0.75f to R.string.speed_0_75,
          1.0f to R.string.speed_1_0,
          1.25f to R.string.speed_1_25,
          1.5f to R.string.speed_1_5,
          2.0f to R.string.speed_2_0,
        )
        speedOptions.forEach { (speed, labelRes) ->
          DropdownMenuItem(
            text = {
              Text(
                text = stringResource(labelRes),
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
        contentDescription = stringResource(R.string.sleep_time_btn),
        badgeText = sleepTimerText,
        tint = iconTint,
        onClick = { showSleepTimerMenu = true },
      )
      DropdownMenu(
        expanded = showSleepTimerMenu,
        onDismissRequest = { showSleepTimerMenu = false },
      ) {
        val timerOptions = listOf(
          0 to R.string.action_cancel,
          10 to R.string.timer_10_minutes,
          20 to R.string.timer_20_minutes,
          30 to R.string.timer_30_minutes,
          45 to R.string.timer_45_minutes,
          60 to R.string.timer_60_minutes,
        )
        timerOptions.forEach { (minutes, labelRes) ->
          DropdownMenuItem(
            text = {
              Text(
                text = stringResource(labelRes),
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
              text = stringResource(R.string.timer_complete),
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

    // 全屏切换
    SubActionIcon(
      iconRes = if (fullScreenEntered) {
        com.composables.icons.tabler.outline.R.drawable.tabler_ic_minimize_outline
      } else {
        com.composables.icons.tabler.outline.R.drawable.tabler_ic_maximize_outline
      },
      contentDescription = stringResource(R.string.toggle_fullscreen),
      tint = iconTint,
      onClick = onToggleFullScreen,
    )

    // 更多（带菜单）
    Box {
      SubActionIcon(
        iconRes = R.drawable.ic_more_vert,
        contentDescription = stringResource(R.string.action_more),
        tint = iconTint,
        onClick = { showMoreMenu = true },
      )
      DropdownMenu(
        expanded = showMoreMenu,
        onDismissRequest = { showMoreMenu = false },
      ) {
        if (hasSubtitle) {
          DropdownMenuItem(
            text = {
              Text(
                text = stringResource(
                  if (subtitleVisible) {
                    R.string.disable_subtitle_action
                  } else {
                    R.string.enable_subtitle_action
                  }
                ),
                style = MaterialTheme.typography.bodyMedium,
              )
            },
            onClick = {
              onToggleSubtitle()
              showMoreMenu = false
            },
          )
        }
        DropdownMenuItem(
          text = {
            Text(
              text = stringResource(R.string.select_subtitle),
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
              text = stringResource(R.string.split_by_subtitle),
              style = MaterialTheme.typography.bodyMedium,
            )
          },
          onClick = {
            onSplitSubtitle(true)
            showMoreMenu = false
          },
        )
        DropdownMenuItem(
          text = {
            Text(
              text = stringResource(R.string.split_by_audio),
              style = MaterialTheme.typography.bodyMedium,
            )
          },
          onClick = {
            onSplitSubtitle(false)
            showMoreMenu = false
          },
        )
      }
    }
  }
}

/**
 * 单个副操作按钮：42dp 宽，图标居中，无边框点击效果
 */
@Composable
private fun SubActionIcon(
  iconRes: Int,
  contentDescription: String,
  tint: Color,
  onClick: () -> Unit,
) {
  IconButton (
    modifier = Modifier.size(iconWrapperSize),
    onClick = onClick
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
  IconButton(
    modifier = Modifier.size(iconWrapperSize),
    onClick = onClick
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
