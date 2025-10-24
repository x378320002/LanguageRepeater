package com.language.repeater.utils

import android.app.LocaleConfig
import android.app.LocaleManager
import com.language.repeater.MyApp
import java.util.Locale
import kotlin.math.floor

/**
 * Date: 2025-10-13
 * Time: 15:52
 * Description:
 */
object CommonUtil {
  // 辅助函数，用于将 dp 单位转换为像素
  fun Float.toDp(): Float {
    return this * MyApp.instance.resources.displayMetrics.density
  }

  /**
   * 把秒转成xx:xx的时间形式
   */
  fun formatTime(seconds: Float): String {
    // 转为整数秒
    val totalSeconds = seconds.toInt()
    val minutes = totalSeconds / 60
    val secs = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
  }

  /**
   * 把秒转成xx:xx.x的时间形式
   */
  fun formatTimeFloat(seconds: Float): String {
    // 转为整数秒
    val totalSeconds = seconds
    val minutes = floor(totalSeconds / 60).toInt()
    val secs = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%04.1f", minutes, secs)
  }
}