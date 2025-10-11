package com.language.repeater

import android.app.Application

/**
 * Date: 2025-07-14
 * Time: 16:51
 * Description:
 */
class MyApp: Application() {
  companion object {
    lateinit var instance: MyApp
      private set
  }

  override fun onCreate() {
    super.onCreate()
    instance = this
  }
}