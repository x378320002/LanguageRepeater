package com.language.repeater.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val darkColor = darkColorScheme(
  primary = Color(0xFF07C160),
  onPrimary = Color.White,
)

private val bgColor = Color(red = 248, green = 243, blue = 235, alpha = 255)
private val bgCard = Color(red = 235, green = 235, blue = 225, alpha = 255)

private val lightColor = lightColorScheme(
  background = bgColor,
  surface = bgColor,
  surfaceVariant =  bgCard,
  surfaceContainerHighest = bgCard,
  primary = Color(0xFF07C160),
  onPrimary = Color.White,
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