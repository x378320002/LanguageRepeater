package com.language.repeater.setting

import com.language.repeater.datastore.DsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 设置页状态持有者
 * 集中管理所有 StateFlow，方便统一传递给 Compose 组件
 *
 * [DsManager.observe] 本身已是全局缓存的 StateFlow，这里直接引用，不再二次 stateIn。
 */
class SettingStateHolder {

  /** 字幕文件夹 URI 字符串，空字符串表示未设置 */
  val subtitleFolder: StateFlow<String> = DsManager.observe(DsManager.KEY_SUBTITLE_FOLDER, "")

  /** 句子最小间隔（ms） */
  val sentenceGap: StateFlow<Int> = DsManager.observe(DsManager.KEY_SENTENCE_GAP, 600)

  /** 全屏手势开关 */
  val fullGesture: StateFlow<Boolean> = DsManager.observe(DsManager.KEY_FULL_GESTURE, false)

  /** 左侧亮度手势开关 */
  val leftBrightnessGesture: StateFlow<Boolean> =
    DsManager.observe(DsManager.KEY_LEFT_BRIGHTNESS_GESTURE, true)

  /** 右侧音量手势开关 */
  val rightVolumeGesture: StateFlow<Boolean> =
    DsManager.observe(DsManager.KEY_RIGHT_VOLUME_GESTURE, true)

  /** 长按播放倍速 */
  val longPressSpeed: StateFlow<Float> = DsManager.observe(DsManager.KEY_LONG_PRESS_SPEED, 0.5f)

  /** 临时文件缓存大小（MB），用字符串直接展示 */
  val cacheSizeFlow = MutableStateFlow("...")
}
