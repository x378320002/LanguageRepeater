package com.language.repeater.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val darkColor = darkColorScheme()

private val lightColor = lightColorScheme()

@Composable
fun MyTheme(content: @Composable () -> Unit) {
  val colorTheme = if (isSystemInDarkTheme()) darkColor else lightColor
  MaterialTheme(colorScheme = colorTheme) {
    content()
  }
}