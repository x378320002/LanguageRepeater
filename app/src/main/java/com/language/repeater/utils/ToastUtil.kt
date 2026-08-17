package com.language.repeater.utils

import android.widget.Toast
import com.language.repeater.app.MainApp

/**
 * Date: 2025-10-10
 * Time: 20:05
 * Description:
 */
object ToastUtil {
  fun toast(content: String) {
    Toast
      .makeText(MainApp.instance, content, Toast.LENGTH_SHORT)
      .show()
  }

  fun toast(stringId: Int) {
    toast(ResourcesUtil.getString(stringId))
  }
}