package com.language.repeater.setting

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.style.ExperimentalFoundationStyleApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Setting 页面入口 Composable
 *
 * 职责：
 * 1. 从 ViewModel 收集所有 StateFlow → State
 * 2. 把状态和事件回调向下传递给子 Composable
 * 3. 控制对话框的显示/隐藏（用局部 remember { mutableStateOf } 管理）
 *
 * @param viewModel  由 Fragment 传入，不在这里创建，保证与 Fragment 生命周期一致
 * @param onNavigateUp  返回按钮回调，由 Fragment 传入 findNavController().navigateUp()
 * @param onPickSubtitleFolder  选择字幕文件夹回调，由 Fragment 传入 openDirLauncher.launch(null)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationStyleApi::class)
@Composable
fun SettingScreen(
  nightMode: Boolean,
  onNightModeChange: (Boolean) -> Unit,
  onBack: () -> Unit,
  onPickSubtitleFolder: () -> Unit,
) {
  val viewModel: SettingViewModel = viewModel()

  // -----------------------------------------------------------------------
  // 从 ViewModel 收集状态
  // collectAsState() 会在值变化时自动触发 recomposition
  // -----------------------------------------------------------------------
  val subtitleFolder by viewModel.subtitleFolder.collectAsState()
  val sentenceGap by viewModel.sentenceGap.collectAsState()
  val longPressSpeed by viewModel.longPressSpeed.collectAsState()
  val cacheSizeText by viewModel.cacheSizeFlow.collectAsState()

  // -----------------------------------------------------------------------
  // 对话框显示控制（UI 局部状态，不需要放进 ViewModel）
  // -----------------------------------------------------------------------

  /** 是否显示"句子最小间隔"输入对话框 */
  var showSentenceGapDialog by remember { mutableStateOf(false) }

  /** 是否显示"长按倍速"输入对话框 */
  var showLongPressSpeedDialog by remember { mutableStateOf(false) }

  // -----------------------------------------------------------------------
  // 布局
  // -----------------------------------------------------------------------
  Scaffold(
    topBar = {
      MyTopAppBar(
        nightMode = nightMode,
        onNightModeChange = onNightModeChange,
        onBack = onBack
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .padding(paddingValues)
        .padding(horizontal = 19.dp)
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
    ) {
      // 第一组卡片：字幕文件夹 / 清除缓存 / 句子最小间隔
      SettingGroup1(
        subtitleFolder = subtitleFolder,
        cacheSizeText = cacheSizeText,
        sentenceGap = sentenceGap,
        speed = longPressSpeed,
        onPickSubtitleFolder = {
          onPickSubtitleFolder()
        },
        onClearCache = {
          viewModel.clearCache()
        },
        onSentenceGapClick = {
          showSentenceGapDialog = true
        },
        onSpeedClick = {
          showLongPressSpeedDialog = true
        }
      )

      Spacer(modifier = Modifier.height(30.dp))

      // 手势设置 分组标题文字
      Text(
        text = "手势设置",
        modifier = Modifier.padding(start = 0.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      SettingGroup2()
    }
  }


  // -----------------------------------------------------------------------
  // 对话框：悬浮在布局之上，仅在 showXxxDialog == true 时显示
  // -----------------------------------------------------------------------

  if (showSentenceGapDialog) {
    SentenceGapDialog(
      currentValue = sentenceGap,
      onConfirm = { value ->
        viewModel.saveSentenceGap(value)
        showSentenceGapDialog = false
      },
      onDismiss = { showSentenceGapDialog = false }
    )
  }

  if (showLongPressSpeedDialog) {
    LongPressSpeedDialog(
      currentValue = longPressSpeed,
      onConfirm = { value ->
        viewModel.saveLongPressSpeed(value)
        showLongPressSpeedDialog = false
      },
      onDismiss = { showLongPressSpeedDialog = false }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyTopAppBar(
  nightMode: Boolean,
  onNightModeChange: (Boolean) -> Unit,
  onBack: () -> Unit,
) {
  TopAppBar(
    title = {
      Text(
        text = "设置",
        style = MaterialTheme.typography.titleLarge
      )
    },
    navigationIcon = {
      IconButton(onClick = onBack) {
        Icon(
          painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_chevron_left_outline),
          contentDescription = null
        )
      }
    },
    actions = {
      IconButton(onClick = { onNightModeChange(!nightMode) }) {
        Icon(
          painter = if (nightMode) {
            painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_moon_outline)
          } else {
            painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_sun_outline)
          },
          contentDescription = null
        )

      }
    }
  )
}

@Composable
private fun MyDivider() {
  HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 15.dp))
}

// ---------------------------------------------------------------------------
// 第一组卡片
// ---------------------------------------------------------------------------

/**
 * 卡片组1：字幕文件夹 / 清除缓存 / 句子最小间隔
 * 三个点击项叠在一张卡片里，中间用 HorizontalDivider 分隔
 *
 * @param subtitleFolder      当前字幕文件夹路径（空字符串表示未设置）
 * @param cacheSizeText       缓存大小展示文字，如 "1.23 MB"
 * @param sentenceGap         当前句子最小间隔值
 * @param onPickSubtitleFolder 点击字幕文件夹行的回调
 * @param onClearCache        点击清除缓存行的回调
 * @param onSentenceGapClick  点击句子间隔行的回调（打开对话框）
 */
@Composable
private fun SettingGroup1(
  subtitleFolder: String,
  cacheSizeText: String,
  sentenceGap: Int,
  speed: Float,
  onPickSubtitleFolder: () -> Unit,
  onClearCache: () -> Unit,
  onSentenceGapClick: () -> Unit,
  onSpeedClick: () -> Unit,
) {
  Card {
    SettingClickItem(
      title = "字幕文件夹",
      desc = subtitleFolder,
      onClick = onPickSubtitleFolder
    )

    MyDivider()
    SettingClickItem(
      title = "清除缓存",
      desc = cacheSizeText,
      onClick = onClearCache
    )

    MyDivider()
    SettingClickItem(
      title = "句子最小间隔",
      desc = "$sentenceGap ms",
      onClick = onSentenceGapClick
    )

    MyDivider()
    SettingClickItem(
      title = "长按倍速",
      desc = "$speed x",
      onClick = onSpeedClick
    )
  }
}

// ---------------------------------------------------------------------------
// 第二组卡片
// ---------------------------------------------------------------------------

@Composable
private fun SettingGroup2() {
  val viewModel: SettingViewModel = viewModel()
  val fullGesture by viewModel.fullGesture.collectAsState()
  val leftBrightnessGesture by viewModel.leftBrightnessGesture.collectAsState()
  val rightVolumeGesture by viewModel.rightVolumeGesture.collectAsState()

  Card {
    SettingSwitchItem(title = "全屏手势", checked = fullGesture) {
      viewModel.saveFullGesture(it)
    }
    MyDivider()
    SettingSwitchItem(title = "左侧亮度手势", checked = leftBrightnessGesture, {
      viewModel.saveLeftBrightnessGesture(it)
    })
    MyDivider()
    SettingSwitchItem(title = "右侧音量手势", checked = rightVolumeGesture, {
      viewModel.saveRightVolumeGesture(it)
    })
  }
}

// ---------------------------------------------------------------------------
// 通用 Item 组件
// ---------------------------------------------------------------------------

/**
 * 点击类型的设置项：左侧标题 + 左侧副标题，整行可点击
 * 对应原 XML 中两行 TextView 叠在 ConstraintLayout 里的样式
 *
 * @param title   主标题
 * @param desc    副标题（灰色小字），传 null 则不显示
 * @param onClick 点击回调
 */
@Composable
private fun SettingClickItem(
  title: String,
  desc: String? = null,
  onClick: () -> Unit,
) {
  ListItem(
    colors = ListItemDefaults.colors().copy(containerColor = Color.Transparent),
    headlineContent = {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium
      )
    },
    trailingContent = {
      if (!desc.isNullOrBlank()) {
        Text(
          text = desc,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    modifier = Modifier
      .height(48.dp)
      .clickable(onClick = onClick)
  )
}

/**
 * 开关类型的设置项：左侧标题，右侧 Switch
 * 对应原 XML 中 TextView + SwitchMaterial 的 ConstraintLayout 样式
 *
 * @param title          主标题
 * @param checked        Switch 当前状态
 * @param onCheckedChange Switch 变化回调
 */
@OptIn(ExperimentalFoundationStyleApi::class)
@Composable
private fun SettingSwitchItem(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  ListItem(
    colors = ListItemDefaults.colors().copy(containerColor = Color.Transparent),
    modifier = Modifier.height(48.dp),
    headlineContent = {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium
      )
    },
    trailingContent = {
      Switch(
        checked = checked,
        onCheckedChange = onCheckedChange
      )
    }
  )

  //Row(
  //  modifier = Modifier
  //    .clickable {
  //      onCheckedChange(!checked)
  //    }
  //    .height(48.dp)
  //    .padding(horizontal = 15.dp)
  //    .fillMaxWidth(),
  //  verticalAlignment = Alignment.CenterVertically,
  //  horizontalArrangement = Arrangement.SpaceBetween
  //) {
  //  Text(
  //    text = title,
  //    style = MaterialTheme.typography.bodyMedium
  //  )
  //
  //  Switch(checked = checked, onCheckedChange = onCheckedChange)
  //}
}

// ---------------------------------------------------------------------------
// 对话框
// ---------------------------------------------------------------------------

/**
 * 句子最小间隔输入对话框
 * 对应原 Fragment 里 MaterialAlertDialogBuilder + dialog_edit_num.xml 的交互
 *
 * @param currentValue 当前间隔值，显示为输入框初始值
 * @param onConfirm    用户点击确认后的回调，参数为输入的新值
 * @param onDismiss    用户取消/点击对话框外部时的回调
 */
@Composable
private fun SentenceGapDialog(
  currentValue: Int,
  onConfirm: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  var text by remember { mutableIntStateOf(currentValue) }
  AlertDialog(
    onDismissRequest = {
      onDismiss()
    },
    confirmButton = {
      TextButton(
        onClick = {
          if (text != currentValue) {
            onConfirm(text)
          }
        }
      ) {
        Text("确认")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss
      ) {
        Text("取消")
      }
    },
    text = {
      Column {
        Text(
          "自动分割句子时, 小于此间隔的连续句子被视作成同一句",
          style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
          label = {
            Text("设置最小间隔")
          },
          maxLines = 1,
          value = text.toString(),
          onValueChange = { newValue: String ->
            if (newValue.length > 1 && newValue.all { it.isDigit() }) {
              text = newValue.toInt()
            }
          },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
      }
    },
  )
}

/**
 * 长按播放倍速输入对话框
 *
 * @param currentValue 当前倍速值
 * @param onConfirm    用户确认后的回调，参数为新倍速（校验范围 0.1f ~ 2.0f）
 * @param onDismiss    取消回调
 */
@Composable
private fun LongPressSpeedDialog(
  currentValue: Float,
  onConfirm: (Float) -> Unit,
  onDismiss: () -> Unit,
) {
  val regex = remember { Regex("^\\d*(\\.\\d*)?$") }
  var value by remember { mutableFloatStateOf(currentValue) }
  val context = LocalContext.current
  AlertDialog(
    onDismissRequest = {
      onDismiss()
    },
    confirmButton = {
      TextButton(
        onClick = {
          if (currentValue != value) {
            if (value !in 0.1f..2.0f) {
              // 提示错误
              Toast.makeText(
                context,
                "请输入0.1~2.0之间的数字",
                Toast.LENGTH_SHORT
              ).show()
            } else {
              // 保存
              onConfirm(value)
            }
          }
        }
      ) {
        Text("确认")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss
      ) {
        Text("取消")
      }
    },
    title = {
      Text("长按倍速调整")
    },
    text = {
      Column {
        OutlinedTextField(
          label = {
            Text("范围: 0.1-2.0")
          },
          maxLines = 1,
          value = value.toString(),
          onValueChange = { newValue: String ->
            if (newValue.length > 1 && newValue.matches(regex)) {
              val v = newValue.toFloatOrNull()
              if (v != null) {
                value = v
              }
            }
          },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
      }
    },
  )
}
