package com.language.repeater.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

object DataStoreKey {
  val SUBTITLE_FOLDER_KEY = stringPreferencesKey("subtitle_folder_uri")
}