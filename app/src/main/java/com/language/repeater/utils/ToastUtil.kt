package com.language.repeater.utils

import android.widget.Toast
import androidx.lifecycle.application
import com.language.repeater.MyApp

/**
 * Date: 2025-10-10
 * Time: 20:05
 * Description:
 */
object ToastUtil {
  fun toast(content: String) {
    Toast
      .makeText(MyApp.instance, content, Toast.LENGTH_SHORT)
      .show()
  }
}