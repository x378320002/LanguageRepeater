package com.language.repeater.setting

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.language.repeater.app.MainApp
import com.language.repeater.datastore.DsManager
import com.language.repeater.pcm.FFmpegUtil
import com.language.repeater.sentence.SentenceStoreUtil
import com.language.repeater.setting.action.ActionClearCache
import com.language.repeater.setting.action.ActionFullGestureChange
import com.language.repeater.setting.action.ActionLeftBrightnessGestureChange
import com.language.repeater.setting.action.ActionLongPressSpeedChange
import com.language.repeater.setting.action.ActionRightVolumeGestureChange
import com.language.repeater.setting.action.ActionSentenceGapChange
import com.language.repeater.setting.action.ActionSubtitleFolderChange
import com.language.repeater.setting.action.ActionThemeChange
import com.language.repeater.setting.action.SettingAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingViewModel : ViewModel() {

  // -----------------------------------------------------------------------
  // State Holder：集中管理所有状态
  // -----------------------------------------------------------------------
  val stateHolder = SettingStateHolder(viewModelScope)

  init {
    viewModelScope.launch {
      stateHolder.cacheSizeFlow.value = calculateCacheSize()
    }
  }

  // -----------------------------------------------------------------------
  // Actions：把所有写操作封装成方法，SettingScreen 直接调用，不关心 DataStore 细节
  // -----------------------------------------------------------------------
  fun onAction(action: SettingAction) {
    when (action) {
      is ActionThemeChange -> saveNightMode(action.isLight)
      is ActionSentenceGapChange -> saveSentenceGap(action.gap)
      is ActionLongPressSpeedChange -> saveLongPressSpeed(action.speed)
      is ActionFullGestureChange -> saveFullGesture(action.enabled)
      is ActionLeftBrightnessGestureChange -> saveLeftBrightnessGesture(action.enabled)
      is ActionRightVolumeGestureChange -> saveRightVolumeGesture(action.enabled)
      is ActionSubtitleFolderChange -> saveSubtitleFolder(action.uri)
      is ActionClearCache -> clearCache()
    }
  }

  /** 保存句子最小间隔 */
  private fun saveSentenceGap(gap: Int) {
    viewModelScope.launch {
      DsManager.set(DsManager.KEY_SENTENCE_GAP, gap)
    }
  }

  /** 保存长按倍速 */
  private fun saveLongPressSpeed(speed: Float) {
    viewModelScope.launch {
      DsManager.set(DsManager.KEY_LONG_PRESS_SPEED, speed)
    }
  }

  /** 保存全屏手势开关 */
  private fun saveFullGesture(enabled: Boolean) {
    viewModelScope.launch {
      DsManager.set(DsManager.KEY_FULL_GESTURE, enabled)
    }
  }

  /** 保存左侧亮度手势开关 */
  private fun saveLeftBrightnessGesture(enabled: Boolean) {
    viewModelScope.launch {
      DsManager.set(DsManager.KEY_LEFT_BRIGHTNESS_GESTURE, enabled)
    }
  }

  /** 保存右侧音量手势开关 */
  private fun saveRightVolumeGesture(enabled: Boolean) {
    viewModelScope.launch {
      DsManager.set(DsManager.KEY_RIGHT_VOLUME_GESTURE, enabled)
    }
  }

  /** 保存夜间模式并立即切换，下次启动由 MainApp 读取该值恢复 */
  private fun saveNightMode(enabled: Boolean) {
    AppCompatDelegate.setDefaultNightMode(
      if (enabled) {
        AppCompatDelegate.MODE_NIGHT_YES
      } else {
        AppCompatDelegate.MODE_NIGHT_NO
      }
    )
    viewModelScope.launch {
      DsManager.set(DsManager.KEY_NIGHT_MODE, enabled)
    }
  }

  /** 保存字幕文件夹 URI */
  private fun saveSubtitleFolder(uriString: String) {
    viewModelScope.launch {
      DsManager.set(DsManager.KEY_SUBTITLE_FOLDER, uriString)
    }
  }

  /** 清除 WAV 与句子 JSON 缓存，并刷新缓存大小。 */
  private fun clearCache() {
    viewModelScope.launch {
      stateHolder.cacheSizeFlow.value = "..."
      val currentMediaId = MainApp.instance.playbackGraph.player.state.value.currentMedia?.mediaId
      val mediaIdsToKeep = listOfNotNull(currentMediaId)
      // clearTempData 的 except 参数表示按文件名匹配并保留这些 mediaId 的缓存。
      FFmpegUtil.clearTempData(MainApp.instance, mediaIdsToKeep)
      SentenceStoreUtil.clearTempData(MainApp.instance, mediaIdsToKeep)
      stateHolder.cacheSizeFlow.value = calculateCacheSize()
      delay(2000)
      stateHolder.cacheSizeFlow.value = calculateCacheSize()
    }
  }

  @SuppressLint("DefaultLocale")
  private suspend fun calculateCacheSize(): String = withContext(Dispatchers.IO) {
    val size1 = FFmpegUtil.getDirectorySize(MainApp.instance)
    val size2 = SentenceStoreUtil.getDirectorySize(MainApp.instance)
    val totalSizeBytes = size1 + size2
    val totalSizeMB = totalSizeBytes / (1024 * 1024).toDouble()
    "${String.format("%.2f", totalSizeMB)} MB"
  }
}
