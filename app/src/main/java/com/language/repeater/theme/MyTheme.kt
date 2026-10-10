package com.language.repeater.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val darkColor = darkColorScheme()

private val bgColor = Color(red = 248, green = 243, blue = 229, alpha = 255)
private val bgCard = Color(red = 229, green = 229, blue = 205, alpha = 255)

private val lightColor = lightColorScheme(
  background = bgColor,
  surface = bgColor,
  surfaceVariant = bgCard,
  surfaceContainerHighest = bgCard
)

@Composable
fun MyTheme(content: @Composable () -> Unit) {
  val colorTheme = if (isSystemInDarkTheme()) darkColor else lightColor
  MaterialTheme(colorScheme = colorTheme) {
    content()
  }
}

/**
 * 叠在视频画面之上的浮层主题。
 *
 * 浮层底色恒为黑色半透明，与系统明暗无关，因此这里锁定暗色配色，
 * 保证图标和文字在任何系统主题下都可读。
 */
@Composable
fun VideoOverlayTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = darkColor) {
    content()
  }
}