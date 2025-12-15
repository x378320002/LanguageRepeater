package com.language.repeater

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavOptions
import androidx.navigation.createGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.test.TestFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import java.io.IOException
import kotlin.coroutines.coroutineContext

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
  val volumeFlow = MutableSharedFlow<Int>(
    extraBufferCapacity = 2,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    )
    setContentView(R.layout.activity_nav_host)

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
      //Add other fragment destinations similarly.
    }
  }
}