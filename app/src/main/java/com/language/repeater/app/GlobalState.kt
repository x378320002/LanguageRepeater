package com.language.repeater.app

import com.language.repeater.datastore.DsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 全局应用状态单例
 */
object GlobalState {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  /** 夜间模式开关 */
  val nightMode: StateFlow<Boolean> = DsManager.observe(DsManager.KEY_NIGHT_MODE, true)
    .stateIn(scope, SharingStarted.WhileSubscribed(5000), true)
}
