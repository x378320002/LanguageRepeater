package com.language.repeater

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.NavOptions
import androidx.navigation.createGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment
import com.language.repeater.databinding.ActivityNavHostBinding
import com.language.repeater.foundation.BaseActivity
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.playcore.PlaybackCore.Companion.TAG
import com.language.repeater.setting.SettingFragment
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

    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
    val navController = navHostFragment.navController
    navController.graph = navController.createGraph(startDestination = PlayVideoPageKey) {
      fragment<PlayVideoFragment, PlayVideoPageKey> {
        label = "PlayVideoPageKey"
      }
      fragment<SettingFragment, SettingPageKey> {
        label = "PlayVideoPageKey"
      }
      //Add other fragment destinations similarly.
    }
  }
}