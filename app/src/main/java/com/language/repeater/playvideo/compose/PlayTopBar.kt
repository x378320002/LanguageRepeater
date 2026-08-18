package com.language.repeater.playvideo.compose

import android.R.attr.navigationIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayTopBar(
  onAdd: () -> Unit,
  onHistory: () -> Unit,
  onSetting: () -> Unit
) {
//  Box(modifier = Modifier.height(80.dp).fillMaxWidth().background(Color.Red)) { }
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
