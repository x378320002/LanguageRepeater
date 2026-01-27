package com.language.repeater

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.playcore.PlaybackCore.Companion.TAG
import com.language.repeater.playcore.PlaybackService
import com.language.repeater.setting.SettingFragment
import com.language.repeater.test.TestFragment
import kotlinx.serialization.Serializable

@Serializable
object PlayVideoPageKey

@Serializable
object TestPageKey

@Serializable
object SettingPageKey

val defaultNavOptions = NavOptions.Builder()
  .setEnterAnim(R.anim.slide_in_right)
  .setExitAnim(R.anim.slide_out_hold)
  .setPopEnterAnim(R.anim.slide_out_hold)
  .setPopExitAnim(R.anim.slide_out_right)
  .build()

class MainActivity : AppCompatActivity() {
  // 注册权限请求回调
  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted: Boolean ->
    Log.i(TAG, "requestPermissionLauncher isGranted:$isGranted")
    if (isGranted) {
      // 权限已授予，可以启动服务
      //startPlaybackService()
    } else {
      // 权限被拒绝，处理逻辑（比如提示用户）
      // 注意：没有通知权限，前台服务可能会崩溃或被系统杀掉
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    )
    setContentView(R.layout.activity_nav_host)
    WindowCompat.setDecorFitsSystemWindows(window, false)
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

    //checkAndStartService()
  }

  private fun checkAndStartService() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        //startPlaybackService()
      } else {
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    } else {
      // Android 12 及以下不需要动态申请通知权限
      //startPlaybackService()
    }
  }

  private fun startPlaybackService() {
    Log.i(TAG, "startPlaybackService")
    val intent = Intent(this, PlaybackService::class.java)
    // Android 8.0+ 必须用 startForegroundService
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      startForegroundService(intent)
    } else {
      startService(intent)
    }
  }
}