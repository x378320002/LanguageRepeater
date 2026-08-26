package com.language.repeater.setting

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.graphicsLayer
//import com.composables.ui.components.Switch
import com.language.repeater.setting.action.ActionClearCache
import com.language.repeater.setting.action.ActionFullGestureChange
import com.language.repeater.setting.action.ActionLeftBrightnessGestureChange
import com.language.repeater.setting.action.ActionLongPressSpeedChange
import com.language.repeater.setting.action.ActionRightVolumeGestureChange
import com.language.repeater.setting.action.ActionSentenceGapChange
import com.language.repeater.setting.action.ActionSubtitleFolderChange
import com.language.repeater.setting.action.ActionThemeChange
import com.language.repeater.utils.FileUtil

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationStyleApi::class)
@Composable
fun SettingScreen(
  backStack: NavBackStack<NavKey>? = null,
  viewModel: SettingViewModel = viewModel()
) {
  var showSentenceGapDialog by remember { mutableStateOf(false) }
  var showLongPressSpeedDialog by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val subtitleFolderPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocumentTree()
  ) { uri ->
    if (uri != null) {
      FileUtil.takePersistablePermission(context, uri)
      viewModel.onAction(ActionSubtitleFolderChange(uri.toString()))
    }
  }

  Scaffold(
    topBar = {
      SettingTopAppBar(
        viewModel = viewModel,
        onBack = { backStack?.removeLastOrNull() }
      )
    }
  ) { paddingValues ->
    MainContent(
      viewModel = viewModel,
      paddingValues = paddingValues,
      onShowSentenceGapDialog = { showSentenceGapDialog = true },
      onShowLongPressSpeedDialog = { showLongPressSpeedDialog = true },
      onSelectSubtitleFolder = { subtitleFolderPicker.launch(null) }
    )
  }

  if (showSentenceGapDialog) {
    SentenceGapDialog(
      viewModel = viewModel,
      onDismiss = { showSentenceGapDialog = false }
    )
  }

  if (showLongPressSpeedDialog) {
    LongPressSpeedDialog(
      viewModel = viewModel,
      onDismiss = { showLongPressSpeedDialog = false }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingTopAppBar(
  viewModel: SettingViewModel,
  onBack: () -> Unit
) {
  val nightMode = isSystemInDarkTheme()
  TopAppBar(
    title = {
      Text(
        text = "设置",
        style = MaterialTheme.typography.titleMedium
      )
    },
    navigationIcon = {
      IconButton(onClick = onBack) {
        Icon(
          painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_arrow_left_outline),
          contentDescription = null
        )
      }
    },
    actions = {
      IconButton(onClick = { viewModel.onAction(ActionThemeChange(!nightMode)) }) {
        Icon(
          painter = if (nightMode) {
            painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_sun_outline)
          } else {
            painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_moon_outline)
          },
          contentDescription = null
        )
      }
    }
  )
}

@OptIn(ExperimentalFoundationStyleApi::class)
@Composable
private fun MainContent(
  viewModel: SettingViewModel,
  paddingValues: PaddingValues,
  onShowSentenceGapDialog: () -> Unit,
  onShowLongPressSpeedDialog: () -> Unit,
  onSelectSubtitleFolder: () -> Unit
) {
  Column(
    modifier = Modifier
      .padding(paddingValues)
      .padding(horizontal = 19.dp)
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
  ) {
    val fullGesture by viewModel.stateHolder.fullGesture.collectAsState()
    val leftBrightnessGesture by viewModel.stateHolder.leftBrightnessGesture.collectAsState()
    val rightVolumeGesture by viewModel.stateHolder.rightVolumeGesture.collectAsState()
    Card {
      SettingSwitchItem(title = "全屏手势", checked = fullGesture) {
        viewModel.onAction(ActionFullGestureChange(it))
      }
      SettingDivider()
      SettingSwitchItem(title = "左侧亮度手势", checked = leftBrightnessGesture) {
        viewModel.onAction(ActionLeftBrightnessGestureChange(it))
      }
      SettingDivider()
      SettingSwitchItem(title = "右侧音量手势", checked = rightVolumeGesture) {
        viewModel.onAction(ActionRightVolumeGestureChange(it))
      }
    }

    Spacer(modifier = Modifier.height(30.dp))

    // 第一组卡片：字幕文件夹 / 清除缓存 / 句子最小间隔 / 长按倍速
    val subtitleFolder by viewModel.stateHolder.subtitleFolder.collectAsState()
    val cacheSizeText by viewModel.stateHolder.cacheSizeFlow.collectAsState()
    val sentenceGap by viewModel.stateHolder.sentenceGap.collectAsState()
    val speed by viewModel.stateHolder.longPressSpeed.collectAsState()
    Card {
      SettingClickItem(
        title = "长按倍速",
        desc = "$speed x",
        onClick = onShowLongPressSpeedDialog
      )

      SettingDivider()
      SettingClickItem(
        title = "句子最小间隔",
        desc = "$sentenceGap ms",
        onClick = onShowSentenceGapDialog
      )

      SettingDivider()
      SettingClickItem(
        title = "清除缓存",
        desc = cacheSizeText,
        onClick = { viewModel.onAction(ActionClearCache) }
      )

      SettingDivider()

      SettingClickItem(
        title = "默认查找字幕文件夹",
        desc = subtitleFolder,
        onClick = onSelectSubtitleFolder
      )
    }
  }
}

@Composable
private fun SettingDivider() {
  HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 15.dp))
}

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
        onCheckedChange = onCheckedChange,
        modifier = Modifier
          .graphicsLayer(scaleX = 0.8f, scaleY = 0.8f)
      )
    }
  )
}

@Composable
private fun SentenceGapDialog(
  viewModel: SettingViewModel,
  onDismiss: () -> Unit
) {
  val currentValue by viewModel.stateHolder.sentenceGap.collectAsState()
  var text by remember { mutableIntStateOf(currentValue) }

  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = {
          if (text != currentValue) {
            viewModel.onAction(ActionSentenceGapChange(text))
          }
          onDismiss()
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

@Composable
private fun LongPressSpeedDialog(
  viewModel: SettingViewModel,
  onDismiss: () -> Unit
) {
  val currentValue by viewModel.stateHolder.longPressSpeed.collectAsState()
  var value by remember { mutableFloatStateOf(currentValue) }
  val regex = remember { Regex("^\\d*(\\.\\d*)?$") }
  val context = LocalContext.current

  AlertDialog(
    onDismissRequest = onDismiss,
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
              viewModel.onAction(ActionLongPressSpeedChange(value))
            }
          }
          onDismiss()
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
