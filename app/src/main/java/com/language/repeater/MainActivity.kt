package com.language.repeater

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavOptions
import androidx.navigation.createGraph
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment
import com.language.repeater.databinding.ActivityMainBinding
import com.language.repeater.playvideo.PlayVideoFragment
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Serializable
object PlayVideoPageKey

@Serializable
object TestPageKey

val defaultNavOptions = NavOptions.Builder()
  .setEnterAnim(R.anim.slide_in_right)
  .setExitAnim(R.anim.slide_out_hold)
  .setPopEnterAnim(R.anim.slide_out_hold)
  .setPopExitAnim(R.anim.slide_out_right)
  .build()

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    )
    setContentView(R.layout.activity_main)

    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
    val navController = navHostFragment.navController
    navController.graph = navController.createGraph(startDestination = PlayVideoPageKey) {
      // Associate each destination with one of the route constants.
      fragment<TestFragment, TestPageKey> {
        label = "TestPageKey"
      }
      fragment<PlayVideoFragment, PlayVideoPageKey> {
        label = "PlayVideoPageKey"
      }
      // Add other fragment destinations similarly.
    }
  }
}