package com.language.repeater.playvideo.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    title = {
      val playerState by viewModel.player.state.collectAsState()
      val title = playerState.currentMedia?.title.orEmpty()
      if (title.isNotEmpty()) {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.MiddleEllipsis,
        )
      }
    },
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
