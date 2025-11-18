package com.language.repeater.foundation

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Date: 2025-11-14
 * Time: 11:04
 * Description:
 */
open class BaseComponent<F : BaseFragment>: LifecycleEventObserver {
  companion object {
    private const val TAG = "BaseComponent"
  }

  var hasAttached = false
    private set
  lateinit var fragment: F
  private var viewLifecycleOwner: LifecycleOwner? = null

//  inline fun <reified T: BaseFragment>addComponent(component: BaseComponent<T>) {
//    fragment.addComponent(component)
//  }

  /**
   * 1. 附加到 Fragment
   * 这是您在 Fragment 的 onCreate 中唯一需要调用的方法。
   */
  fun attach(f: F) {
    if (hasAttached) {
      fragment.lifecycle.removeObserver(this)
      viewLifecycleOwner?.lifecycle?.removeObserver(this)
    }
    hasAttached = true
    fragment = f

    // --- 步骤 1: 观察 Fragment 的主生命周期 ---
    // (主要用于 onCreate, onDestroy)
    fragment.lifecycle.addObserver(this)

    // --- 步骤 2: 观察 Fragment 的 View 生命期 ---
    // (这是最关键的技巧)
    // 我们不直接观察 viewLifecycleOwner，而是观察
    // "viewLifecycleOwnerLiveData"，这是一个能通知我们
    // "View 被创建了" 或 "View 被销毁了" 的 LiveData。
    fragment.viewLifecycleOwnerLiveData.observe(fragment) { newViewOwner ->
      // 首先，清理掉旧的观察者 (如果存在的话)
      this.viewLifecycleOwner?.lifecycle?.removeObserver(this)
      this.viewLifecycleOwner = newViewOwner
      newViewOwner?.lifecycle?.addObserver(this)
    }
  }

  /**
   * 2. 统一的事件入口
   * 所有事件都从这里进来
   */
  override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    // 3. 核心：区分事件来源
    when (source) {
      fragment -> handleFragmentLifecycle(event)
      viewLifecycleOwner -> handleViewLifecycle(event)
    }
  }

  /**
   * 处理 Fragment 自身的生命周期
   * (随 Fragment 实例, 从 onCreate 到 onDestroy)
   */
  private fun handleFragmentLifecycle(event: Lifecycle.Event) {
    when (event) {
      Lifecycle.Event.ON_CREATE -> onCreate()
      Lifecycle.Event.ON_DESTROY -> {
        onDestroy()
        this.viewLifecycleOwner = null
      }
      else -> {}
    }
  }

  /**
   * 处理 Fragment View 的生命周期
   * (随 View, 从 onCreateView 到 onDestroyView)
   */
  private fun handleViewLifecycle(event: Lifecycle.Event) {
    when (event) {
      Lifecycle.Event.ON_CREATE -> onCreateView()
      Lifecycle.Event.ON_START -> onStart()
      Lifecycle.Event.ON_RESUME -> onResume()
      Lifecycle.Event.ON_PAUSE -> onPause()
      Lifecycle.Event.ON_STOP -> onStop()
      Lifecycle.Event.ON_DESTROY -> onDestroyView()
      else -> {}
    }
  }

  open fun onCreate() { Log.d(TAG, "Fragment: ON_CREATE (逻辑创建)") }
  open fun onCreateView() { Log.d(TAG, "View: ON_CREATE_VIEW (视图创建)") }
  open fun onStart() { Log.d(TAG, "View: ON_START") }
  open fun onResume() { Log.d(TAG, "View: ON_RESUME (视图可见)") }
  open fun onPause() { Log.d(TAG, "View: ON_PAUSE") }
  open fun onStop() { Log.d(TAG, "View: ON_STOP") }
  open fun onDestroyView() { Log.d(TAG, "View: ON_DESTROY_VIEW (视图销毁)") }
  open fun onDestroy() { Log.d(TAG, "Fragment: ON_DESTROY (逻辑销毁)") }
}