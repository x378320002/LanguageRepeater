package com.language.repeater.playvideo.compose

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayTopBar(
  onAdd: () -> Unit,
  onHistory: () -> Unit,
  onSetting: () -> Unit
) {
  TopAppBar(
    title = {},
    navigationIcon = {
      IconButton(onClick = onAdd) {
        Icon(
          painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_plus_outline),
          contentDescription = null
        )
      }
    },
    actions = {
      IconButton(onClick = onHistory) {
        Icon(
          painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_history_outline),
          contentDescription = null
        )
      }
      IconButton(onClick = onSetting) {
        Icon(
          painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_settings_outline),
          contentDescription = null
        )
      }
    }
  )
}
