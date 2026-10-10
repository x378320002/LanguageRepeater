package com.language.repeater.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.language.repeater.app.MainApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.ConcurrentHashMap

val Context.subtitleStore: DataStore<Preferences> by preferencesDataStore(name = "video_subtitle_map")
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_config")

/**
 * 当前播放的条目及其位置，全局只有这一份。
 *
 * 两个字段必须同时更新：只改其中一个会留下「新条目配旧位置」的状态，
 * 下次启动就会从错误的位置续播。因此底层的两个键是私有的，
 * 只能经 [DsManager.getPlayProgress] 和 [DsManager.setPlayProgress] 成对读写。
 */
data class PlayProgress(
  val mediaId: String = "",
  val positionMs: Long = 0L
) {
  val isEmpty: Boolean
    get() = mediaId.isEmpty()

  companion object {
    val EMPTY = PlayProgress()
  }
}

object DsManager {
  /** 配置流常驻进程，与应用同生共死，不需要取消入口。 */
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /** key → 已建立的订阅流。配置项数量固定，缓存不会无限增长。 */
  private val cache = ConcurrentHashMap<Preferences.Key<*>, StateFlow<*>>()

  //当前的subtitle备用寻找文件夹
  val KEY_SUBTITLE_FOLDER = stringPreferencesKey("subtitle_folder_uri")
  //当前播放的视频ID。与播放位置成对读写，见 PlayProgress
  private val KEY_CURRENT_PLAY_ID = stringPreferencesKey("key_current_play_id")
  //当前播放视频的播放位置。与视频ID成对读写，见 PlayProgress
  private val KEY_CURRENT_PLAY_POSITION = longPreferencesKey("key_current_play_position")
  //当前是否开启了AB句子模式
  val KEY_AB_REPEATED = booleanPreferencesKey("key_is_repeated")
  //当前的播放模式
  val KEY_PLAYER_PLAY_MODE = intPreferencesKey("key_player_play_mode")
  //当前是否在编辑模式
  val KEY_EDIT_SEN_MODE = booleanPreferencesKey("key_edit_sen_mode")

  //settings
  //句子最小间隔
  val KEY_SENTENCE_GAP = intPreferencesKey("key_sentence_gap")
  //竖屏当前是否全屏响应手势
  val KEY_FULL_GESTURE = booleanPreferencesKey("key_full_gesture")
  //全屏手势区域左侧上下滑动是否响应亮度调整
  val KEY_LEFT_BRIGHTNESS_GESTURE = booleanPreferencesKey("key_left_brightness_gesture")
  //全屏手势区域右侧上下滑动是否响应音量调整
  val KEY_RIGHT_VOLUME_GESTURE = booleanPreferencesKey("key_right_volume_gesture")
  //长按手势触发的播放倍速
  val KEY_LONG_PRESS_SPEED = floatPreferencesKey("key_long_press_speed")
  //是否开启暗色模式
  val KEY_NIGHT_MODE = booleanPreferencesKey("key_night_mode")

  /**
   * 订阅某个配置项。同一个 key 全局只建一条流，值常驻内存。
   *
   * 返回 StateFlow 是为了让调用方能同步拿到当前值：DataStore 的读是异步的，
   * 若每次组合都从 default 起步，真实值到达时会被 UI 当成一次真正的变化，
   * 表现为进场动画、主题闪烁。缓存后只有进程冷启动的第一帧是 default。
   *
   * 同一个 key 首次订阅时的 default 会被后续订阅复用，因此同一 key 必须始终传相同的 default。
   */
  @Suppress("UNCHECKED_CAST")
  fun <T> observe(
    key: Preferences.Key<T>,
    default: T
  ): StateFlow<T> {
    return cache.getOrPut(key) {
      MainApp.instance.dataStore.data
        .map { preferences ->
          preferences[key] ?: default
        }
        .distinctUntilChanged() // 统一在这里加，永远不会忘！
        .stateIn(scope, SharingStarted.Eagerly, default)
    } as StateFlow<T>
  }

  suspend fun <T> get(
    key: Preferences.Key<T>,
    default: T
  ): T {
    return MainApp.instance.dataStore.data.map { preferences ->
      preferences[key] ?: default
    }.first()
  }

  suspend fun <T> set(key: Preferences.Key<T>, value: T) {
    MainApp.instance.dataStore.edit { preferences ->
      preferences[key] = value
    }
  }

  /** 一次读出成对的进度，保证两个字段来自同一个快照。 */
  suspend fun getPlayProgress(): PlayProgress {
    return MainApp.instance.dataStore.data.map { preferences ->
      PlayProgress(
        mediaId = preferences[KEY_CURRENT_PLAY_ID] ?: "",
        positionMs = preferences[KEY_CURRENT_PLAY_POSITION] ?: 0L
      )
    }.first()
  }

  /** 两个键在同一次 edit 内更新，DataStore 保证原子，不会出现只改一半的中间态。 */
  suspend fun setPlayProgress(progress: PlayProgress) {
    MainApp.instance.dataStore.edit { preferences ->
      preferences[KEY_CURRENT_PLAY_ID] = progress.mediaId
      preferences[KEY_CURRENT_PLAY_POSITION] = progress.positionMs
    }
  }
}