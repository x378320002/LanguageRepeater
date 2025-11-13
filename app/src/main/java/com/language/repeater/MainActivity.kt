package com.language.repeater

import android.graphics.Color
import android.os.Bundle
import android.util.Log
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

    //test()
    //test2()
  }

  fun test2() {
    val handler = CoroutineExceptionHandler {_, e->
      Log.i("wangzixu", "CoroutineExceptionHandler ${e.message}")
    }
    val handler2 = CoroutineExceptionHandler {_, e->
      Log.i("wangzixu", "CoroutineExceptionHandler222 ${e.message}")
    }
    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    scope.launch(handler) {
      // supervisorScope 会挂起，直到它内部都完成
      // 并且它会用 SupervisorJob 来管理它内部的协程
      launch(handler2 + SupervisorJob()) {
        supervisorScope {
          launch {
            delay(1000)
            throw IOException("子 A 失败")
          }
          launch {
            try {
              delay(2000)
              Log.i("wangzixu", "子 B 完成") // 仍然会执行
            } catch (e: Exception) {
              Log.i("wangzixu", "子 B 取消") // 仍然会执行
            }
          }
        }
      }
    }
  }

  fun test() {
    val handler = CoroutineExceptionHandler {_, e->
      Log.i("wangzixu", "CoroutineExceptionHandler ${e.message}")
    }
    val job1 = GlobalScope.launch(handler) {
      var success = false
      try {
        t1()
        Log.i("wangzixu", "t1过后")
        success = true
      } catch (cancel: CancellationException) {
        Log.i("wangzixu", "test 被取消了:${cancel.message}, success:$success")
        Log.i("wangzixu", "test cancel withContext前")
        withContext(Dispatchers.Main + NonCancellable) {
          Log.i("wangzixu", "====test cancel withContext===")
        }
        Log.i("wangzixu", "test cancel withContext后")
      } catch (e: Exception) {
        Log.i("wangzixu", "test Exception:${e.message}, success:$success")
        withContext(Dispatchers.Main) {
          Log.i("wangzixu", "====test Exception withContext===")
        }
      } finally {
        Log.i("wangzixu", "test finally called")
        t2()
//        withContext(Dispatchers.Main + NonCancellable) {
//          Log.i("wangzixu", "====test finally withContext===")
//        }
        Log.i("wangzixu", "====test finally withContext=== 后")
      }
    }

    job1.invokeOnCompletion { e->
      Log.i("wangzixu", "invokeOnCompletion ${e?.message}")
    }

    GlobalScope.launch {
      delay(1500)
      Log.i("wangzixu", "调用了取消")
      job1.cancel()
    }
  }

  suspend fun t2() {
    withContext(Dispatchers.Main + NonCancellable) {
      Log.i("wangzixu", "====test finally withContext===")
    }
  }

  suspend fun t1() {
    var i = 0
    while (i < 3) {
      i++
      //repeat(100000000) {i = i + 1.0}
      //Log.i("wangzixu", "t1 检查ensureActive")
      delay(1000)
//      if (i == 1) {
//        throw CancellationException("t1 自己抛出异常")
//      }
    }
  }
}