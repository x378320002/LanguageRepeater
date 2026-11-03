package com.language.repeater.setting

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.language.repeater.R
import com.language.repeater.datastore.DsManager
import com.language.repeater.setting.action.ActionClearCache
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
  var showDeveloperContactDialog by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val resources = LocalResources.current
  val uriHandler = LocalUriHandler.current
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
      onSelectSubtitleFolder = { subtitleFolderPicker.launch(null) },
      onShowDeveloperContact = { showDeveloperContactDialog = true },
      onOpenPrivacyPolicy = {
        try {
          uriHandler.openUri(resources.getString(R.string.privacy_policy_url))
        } catch (_: IllegalArgumentException) {
          Toast.makeText(
            context,
            resources.getString(R.string.error_no_browser),
            Toast.LENGTH_SHORT
          ).show()
        }
      }
    )
  }

  if (showDeveloperContactDialog) {
    DeveloperContactDialog(
      onDismiss = { showDeveloperContactDialog = false }
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
        text = stringResource(R.string.setting_title),
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
  onSelectSubtitleFolder: () -> Unit,
  onShowDeveloperContact: () -> Unit,
  onOpenPrivacyPolicy: () -> Unit
) {
  Column(
    modifier = Modifier
      .padding(paddingValues)
      .padding(horizontal = 19.dp)
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
  ) {
    val leftBrightnessGesture by DsManager.observe(DsManager.KEY_LEFT_BRIGHTNESS_GESTURE, true).collectAsState()
    val rightVolumeGesture by DsManager.observe(DsManager.KEY_RIGHT_VOLUME_GESTURE, true).collectAsState()
    Card {
      SettingSwitchItem(
        title = stringResource(R.string.setting_left_brightness),
        checked = leftBrightnessGesture
      ) {
        viewModel.onAction(ActionLeftBrightnessGestureChange(it))
      }
      SettingDivider()
      SettingSwitchItem(
        title = stringResource(R.string.setting_right_volume),
        checked = rightVolumeGesture
      ) {
        viewModel.onAction(ActionRightVolumeGestureChange(it))
      }
    }

    Spacer(modifier = Modifier.height(30.dp))

    // 播放参数与处理缓存。
    val subtitleFolder by viewModel.stateHolder.subtitleFolder.collectAsState()
    val cacheSizeText by viewModel.stateHolder.cacheSizeFlow.collectAsState()
    val sentenceGap by viewModel.stateHolder.sentenceGap.collectAsState()
    val speed by viewModel.stateHolder.longPressSpeed.collectAsState()
    Card {
      SettingClickItem(
        title = stringResource(R.string.setting_long_press_speed),
        desc = stringResource(R.string.playback_speed_value, speed.toString()),
        onClick = onShowLongPressSpeedDialog
      )

      SettingDivider()
      SettingClickItem(
        title = stringResource(R.string.setting_sentence_gap),
        desc = stringResource(R.string.sentence_gap_value, sentenceGap),
        onClick = onShowSentenceGapDialog
      )

      SettingDivider()
      SettingClickItem(
        title = stringResource(R.string.setting_clear_temp),
        desc = cacheSizeText,
        onClick = { viewModel.onAction(ActionClearCache) }
      )
    }

    Spacer(modifier = Modifier.height(30.dp))

    Card {
      SettingClickItem(
        title = stringResource(R.string.setting_privacy_policy),
        desc = stringResource(R.string.setting_open_in_browser),
        onClick = onOpenPrivacyPolicy
      )
      SettingDivider()
      SettingClickItem(
        title = stringResource(R.string.setting_developer_contact),
        desc = stringResource(R.string.setting_email_feedback),
        onClick = onShowDeveloperContact
      )
    }
  }
}

@Composable
private fun DeveloperContactDialog(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val resources = LocalResources.current
  val email = "wangzixu0612@gmail.com"

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.setting_developer_contact)) },
    text = {
      Column {
        Text(email)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.developer_feedback_description))
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          // 邮件主题和正文按当前语言读取，并编码为 mailto 查询参数。
          val subject = Uri.encode(resources.getString(R.string.feedback_email_subject))
          val body = Uri.encode(resources.getString(R.string.feedback_email_body))
          val mailUri = Uri.parse("mailto:$email?subject=$subject&body=$body")
          val intent = Intent(Intent.ACTION_SENDTO, mailUri)
          try {
            context.startActivity(intent)
            onDismiss()
          } catch (_: ActivityNotFoundException) {
            Toast.makeText(
              context,
              resources.getString(R.string.error_no_email_app),
              Toast.LENGTH_LONG
            ).show()
          }
        }
      ) {
        Text(stringResource(R.string.action_write_email))
      }
    },
    dismissButton = {
      TextButton(
        onClick = {
          val clipboard = context.getSystemService(ClipboardManager::class.java)
          if (clipboard != null) {
            clipboard.setPrimaryClip(
              ClipData.newPlainText(resources.getString(R.string.developer_email_clip_label), email)
            )
            Toast.makeText(
              context,
              resources.getString(R.string.email_address_copied),
              Toast.LENGTH_SHORT
            ).show()
          } else {
            Toast.makeText(
              context,
              resources.getString(R.string.error_copy_email),
              Toast.LENGTH_SHORT
            ).show()
          }
          onDismiss()
        }
      ) {
        Text(stringResource(R.string.action_copy_email))
      }
    }
  )
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
      .heightIn(min = 48.dp)
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
    modifier = Modifier.heightIn(min = 48.dp),
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
        Text(stringResource(R.string.action_confirm))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss
      ) {
        Text(stringResource(R.string.action_cancel))
      }
    },
    text = {
      Column {
        Text(
          stringResource(R.string.sentence_gap_description),
          style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
          label = {
            Text(stringResource(R.string.sentence_gap_dialog_title))
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
  val resources = LocalResources.current

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
                resources.getString(R.string.speed_input_error),
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
        Text(stringResource(R.string.action_confirm))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss
      ) {
        Text(stringResource(R.string.action_cancel))
      }
    },
    title = {
      Text(stringResource(R.string.long_press_speed_dialog_title))
    },
    text = {
      Column {
        OutlinedTextField(
          label = {
            Text(stringResource(R.string.speed_range_hint))
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
