package com.language.repeater.playvideo.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.language.repeater.playvideo.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayTopBar(
  viewModel: PlayerViewModel,
  onGoSetting: () -> Unit
) {
  // 文件选择器
  val openFileLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenMultipleDocuments()
  ) { uris ->
    if (uris.isNotEmpty()) {
      viewModel.handleSelectedVideos(uris)
    }
  }

  TopAppBar(
    title = {},
    navigationIcon = {
      IconButton(onClick = {
        openFileLauncher.launch(arrayOf("audio/*", "video/*"))
      }) {
        Icon(
          painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_plus_outline),
          contentDescription = null
        )
      }
    },
    actions = {
//      IconButton(onClick = onHistory) {
//        Icon(
//          painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_history_outline),
//          contentDescription = null
//        )
//      }
      IconButton(onClick = onGoSetting) {
        Icon(
          painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_settings_outline),
          contentDescription = null
        )
      }
    }
  )
}
