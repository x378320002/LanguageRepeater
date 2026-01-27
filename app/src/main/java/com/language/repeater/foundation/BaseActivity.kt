package com.language.repeater.foundation

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewbinding.ViewBinding

/**
 * Date: 2026-01-27
 * Time: 19:29
 * Description:
 */
abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

  protected lateinit var binding: VB

  abstract fun inflateBinding(layoutInflater: LayoutInflater): VB

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    //内部已经调用了 WindowCompat.setDecorFitsSystemWindows(window, false)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.auto(lightScrim = Color.TRANSPARENT, darkScrim = Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.auto(lightScrim = Color.TRANSPARENT, darkScrim = Color.TRANSPARENT)
    )
    binding = inflateBinding(layoutInflater)

    setContentView(binding.root)

    ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      view.setPadding(
        systemBars.left,
        systemBars.top,
        systemBars.right,
        systemBars.bottom
      )
      insets
      //WindowInsetsCompat.CONSUMED
    }
  }
}
