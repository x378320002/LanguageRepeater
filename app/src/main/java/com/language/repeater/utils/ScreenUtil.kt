package com.language.repeater.utils

import android.util.Size
import com.language.repeater.app.MainApp

/**
 * Date: 2025-10-10
 * Time: 20:14
 * Description:
 */
object ScreenUtil {
  fun getScreenSize(): Size {
    val displayMetrics = MainApp.instance.resources.displayMetrics
    return Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
  }
}