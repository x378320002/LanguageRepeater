package com.language.repeater.utils

import com.language.repeater.MyApp

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
}