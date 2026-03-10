package com.language.repeater.utils

import android.R.attr.fragment
import android.content.Context
import android.os.Build
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import com.language.repeater.MyApp
import com.language.repeater.R

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

  fun createLightPopMenu(context: Context, view: View): PopupMenu {
    val c = ContextThemeWrapper(context, R.style.PopupMenu_Light)
    return PopupMenu(context, view)
  }
}