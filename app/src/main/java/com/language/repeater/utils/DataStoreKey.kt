package com.language.repeater.utils

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object DataStoreKey {
  val KEY_SUBTITLE_FOLDER = stringPreferencesKey("subtitle_folder_uri")

  val KEY_CURRENT_PLAYLIST = stringPreferencesKey("key_current_playlist")
  val KEY_CURRENT_PLAY_INFO = stringPreferencesKey("key_current_play_info")

  val KEY_IS_REPEATED = booleanPreferencesKey("key_is_repeated")
}