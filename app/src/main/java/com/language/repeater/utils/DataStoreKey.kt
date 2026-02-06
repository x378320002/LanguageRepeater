package com.language.repeater.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.language.repeater.MyApp
import com.language.repeater.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 封装好的通用取值方法：
 * 1. 自动处理 map 取值
 * 2. 自动处理默认值
 * 3. 自动加上 distinctUntilChanged 防抖
 */
fun <T> DataStore<Preferences>.observe(
  key: Preferences.Key<T>,
  default: T
): Flow<T> {
  return this.data
    .map { preferences ->
      preferences[key] ?: default
    }
    .distinctUntilChanged() // 统一在这里加，永远不会忘！
}

object DataStoreKey {
  //当前的subtitle备用寻找文件夹
  val KEY_SUBTITLE_FOLDER = stringPreferencesKey("subtitle_folder_uri")
  //当前播放到第几个信息
  val KEY_CURRENT_PLAY_INFO = stringPreferencesKey("key_current_play_info")
  //当前是否开启了AB句子模式
  val KEY_AB_REPEATED = booleanPreferencesKey("key_is_repeated")
  //当前的播放模式
  val KEY_PLAYER_PLAY_MODE = intPreferencesKey("key_player_play_mode")

  val KEY_SENTENCE_GAP = intPreferencesKey("key_sentence_gap")

  fun observeRepeatMode(): Flow<Int> {
    return MyApp.instance.dataStore.observe(KEY_PLAYER_PLAY_MODE, 1)
  }

  suspend fun saveRepeatMode(mode: Int) {
    MyApp.instance.dataStore.edit { preferences ->
      preferences[KEY_PLAYER_PLAY_MODE] = mode
    }
  }

  fun observeSubTitleFolder(): Flow<String> {
    return MyApp.instance.dataStore.observe(KEY_SUBTITLE_FOLDER, "")
  }

  suspend fun saveSubTitleFolder(dir: String) {
    MyApp.instance.dataStore.edit { preferences ->
      preferences[KEY_SUBTITLE_FOLDER] = dir
    }
  }

  fun observeSentenceGap(): Flow<Int> {
    return MyApp.instance.dataStore.observe(KEY_SENTENCE_GAP, 600)
  }

  suspend fun saveSentenceGap(gap: Int) {
    MyApp.instance.dataStore.edit { preferences ->
      preferences[KEY_SENTENCE_GAP] = gap
    }
  }
}