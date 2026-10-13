package com.language.repeater.playvideo.compose

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
  var showMediaLibrary by remember { mutableStateOf(false) }
  val openMediaLibrary = rememberMediaLibraryOpener(
    onGranted = { showMediaLibrary = true }
  )

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
      IconButton(onClick = { openMediaLibrary() }) {
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

  if (showMediaLibrary) {
    MediaLibrarySheet(
      onConfirm = { items ->
        showMediaLibrary = false
        viewModel.handleSelectedMediaStoreItems(items)
      },
      onDismiss = { showMediaLibrary = false }
    )
  }
}
