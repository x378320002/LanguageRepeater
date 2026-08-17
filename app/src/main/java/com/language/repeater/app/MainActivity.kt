package com.language.repeater.app

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.createGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment
import com.language.repeater.R
import com.language.repeater.databinding.ActivityNavHostBinding
import com.language.repeater.foundation.BaseActivity
import com.language.repeater.playvideo.PlayVideoFragmentCompose
import com.language.repeater.setting.SettingFragment
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object PlayVideoPageKey

@Serializable
object TestPageKey

@Serializable
object SettingPageKey

val defaultNavOptions = NavOptions.Builder()
  .setEnterAnim(R.anim.slide_in_right)
  .setExitAnim(R.anim.slide_out_left)
  .setPopEnterAnim(R.anim.slide_in_left)
  .setPopExitAnim(R.anim.slide_out_right)
  .build()

class MainActivity : BaseActivity<ActivityNavHostBinding>() {
  override fun inflateBinding(layoutInflater: LayoutInflater): ActivityNavHostBinding {
    return ActivityNavHostBinding.inflate(layoutInflater)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val navHostFragment =
      supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
    val navController = navHostFragment.navController
    navController.graph = navController.createGraph(startDestination = PlayVideoPageKey) {
      fragment<PlayVideoFragmentCompose, PlayVideoPageKey> {
        label = "PlayVideoPageKey"
      }
      fragment<SettingFragment, SettingPageKey> {
        label = "PlayVideoPageKey"
      }
    }

    lifecycleScope.launch {
      GlobalState.nightMode.collect {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (it) {
          AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
          controller.isAppearanceLightStatusBars = false
          controller.isAppearanceLightNavigationBars = false
        } else {
          AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
          controller.isAppearanceLightStatusBars = true
          controller.isAppearanceLightNavigationBars = true
        }
      }
    }
  }
}