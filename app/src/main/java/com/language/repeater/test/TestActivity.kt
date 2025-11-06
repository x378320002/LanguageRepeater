package com.language.repeater.test

import com.language.repeater.R

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.createGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment
import kotlinx.serialization.Serializable

@Serializable
object PlayVideoPageKey

@Serializable
object TestPageKey

class TestActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    )
    setContentView(R.layout.activity_main)

    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
    val navController = navHostFragment.navController
    navController.graph = navController.createGraph(startDestination = TestPageKey) {
      // Associate each destination with one of the route constants.
      fragment<TestFragment, TestPageKey> {
        label = "TestPageKey"
      }
      // Add other fragment destinations similarly.
    }
  }
}