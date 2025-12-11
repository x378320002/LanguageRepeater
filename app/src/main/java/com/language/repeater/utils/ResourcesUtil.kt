package com.language.repeater.utils

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.language.repeater.MyApp

/**
 * Date: 2025-10-13
 * Time: 15:52
 * Description:
 */
object ResourcesUtil {
  // 辅助函数，用于将 dp 单位转换为像素
  fun Float.toDp(): Float {
    return this * MyApp.instance.resources.displayMetrics.density
  }

  fun getColor(@ColorRes colorRes: Int) : Int {
    return MyApp.instance.resources.getColor(colorRes, null)
  }

  fun getString(@StringRes str: Int) : String {
    return MyApp.instance.resources.getString(str)
  }
}